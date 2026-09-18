#!/usr/bin/env python3
"""
Web Scraping Events for Mallorca - Optimizado con Lógica de Datos Especializada
-------------------------------------------------------------------------------
Extracción concurrente, rotación de User-Agents, control de errores granular,
categorización inteligente, parseo de fechas relativas y deduplicación por histórico.
"""

from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import hashlib
import json
import random
import re
import sys
import threading as _th
import time
import concurrent.futures
from typing import Dict, List, Optional, Set
import webbrowser

try:
    from bs4 import BeautifulSoup
    from playwright.sync_api import sync_playwright
    import requests
except ImportError:
    print("❌ Dependencias faltantes. Instala con:")
    print("   pip install requests beautifulsoup4 lxml playwright")
    print("   playwright install chromium")
    sys.exit(1)

# ============================================================================
# CONFIGURACIÓN Y CONSTANTES
# ============================================================================

TOOLS_DIR = Path(__file__).parent
REVIEW_HTML = TOOLS_DIR / "review.html"
APPROVED_EVENTS_FILE = TOOLS_DIR / "approved_events.json"

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
]

MALLORCA_MUNICIPALITIES = {
    "palma", "calvià", "manacor", "llucmajor", "marratxí", "inca", "alcúdia",
    "felanitx", "pollença", "sóller", "sa pobla", "artà", "campos", "santanyí",
    "ses salines", "capdepera", "andratx", "santa margalida", "petra", "muro",
    "alaró", "binissalem", "sant llorenç", "santa maria", "esporles", "sineu",
    "porreres", "algaida", "consell", "búger", "costitx", "ariany", "banyalbufar",
    "campanet", "deià", "escorca", "estellencs", "fornalutx", "lloret", "lloseta",
    "maria de la salut", "montuïri", "puigpunyent", "sant joan", "selva",
    "sencelles", "vilafranca",
}

CATEGORY_KEYWORDS = {
    "CONCERT": ["concierto", "concert", "música", "music", "recital", "jazz", "rock", "pop", "orquestra", "sinfónica", "banda", "dj", "acústico"],
    "FESTIVAL": ["festival", "feria", "fiesta", "festa", "celebración", "verbena", "carnaval"],
    "SPORT": ["deporte", "sport", "triatlón", "maratón", "ciclismo", "running", "vela", "regata", "torneo", "campeonato", "pádel", "surf", "sup"],
    "CULTURE": ["exposición", "exhibition", "teatro", "theatre", "danza", "dance", "conferencia", "obra", "museo", "arte", "literatura", "cine"],
    "GASTRONOMY": ["gastronómico", "gastronomic", "cata", "tasting", "culinaria", "degustación", "tapas", "vino", "maridaje", "restaurante", "chef", "rodaballo", "bacalao"],
    "MARKET": ["mercado", "market", "mercat", "artesanía", "fira", "mercadillo", "rastro"],
    "NIGHTLIFE": ["party", "noche", "night", "discoteca", "club", "tardeo"],
    "FAMILY": ["familia", "family", "niños", "kids", "children", "infantil", "familiar", "cuentacuentos", "marionetas", "títeres"],
}

# ============================================================================
# LÓGICA DE DATOS ESPECIALIZADA
# ============================================================================

def get_random_headers() -> Dict[str, str]:
    return {
        "User-Agent": random.choice(USER_AGENTS),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "es-ES,es;q=0.9",
        "Connection": "keep-alive"
    }

def fetch_soup_requests(url: str, timeout: int = 15) -> Optional[BeautifulSoup]:
    try:
        response = requests.get(url, headers=get_random_headers(), timeout=timeout)
        response.raise_for_status()
        return BeautifulSoup(response.content, "html.parser")
    except Exception as e:
        print(f"      ❌ Fallo de conexión en {url}: {e}")
        return None

def generate_event_id(source: str, title: str, date: str) -> str:
    """Genera un hash único basado en la fuente, el título y la fecha exacta."""
    content = f"{source}-{title}-{date}"
    return f"web-{source}-{hashlib.md5(content.encode()).hexdigest()[:12]}"

