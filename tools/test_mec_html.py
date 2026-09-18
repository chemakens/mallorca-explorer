#!/usr/bin/env python3
"""Test rápido: comprueba si el calendario MEC carga eventos en el HTML estático."""
import requests
from bs4 import BeautifulSoup

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
}

url = "https://firesifestes.es/es/calendario/2026/09/"
print(f"Fetching {url}...")
r = requests.get(url, headers=HEADERS, timeout=15)
print(f"Status: {r.status_code} | Size: {len(r.text)} bytes")

soup = BeautifulSoup(r.text, "html.parser")

# Buscar estructura MEC
secs = soup.select("div[class*='mec-calendar-events-sec']")
days_with_events = soup.select("dt[class*='mec-has-event'], .mec-has-event")
articles = soup.select("article")

print(f"\nmec-calendar-events-sec divs: {len(secs)}")
print(f"mec-has-event elements: {len(days_with_events)}")
print(f"article elements: {len(articles)}")

if secs:
    for s in secs[:3]:
        cell = s.get("data-mec-cell", "?")
        titles = [a.get_text(strip=True) for a in s.select(".mec-event-title a")]
        print(f"  📅 {cell}: {titles}")
elif "mec" in r.text.lower():
    print("\n⚠️ MEC existe en el HTML pero la estructura de eventos no está — probablemente AJAX")
    # Buscar URLs AJAX
    import re
    ajax_urls = re.findall(r'mec[_-]?action["\s:=]+["\']([^"\']+)', r.text, re.I)
    print("AJAX actions encontradas:", ajax_urls[:5])
else:
    print("\n❌ No hay MEC en el HTML estático — el calendario carga con JavaScript")
    print("Necesitamos Playwright o encontrar el endpoint AJAX")
    print("\nContenido del título:", soup.title.text if soup.title else "N/A")
