#!/usr/bin/env python3
"""Servidor HTTP para review.html — sirve archivos estáticos y acepta POST /save"""
import http.server
import json
import os

PORT = 8765
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

class ReviewHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_POST(self):
        if self.path == '/save':
            length = int(self.headers.get('Content-Length', 0))
            data = self.rfile.read(length)
            events = json.loads(data)
            out_path = os.path.join(DIRECTORY, 'approved_events.json')
            with open(out_path, 'w', encoding='utf-8') as f:
                json.dump(events, f, ensure_ascii=False, indent=2)
            print(f"\n✅ {len(events)} eventos guardados en approved_events.json")
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({"ok": True, "saved": len(events)}).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def log_message(self, format, *args):
        pass  # Silencioso salvo el mensaje de guardado

if __name__ == '__main__':
    with http.server.HTTPServer(('', PORT), ReviewHandler) as httpd:
        print(f"🌐 Servidor de revisión en http://localhost:{PORT}/review.html")
        print("   (Ctrl+C para parar)\n")
        httpd.serve_forever()
