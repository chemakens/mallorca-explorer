#!/usr/bin/env python3
"""Inspecciona la estructura de categorías en páginas de eventos de firesifestes."""
import requests
from bs4 import BeautifulSoup
import json, re

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
}

# Leer las primeras 5 URLs del output
import json
from pathlib import Path

output = Path(__file__).parent / "firesifestes_output.json"
with open(output) as f:
    eventos = json.load(f)

session = requests.Session()

for ev in eventos[:5]:
    url = ev["website_url"]
    print(f"\n{'='*60}")
    print(f"Título: {ev['title']}")
    print(f"URL: {url}")
    
    r = session.get(url, headers=HEADERS, timeout=10)
    soup = BeautifulSoup(r.text, "html.parser")
    
    # Todos los elementos que puedan contener categoría
    selectores = [
        ".mec-event-category",
        ".mec-category", 
        ".mec-event-cats",
        ".mec-event-cats a",
        "a[rel='category tag']",
        ".cat-links a",
        "[class*='categor']",
        "[class*='mec-cat']",
    ]
    for sel in selectores:
        els = soup.select(sel)
        if els:
            print(f"  {sel}: {[e.get_text(strip=True) for e in els]}")
    
    # Buscar en el HTML texto que contenga "categor"
    cat_texts = re.findall(r'class="[^"]*categor[^"]*"[^>]*>([^<]+)', r.text)
    if cat_texts:
        print(f"  Raw category texts: {cat_texts[:5]}")
    
    # También mostrar el título de la página para confirmar que es el evento correcto
    print(f"  Page title: {soup.title.text.strip()[:80] if soup.title else 'N/A'}")