def detect_category_smart(title: str, description: str = "") -> str:
    """Asigna categoría analizando palabras clave exactas. Prioriza el título sobre la descripción."""
    title_lower = title.lower()
    desc_lower = description.lower()

    # 1. Buscar coincidencias en el título (peso alto)
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(re.search(rf"\b{kw}\b", title_lower) for kw in keywords):
            return category

    # 2. Buscar coincidencias en la descripción (peso bajo)
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(re.search(rf"\b{kw}\b", desc_lower) for kw in keywords):
            return category

    return "CULTURE"

def parse_date_advanced(date_str: str) -> Optional[str]:
    """Convierte fechas relativas y formatos de texto a AAAA-MM-DD estricto."""
    if not date_str:
        return None

    date_str = date_str.lower().strip()
    # Limpiar horas comunes: ", 21:00 h." o "a las 20:00h."
    date_str = re.sub(r",?\s*\d{1,2}:\d{2}\s*h?\.?", "", date_str, flags=re.IGNORECASE)

    today = datetime.now()

    # 1. Fechas Relativas
    if date_str == "hoy": return today.strftime("%Y-%m-%d")
    if date_str in ["mañana", "manana"]: return (today + timedelta(days=1)).strftime("%Y-%m-%d")

    dias_semana = {"lunes": 0, "martes": 1, "miércoles": 2, "miercoles": 2, "jueves": 3, "viernes": 4, "sábado": 5, "sabado": 5, "domingo": 6}
    for prefijo in ["este ", "el próximo ", "el proximo ", "próximo ", "proximo "]:
        if date_str.startswith(prefijo):
            dia = date_str.replace(prefijo, "").strip()
            if dia in dias_semana:
                days_ahead = dias_semana[dia] - today.weekday()
                if days_ahead <= 0: days_ahead += 7
                return (today + timedelta(days=days_ahead)).strftime("%Y-%m-%d")

    # 2. Reemplazos de meses (completos y abreviados)
    meses = {"enero": "01", "febrero": "02", "marzo": "03", "abril": "04", "mayo": "05", "junio": "06",
             "julio": "07", "agosto": "08", "septiembre": "09", "octubre": "10", "noviembre": "11", "diciembre": "12",
             "ene": "01", "ene.": "01", "feb": "02", "feb.": "02", "mar": "03", "mar.": "03",
             "abr": "04", "abr.": "04", "may": "05", "may.": "05", "jun": "06", "jun.": "06",
             "jul": "07", "jul.": "07", "ago": "08", "ago.": "08", "sep": "09", "sep.": "09",
             "oct": "10", "oct.": "10", "nov": "11", "nov.": "11", "dic": "12", "dic.": "12"}

    date_normalized = date_str
    for mes_nombre, mes_num in meses.items():
        date_normalized = re.sub(rf"\b{mes_nombre}\b", mes_num, date_normalized)

    # 3. Formatos estándar numéricos
    formatos = ["%d/%m/%Y", "%d-%m-%Y", "%Y-%m-%d", "%d de %m de %Y", "%d %m %Y", "%d %m. %Y", "%d.%m.%Y"]
    for fmt in formatos:
        try:
            return datetime.strptime(date_normalized, fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue

    # 4. Expresión regular como fallback final ("24 de oct de 2026", "24/10/26")
    match = re.search(r"(\d{1,2})[/\-\s]+(?:de\s+)?(\d{1,2})[/\-\s\.]+(?:de\s+)?(\d{4})", date_normalized)
    if match:
        day, month, year = match.groups()
        try:
            return datetime(int(year), int(month), int(day)).strftime("%Y-%m-%d")
        except ValueError:
            pass

    # 5. Formato sin año: "11 09." → asumir año actual o siguiente
    match_no_year = re.search(r"(\d{1,2})[/\-\s\.]+(\d{1,2})[/\-\s\.]*$", date_normalized)
    if match_no_year:
        day, month = match_no_year.groups()
        try:
            # Intentar con año actual
            candidate = datetime(today.year, int(month), int(day))
            # Si ya pasó, usar año siguiente
            if candidate.date() < today.date():
                candidate = datetime(today.year + 1, int(month), int(day))
            return candidate.strftime("%Y-%m-%d")
        except ValueError:
            pass

    return None

def normalize_municipality(location: str) -> Optional[str]:
    if not location: return None
    location_lower = location.lower().strip()
    if location_lower in MALLORCA_MUNICIPALITIES: return location.title()
    for municipality in MALLORCA_MUNICIPALITIES:
        if municipality in location_lower: return municipality.title()
    if "palma" in location_lower: return "Palma"
    return None

def extract_price(text: str) -> tuple[bool, Optional[str]]:
    text_lower = text.lower()
    if any(w in text_lower for w in ["gratis", "gratuito", "free", "entrada libre", "sin coste"]):
        return True, None
    price_match = re.search(r"(\d+(?:[.,]\d{2})?)\s*€", text)
    if price_match:
        return False, f"{price_match.group(1).replace(',', '.')}€"
    return True, None

def _normalize_title(t: str) -> str:
    t = t.lower().strip()
    t = re.sub(r"\s*[-–|·]\s*mallorca.*$", "", t)
    t = re.sub(r"\s*\(.*?\)", "", t)
    t = re.sub(r"[^\w\s]", "", t)
    import unicodedata
    return "".join(c for c in unicodedata.normalize("NFD", t) if unicodedata.category(c) != "Mn").strip()

def load_historical_ids(filepath: Path) -> Set[str]:
    """Carga los identificadores de los eventos ya procesados para omitirlos durante la extracción."""
    if not filepath.exists():
        return set()
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
            return {e.get("id") for e in data if "id" in e}
    except Exception as e:
        print(f"      ⚠️  No se pudo leer el histórico: {e}")
        return set()

def deduplicate_events(events: List[Dict], historical_ids: Set[str]) -> List[Dict]:
    seen_exact, seen_fuzzy = set(), set()
    unique_events = []
    TITLE_BLACKLIST = ["secrets night", "secrets mallorca"]

    for event in events:
        # Bloqueo por blacklist
        if any(bl in event.get("title", "").lower() for bl in TITLE_BLACKLIST):
            continue

        # Normalización final de fecha
        parsed_date = parse_date_advanced(event["start_date"])
        if not parsed_date:
            continue
        event["start_date"] = parsed_date

        # Generación del ID Único y cruce con histórico
        event_id = generate_event_id(event["source"], event["title"], event["start_date"])
        if event_id in historical_ids:
            continue
        event["id"] = event_id

        # Deduplicación de sesión (Exacta y Fuzzy)
        title_raw = event["title"].lower().strip()
        exact_key = (title_raw, event["start_date"])
        if exact_key in seen_exact:
            continue

        fuzzy_key = (_normalize_title(event["title"])[:40], event["start_date"])
        if fuzzy_key in seen_fuzzy:
            continue

        seen_exact.add(exact_key)
        seen_fuzzy.add(fuzzy_key)
        unique_events.append(event)

    return unique_events

# ============================================================================
# SCRAPERS ESPECÍFICOS (Implementando funciones modulares)
# ============================================================================

def scrape_auditorium_palma() -> List[Dict]:
    events = []
    url = "https://auditoriumpalma.com/es/"
    try:
        print(f"   🔍 Scraping auditorium...")
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=random.choice(USER_AGENTS))
            page.goto(url, wait_until="domcontentloaded", timeout=60000)
            page.wait_for_timeout(4000)
            soup = BeautifulSoup(page.content(), "html.parser")
            browser.close()

            for card in soup.select("article.elementor-post"):
                try:
                    link_elem = card.select_one('a[href*="/espectaculo/"]')
                    if not link_elem: continue

                    title_elem = card.select_one("h3, h2, .elementor-post__title")
                    title = title_elem.get_text(strip=True) if title_elem else next((line.strip() for line in card.select_one(".elementor-post__text").stripped_strings), None)
                    if not title: continue

                    date_str = None
                    date_elem = card.select_one(".elementor-post-date")
                    if date_elem:
                        raw_date = date_elem.get_text(strip=True)
                        # Limpiar formato auditorium: "Del11 Sep.al12 Sep. 2026" → "11 Sep. 2026"
                        # o "19 Sep. 2026a las 20:00h." → "19 Sep. 2026"
                        if "al" in raw_date.lower():
                            # Rango: tomar la primera fecha
                            raw_date = re.sub(r"^Del", "", raw_date, flags=re.IGNORECASE).split("al")[0].strip()
                        # Remover hora "a las XX:XXh."
                        raw_date = re.sub(r"a las \d{1,2}:\d{2}h?\.", "", raw_date, flags=re.IGNORECASE).strip()
                        date_str = raw_date

                    text = card.get_text()
                    is_free, price = (False, "Consultar web") if "comprar entrada" in text.lower() else extract_price(text)

                    if title and date_str:
                        events.append({
                            "source": "auditorium",
                            "title": title,
                            "description": text[:200],
                            "start_date": date_str,
                            "municipality": "Palma",
                            "category": detect_category_smart(title, text),
                            "is_free": is_free,
                            "price": price,
                            "website_url": link_elem.get("href", url),
                        })
                except Exception as e:
                    print(f"      ⚠️ Error en card auditorium: {e}")
                    continue
        print(f"      ✅ auditorium: {len(events)} eventos extraídos")
    except Exception as e:
        print(f"      ❌ Error global auditorium: {e}")
    return events


