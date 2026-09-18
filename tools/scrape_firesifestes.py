#!/usr/bin/env python3
"""
Scraper para Fires i Festes Mallorca.
- HTML del calendario MEC: deduplicación por (url, fecha)
- Para meses futuros: prueba URL params y vista de lista
"""

from datetime import datetime, timedelta
import json
from pathlib import Path
import re
import time
from typing import Dict, List, Optional, Set, Tuple
from bs4 import BeautifulSoup
import requests

BASE_URL = "https://firesifestes.es"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
    "Referer": f"{BASE_URL}/es/calendario/",
}

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

EXCLUDED_ISLANDS = {"menorca", "ibiza", "eivissa", "formentera", "maó", "ciutadella"}

SITE_CATEGORY_MAP = {
    "arte i exposiciones": "CULTURE", "arte y exposiciones": "CULTURE", "cultura": "CULTURE",
    "música y teatro": "CONCERT", "musica y teatro": "CONCERT", "música i teatre": "CONCERT", "conciertos": "CONCERT",
    "ferias y fiestas": "FESTIVAL", "fires i festes": "FESTIVAL", "feria": "FESTIVAL", "fiesta": "FESTIVAL",
    "mercados": "MARKET", "mercats": "MARKET",
    "gastronomía": "GASTRONOMY", "gastronomia": "GASTRONOMY",
    "deportes": "SPORT", "esports": "SPORT",
    "infantil y familiar": "FAMILY", "infantil i familiar": "FAMILY", "familiar": "FAMILY",
    "noche": "NIGHTLIFE", "nightlife": "NIGHTLIFE",
}

CATEGORY_RULES = {
    "GASTRONOMY": ["vins", "vino", "tapas", "cata", "degustación", "bodega", "cocina", "maridaje", "corre vins"],
    "CONCERT": ["concierto", "concert", "recital", "orquesta", "banda", "tributo", "cantautor"],
    "MARKET": ["mercat", "mercado", "market", "artesanía", "firó", "mercadillo"],
    "FESTIVAL": ["festa", "fiesta", "feria", "fira", "verbena", "revetla", "patronales", "diada"],
    "SPORT": ["cursa", "carrera", "trail", "triatlón", "maratón", "ciclismo", "running", "vela", "regata"],
    "FAMILY": ["infantil", "niños", "familiar", "titelles", "marionetas", "cuentacuentos", "kids"],
    "NIGHTLIFE": ["dj", "discoteca", "party", "tardeo", "club", "sunset", "after", "session"],
}


def categorizar_por_keywords(texto: str) -> str:
    t = texto.lower()
    for cat, keywords in CATEGORY_RULES.items():
        if any((re.search(rf"\b{re.escape(k)}\b", t) if len(k) > 4 else k in t) for k in keywords):
            return cat
    return "CULTURE"


def resolver_municipio(texto: str) -> str:
    t = texto.lower()
    for m in MALLORCA_MUNICIPALITIES:
        if re.search(rf"\b{re.escape(m)}\b", t):
            return m.title()
    return "Mallorca"


def html_a_eventos(html: str, today, max_date, seen_keys: Set[str]) -> List[Dict]:
    """Extrae eventos del HTML con estructura MEC. Clave de dedup: url+fecha."""
    soup = BeautifulSoup(html, "html.parser")
    eventos_raw = []

    for sec in soup.select("div[class*='mec-calendar-events-sec']"):
        cell = sec.get("data-mec-cell", "")
        if not cell or len(cell) != 8:
            continue
        try:
            fecha = datetime.strptime(cell, "%Y%m%d").date()
        except ValueError:
            continue

        if fecha < today or fecha > max_date:
            continue

        fecha_iso = fecha.strftime("%Y-%m-%d")

        for article in sec.select("article"):
            title_el = article.select_one(".mec-event-title a, h4 a, h3 a, a[href*='/evento/']")
            if not title_el:
                continue

            titulo = title_el.get_text(strip=True)
            href = title_el.get("href", "")
            if not titulo or len(titulo) < 3:
                continue

            full_url = href if href.startswith("http") else f"{BASE_URL}{href}"
            if "evento-tipo" in full_url:
                continue

            # Clave única: url + fecha (permite misma URL en distintas fechas)
            clave = f"{full_url}||{fecha_iso}"
            if clave in seen_keys:
                continue
            seen_keys.add(clave)

            loc_el = article.select_one(".mec-event-loc-title, .mec-venue, .mec-address")
            municipio_raw = loc_el.get_text(strip=True) if loc_el else ""

            eventos_raw.append({
                "title": titulo,
                "start_date": fecha_iso,
                "municipio_raw": municipio_raw,
                "website_url": full_url,
            })

    return eventos_raw


def cargar_mes_url_param(session: requests.Session, year: int, month: int) -> Optional[str]:
    """Intenta cargar mes futuro via parámetros URL."""
    urls_a_probar = [
        f"{BASE_URL}/es/calendario/?mec-month={month}&mec-year={year}",
        f"{BASE_URL}/es/calendario/?month={month}&year={year}",
        f"{BASE_URL}/es/calendario/{year}/{str(month).zfill(2)}/",
    ]
    for url in urls_a_probar:
        try:
            r = session.get(url, headers=HEADERS, timeout=15)
            if r.status_code == 200 and "mec-calendar-events-sec" in r.text:
                return r.text
        except Exception:
            continue
    return None


