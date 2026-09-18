#!/usr/bin/env python3
"""Comprueba qué rango de fechas tiene el HTML del calendario."""
import requests, re
from bs4 import BeautifulSoup
from collections import Counter

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
}

r = requests.get("https://firesifestes.es/es/calendario/", headers=HEADERS, timeout=15)
html = r.text
soup = BeautifulSoup(html, "html.parser")

# Todas las fechas en data-mec-cell
cells = soup.select("div[class*='mec-calendar-events-sec']")
fechas = []
for sec in cells:
    cell = sec.get("data-mec-cell", "")
    if len(cell) == 8:
        fechas.append(cell)
        
print(f"Total divs mec-calendar-events-sec: {len(cells)}")
print(f"Rango de fechas: {min(fechas) if fechas else 'N/A'} → {max(fechas) if fechas else 'N/A'}")

# Contar eventos por mes
meses = Counter(f[:6] for f in fechas)
for mes in sorted(meses):
    print(f"  {mes[:4]}-{mes[4:6]}: {meses[mes]} días con eventos")

# Total artículos
articles = soup.select("div[class*='mec-calendar-events-sec'] article")
print(f"\nTotal artículos (eventos): {len(articles)}")
