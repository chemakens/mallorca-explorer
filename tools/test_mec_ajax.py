#!/usr/bin/env python3
"""Encuentra el endpoint AJAX de MEC y la URL correcta del calendario."""
import requests
import re
from bs4 import BeautifulSoup

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
}

# Probar diferentes URLs del calendario
urls_a_probar = [
    "https://firesifestes.es/es/calendario/",
    "https://firesifestes.es/calendario/",
    "https://firesifestes.es/es/events/",
    "https://firesifestes.es/es/",
]

session = requests.Session()
html_ok = None
url_ok = None

for url in urls_a_probar:
    r = session.get(url, headers=HEADERS, timeout=10)
    print(f"{r.status_code} → {url}")
    if r.status_code == 200 and len(r.text) > 50000:
        html_ok = r.text
        url_ok = url
        break

if html_ok:
    print(f"\n✅ URL válida: {url_ok} ({len(html_ok)} bytes)")
    
    # Buscar datos JavaScript de MEC (nonce, ajax_url, etc.)
    # MEC inyecta variables JS como mec_var o mec_settings
    js_vars = re.findall(r'var\s+(\w*[Mm][Ee][Cc]\w*)\s*=\s*(\{[^;]+\})', html_ok)
    for name, val in js_vars[:5]:
        print(f"\nJS var: {name}")
        print(val[:300])
    
    # Buscar ajax_url
    ajax_urls = re.findall(r'"ajax_url"\s*:\s*"([^"]+)"', html_ok)
    print(f"\nAJAX URLs encontradas: {ajax_urls}")
    
    # Buscar nonces MEC
    nonces = re.findall(r'"nonce"\s*:\s*"([^"]+)"', html_ok)
    print(f"Nonces: {nonces[:3]}")
    
    # Buscar "action" AJAX
    actions = re.findall(r'"action"\s*:\s*"([^"]+mec[^"]*)"', html_ok, re.I)
    print(f"MEC actions: {actions[:10]}")
    
    # ¿Hay un shortcode o widget ID?
    widget_ids = re.findall(r'mec[_-]skin[_-](\d+)|mec_main_id["\s:=]+["\']?(\d+)', html_ok)
    print(f"Widget IDs: {widget_ids[:5]}")
    
    soup = BeautifulSoup(html_ok, "html.parser")
    # Buscar form o div con data de MEC
    mec_divs = soup.select("[class*='mec-wrap'], [id*='mec-']")
    print(f"\nMEC containers: {len(mec_divs)}")
    for d in mec_divs[:3]:
        print(f"  {d.name}#{d.get('id','?')} .{' '.join(d.get('class',[])[:3])}")
        print(f"  data attrs: {dict((k,v) for k,v in d.attrs.items() if k.startswith('data'))}")
else:
    print("❌ Ninguna URL devolvió contenido válido")
