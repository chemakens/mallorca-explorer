#!/usr/bin/env python3
"""
Scraper interactivo para Fires i Festes Mallorca.
Simula clics en cada día del calendario interactivo (The Events Calendar)
para extraer y categorizar eventos con fecha exacta.
"""

from datetime import datetime
import json
from pathlib import Path
import re
import sys
import time
from typing import Dict, List

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("❌ Falta Playwright. Instálalo con:")
    print("   pip install playwright")
    print("   playwright install chromium")
    sys.exit(1)

BASE_URL = "https://firesifestes.es"

MALLORCA_MUNICIPALITIES = {
    "palma", "calvià", "manacor", "llucmajor", "marratxí", "inca", "alcúdia",
    "felanitx", "pollença", "sóller", "sa pobla", "artà", "campos", "santanyí",
    "ses salines", "capdepera", "andratx", "santa margalida", "petra", "muro",
    "alaró", "binissalem", "sant llorenç", "santa maria", "esporles", "sineu",
    "porreres", "algaida", "consell", "búger", "costitx", "ariany", "banyalbufar",
    "campanet", "deià", "escorca", "estellencs", "fornalutx", "lloret", "lloseta",
    "maria de la salut", "montuïri", "puigpunyent", "sant joan", "selva", "sencelles",
    "vilafranca", "valldemossa", "bunyola"
}

EXCLUDED_ISLANDS = {
    "menorca", "ibiza", "eivissa", "formentera", "maó", "ciutadella"
}

CATEGORY_RULES = {
    "GASTRONOMY": [
        "vins", "vino", "tapas", "cata", "degustación", "gastronomic",
        "gastronómico", "maridaje", "bodega", "cocina", "mostra de cuina"
    ],
    "CONCERT": [
        "concierto", "concert", "es gremi", "recital", "música", "music",
        "acústico", "cantautor", "orquesta", "sinfónica", "banda", "tributo"
    ],
    "MARKET": [
        "mercat", "mercado", "market", "rastro", "artesanía", "segunda mano", "firó"
    ],
    "FESTIVAL": [
        "festa", "fiesta", "feria", "fira", "verbena", "revetla", "patronales", "diada"
    ],
    "SPORT": [
        "cursa", "carrera", "trail", "triatlón", "maratón", "deporte", "sport",
        "ciclismo", "running", "vela", "regata", "fútbol", "pádel"
    ],
    "FAMILY": [
        "infantil", "niños", "familiar", "familia", "titelles", "marionetas",
        "cuentacuentos", "kids"
    ],
    "NIGHTLIFE": [
        "dj", "discoteca", "party", "tardeo", "club", "noche"
    ],
    "CULTURE": [
        "biennal", "exposición", "exposició", "arte", "art", "teatro", "teatre",
        "danza", "museo", "fundació", "patrimonio", "visita guiada", "conferencia", "historia"
    ]
}


def categorizar_evento(texto: str) -> str:
    """Clasifica el evento en una de las categorías internas del proyecto."""
    t = texto.lower()
    for cat, keywords in CATEGORY_RULES.items():
        if any(re.search(rf"\b{re.escape(k)}\b", t) for k in keywords):
            return cat
    return "CULTURE"


def resolver_municipio(texto: str) -> str:
    """Detecta el municipio dentro del texto descartando otras islas."""
    t = texto.lower()
    for m in MALLORCA_MUNICIPALITIES:
        if re.search(rf"\b{re.escape(m)}\b", t):
            return m.title()
    return "Mallorca"


def extraer_eventos(meses_adelante: int = 3) -> List[Dict]:
    """Recorre el calendario mes a mes e interactúa con cada celda de día."""
    today = datetime.now().date()
    eventos_totales = []
    seen_ids = set()

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()

        for offset in range(meses_adelante):
            mes_abs = today.month - 1 + offset
            target_year = today.year + (mes_abs // 12)
            target_month = (mes_abs % 12) + 1

            url = f"{BASE_URL}/es/calendario/{target_year}/{str(target_month).zfill(2)}/"
            print(f"📅 Navegando a mes: {target_month}/{target_year}")

            try:
                page.goto(url, wait_until="networkidle", timeout=30000)
                page.wait_for_timeout(1500)
            except Exception as e:
                print(f"   ⚠️ Error cargando calendario: {e}")
                continue

            dias_celdas = page.locator("td, .tribe-events-calendar-month__day, [class*='day']")
            count = dias_celdas.count()

            for i in range(count):
                celda = dias_celdas.nth(i)
                texto_celda = celda.inner_text().strip()

                num_match = re.search(r"^\d{1,2}$", texto_celda) or re.search(r"\b(\d{1,2})\b", texto_celda)
                if not num_match:
                    continue

                dia_num = int(num_match.group(1))

                # Filtrar fechas anteriores a hoy
                try:
                    fecha_dt = datetime(target_year, target_month, dia_num).date()
                    if fecha_dt < today:
                        continue
                except ValueError:
                    continue

                # Clic sobre la celda para forzar el renderizado de la lista del día
                try:
                    celda.click(timeout=1000)
                    page.wait_for_timeout(350)
                except Exception:
                    continue

                body_text = page.inner_text("body")
                fecha_iso = f"{target_year}-{str(target_month).zfill(2)}-{str(dia_num).zfill(2)}"

                patron_header = rf"EVENTOS PARA\s+{dia_num}(?:TH|ST|ND|RD)?\s+([A-ZÁÉÍÓÚÇ]+)"
                if not re.search(patron_header, body_text, re.IGNORECASE):
                    continue

                enlaces_eventos = page.locator("a[href*='/evento/']").all()

                for link in enlaces_eventos:
                    titulo = link.inner_text().strip()
                    href = link.get_attribute("href") or ""

                    if not titulo or len(titulo) < 3 or "evento-tipo" in href:
                        continue

                    event_id = f"{titulo.lower()}_{fecha_iso}"
                    if event_id in seen_ids:
                        continue

                    parent = link.locator("..")
                    contexto = parent.inner_text().strip() if parent.count() > 0 else titulo

                    if any(isla in contexto.lower() for isla in EXCLUDED_ISLANDS):
                        continue

                    municipio = resolver_municipio(f"{titulo} {contexto}")
                    categoria = categorizar_evento(f"{titulo} {contexto}")

                    seen_ids.add(event_id)
                    eventos_totales.append({
                        "title": titulo,
                        "start_date": fecha_iso,
                        "municipality": municipio,
                        "category": categoria,
                        "location_info": contexto.replace("\n", " - "),
                        "website_url": href if href.startswith("http") else f"{BASE_URL}{href}",
                        "source": "firesifestes",
                    })

        browser.close()

    return eventos_totales


if __name__ == "__main__":
    print("Iniciando extracción con clics en Fires i Festes...")
    eventos = extraer_eventos(meses_adelante=3)
    print(f"\n✅ Total eventos capturados: {len(eventos)}")

    tools_dir = Path(__file__).parent
    output_path = tools_dir / "firesifestes_output.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(eventos, f, ensure_ascii=False, indent=2)

    print(f"📄 Archivo guardado con éxito en: {output_path}")