def extraer_categoria_evento(session: requests.Session, url: str) -> Tuple[Optional[str], Optional[str]]:
    """Visita la página del evento y extrae categoría y municipio."""
    try:
        r = session.get(url, headers=HEADERS, timeout=10)
        if r.status_code != 200:
            return None, None
        soup = BeautifulSoup(r.text, "html.parser")

        categoria_sitio = None
        for tag_el in soup.select(".mec-event-category, .mec-category, .mec-event-cats a, a[rel='category tag'], .cat-links a"):
            texto_cat = tag_el.get_text(strip=True).lower()
            if texto_cat in SITE_CATEGORY_MAP:
                categoria_sitio = SITE_CATEGORY_MAP[texto_cat]
                break

        municipio = None
        for loc_el in soup.select(".mec-event-location, .mec-venue, .mec-address, .mec-event-loc-title"):
            m = resolver_municipio(loc_el.get_text(strip=True))
            if m != "Mallorca":
                municipio = m
                break

        return categoria_sitio, municipio
    except Exception:
        return None, None


def extraer_eventos(meses_adelante: int = 3) -> List[Dict]:
    today = datetime.now().date()
    max_date = today + timedelta(days=meses_adelante * 30)
    print(f"🔍 Buscando eventos: {today} → {max_date}\n")

    session = requests.Session()
    seen_keys: Set[str] = set()
    eventos_raw: List[Dict] = []

    # Mes actual — el HTML del calendario ya lo incluye
    print("📅 Cargando calendario (mes actual)...")
    try:
        r = session.get(f"{BASE_URL}/es/calendario/", headers=HEADERS, timeout=15)
        html_base = r.text if r.status_code == 200 else ""
    except Exception as e:
        print(f"  ⚠️ Error: {e}")
        html_base = ""

    if html_base:
        mes_eventos = html_a_eventos(html_base, today, max_date, seen_keys)
        eventos_raw.extend(mes_eventos)
        print(f"   → {len(mes_eventos)} eventos")

    # Meses siguientes — via URL params
    for offset in range(1, meses_adelante):
        mes_abs = today.month - 1 + offset
        target_year = today.year + (mes_abs // 12)
        target_month = (mes_abs % 12) + 1

        print(f"📅 Cargando {target_month}/{target_year}...")
        html_mes = cargar_mes_url_param(session, target_year, target_month)
        if html_mes:
            mes_eventos = html_a_eventos(html_mes, today, max_date, seen_keys)
            eventos_raw.extend(mes_eventos)
            print(f"   → {len(mes_eventos)} eventos")
        else:
            print(f"   ⚠️ No disponible via URL — solo mes actual incluido")
        time.sleep(0.5)

    print(f"\n📋 Total eventos: {len(eventos_raw)} — extrayendo categorías...\n")

    # Caché de categorías por URL (para no visitar la misma URL múltiples veces)
    cache_cat: Dict[str, Tuple[Optional[str], Optional[str]]] = {}
    eventos_final = []

    for idx, ev in enumerate(eventos_raw):
        url = ev["website_url"]
        print(f"[{idx+1}/{len(eventos_raw)}] {ev['title'][:55]}...")

        if url not in cache_cat:
            cache_cat[url] = extraer_categoria_evento(session, url)
            time.sleep(0.2)

        categoria_sitio, municipio_evento = cache_cat[url]

        municipio = municipio_evento
        if not municipio or municipio == "Mallorca":
            # Buscar municipio en título + ubicación (el título suele incluirlo)
            texto_loc = f"{ev['title']} {ev['municipio_raw']}"
            municipio = resolver_municipio(texto_loc)

        categoria = categoria_sitio or categorizar_por_keywords(ev["title"])

        texto_check = f"{ev['title']} {ev['municipio_raw']} {municipio}".lower()
        if any(isla in texto_check for isla in EXCLUDED_ISLANDS):
            print(f"   🏝️ Isla excluida — SKIP")
            continue

        print(f"   ✅ {ev['start_date']} | {municipio} | {categoria}")

        eventos_final.append({
            "title": ev["title"],
            "start_date": ev["start_date"],
            "municipality": municipio,
            "category": categoria,
            "location_info": ev["municipio_raw"] or municipio,
            "website_url": ev["website_url"],
            "source": "firesifestes",
        })

    eventos_final.sort(key=lambda x: x["start_date"])
    return eventos_final


if __name__ == "__main__":
    print("=" * 65)
    print("🌐 Scraper Fires i Festes Mallorca")
    print("=" * 65)

    eventos = extraer_eventos(meses_adelante=3)

    print(f"\n{'='*65}")
    print(f"✅ Total eventos: {len(eventos)}")
    print("=" * 65)

    tools_dir = Path(__file__).parent
    output_path = tools_dir / "firesifestes_output.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(eventos, f, ensure_ascii=False, indent=2)
    print(f"📄 Guardado en: {output_path}")

    print("\n📊 Por categoría:")
    cats = {}
    for e in eventos:
        cats[e["category"]] = cats.get(e["category"], 0) + 1
    for cat in sorted(cats.keys()):
        print(f"  • {cat}: {cats[cat]}")