def scrape_mallorcamusicmagazine() -> List[Dict]:
    events = []
    url = "https://mallorcamusicmagazine.com/conciertos/"
    try:
        print(f"   🔍 Scraping mallorcamusicmagazine...")
        soup = fetch_soup_requests(url)
        if soup:
            for item in soup.select("li.qodef-blog-list-item"):
                try:
                    link_elem = item.select_one('a[href*="/concierto/"]')
                    if not link_elem: continue

                    text = item.get_text()
                    lines = [line.strip() for line in text.split("\n") if line.strip()]
                    title = next((line for line in lines if len(line) > 3 and line not in ["DESTACADOS", "MÚSICA", "DEPORTES"]), None)
                    if not title: continue

                    date_str = next((line for line in lines if re.search(r"\d{1,2}\s+\w+\s+\d{4}", line)), None)

                    municipality = "Palma"
                    for muni in ["inca", "manacor", "alcúdia", "pollença", "sóller"]:
                        if muni in text.lower():
                            municipality = muni.title(); break

                    is_free, price = extract_price(text)
                    if title and date_str:
                        events.append({
                            "source": "mallorcamusicmagazine",
                            "title": title,
                            "description": "",
                            "start_date": date_str,
                            "municipality": municipality,
                            "category": detect_category_smart(title, text),
                            "is_free": is_free,
                            "price": price or "Consultar web",
                            "website_url": link_elem.get("href", url),
                        })
                except Exception as e:
                    print(f"      ⚠️ Error en item mallorcamusicmagazine: {e}")
                    continue
        print(f"      ✅ mallorcamusicmagazine: {len(events)} eventos extraídos")
    except Exception as e:
        print(f"      ❌ Error global mallorcamusicmagazine: {e}")
    return events

