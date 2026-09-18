#!/usr/bin/env python3
"""Prueba diferentes actions AJAX de MEC para cargar meses."""
import requests, re

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
    "Referer": "https://firesifestes.es/es/calendario/",
}
AJAX_URL = "https://firesifestes.es/wp-admin/admin-ajax.php"

session = requests.Session()

# Obtener nonce fresco
r = session.get("https://firesifestes.es/es/calendario/", headers=HEADERS, timeout=15)
m = re.search(r'"fes_nonce"\s*:\s*"([a-f0-9]+)"', r.text)
nonce = m.group(1) if m else "bd0b8ea116"
skin_id = "7622"
print(f"Nonce: {nonce} | Skin: {skin_id}\n")

# Extraer TODAS las actions JS del HTML
actions_js = re.findall(r'"action"\s*:\s*"([^"]+)"', r.text)
print(f"Actions en el JS de la página: {list(set(actions_js))[:20]}\n")

# Probar actions conocidas de MEC
actions_to_test = [
    "mec_load_single_month",
    "MEC_load_month", 
    "mec_get_monthly_view",
    "mec_fes_monthly_view",
    "mec_skin_render",
    "mec_monthly_view_load_more",
    "mec_load_events",
    "mec_get_events",
]

for action in actions_to_test:
    data = {
        "action": action,
        "mec_skin_id": skin_id,
        "skin_id": skin_id,
        "month": 10,
        "year": 2026,
        "nonce": nonce,
        "security": nonce,
    }
    try:
        resp = session.post(AJAX_URL, data=data, headers=HEADERS, timeout=10)
        has_events = "mec-calendar-events-sec" in resp.text or "mec-event-title" in resp.text
        print(f"  {action}: {resp.status_code} | {len(resp.text)}B | events={has_events} | {resp.text[:80]}")
    except Exception as e:
        print(f"  {action}: ERROR {e}")