# ============================================================================
# CONTROLADOR PRINCIPAL
# ============================================================================

def scrape_all_sources() -> List[Dict]:
    print("🌐 Iniciando extracción con control de duplicados y categorías...")
    historical_ids = load_historical_ids(APPROVED_EVENTS_FILE)
    if historical_ids:
        print(f"   ℹ️  Cargados {len(historical_ids)} eventos históricos para omitir.")
    print("=" * 60)

    all_events = []
    scrapers = [
        scrape_auditorium_palma,
        scrape_mallorcamusicmagazine,
        # Resto de tus scrapers...
    ]

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        futures = {executor.submit(scraper): scraper.__name__ for scraper in scrapers}
        for future in concurrent.futures.as_completed(futures):
            try:
                all_events.extend(future.result())
            except Exception as e:
                print(f"❌ Fallo en Worker {futures[future]}: {e}")

    # Filtrar, normalizar fechas y aplicar cruces deduplicadores
    all_events = deduplicate_events(all_events, historical_ids)

    today = datetime.now().date()
    future_events = []
    for event in all_events:
        try:
            if datetime.strptime(event["start_date"], "%Y-%m-%d").date() >= today:
                future_events.append(event)
        except:
            pass

    print("=" * 60)
    print(f"✅ Total eventos nuevos y validados: {len(future_events)}")

    # Mostrar resumen
    if future_events:
        print("\n📋 Primeros 5 eventos:")
        for i, e in enumerate(future_events[:5], 1):
            print(f"   {i}. {e['title'][:50]:50} | {e['start_date']} | {e['category']}")

    return future_events

if __name__ == "__main__":
    scrape_all_sources()
