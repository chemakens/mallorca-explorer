#!/usr/bin/env python3
"""
Web Scraping Events for Mallorca - Optimizado Total
-------------------------------------------------------------------------------
Extracción concurrente completa, rotación de User-Agents, control de errores,
categorización inteligente, parseo de fechas relativas, deduplicación e imágenes.
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
    "CONCERT": ["concierto", "concert", "música", "music", "recital", "jazz", "rock", "pop", "orquestra", "sinfónica", "banda", "dj", "acústico", "musical", "cantante", "grupo"],
    "FESTIVAL": ["festival", "feria", "fiesta", "festa", "celebración", "verbena", "carnaval", "vermar", "fires", "festes"],
    "SPORT": ["deporte", "sport", "triatlón", "maratón", "ciclismo", "running", "vela", "regata", "torneo", "campeonato", "pádel", "surf", "sup", "carrera", "natación", "yoga"],
    "CULTURE": ["exposición", "exhibition", "teatro", "theatre", "danza", "dance", "conferencia", "obra", "museo", "arte", "literatura", "cine", "película", "documental", "poesía"],
    "GASTRONOMY": ["gastronómico", "gastronomic", "cata", "tasting", "culinaria", "degustación", "tapas", "vino", "maridaje", "restaurante", "chef", "rodaballo", "bacalao", "brunch", "cena", "barbacoa", "cocina"],
    "MARKET": ["mercado", "market", "mercat", "artesanía", "fira", "mercadillo", "rastro", "artesano"],
    "NIGHTLIFE": ["party", "noche", "night", "discoteca", "club", "tardeo", "dj set", "techno", "house"],
    "FAMILY": ["familia", "family", "niños", "kids", "children", "infantil", "familiar", "cuentacuentos", "marionetas", "títeres", "campamento"],
}

# ============================================================================
# LÓGICA DE DATOS ESPECIALIZADA Y FUNCIONES AUXILIARES
# ============================================================================

def get_random_headers() -> Dict[str, str]:
    return {
        "User-Agent": random.choice(USER_AGENTS),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
        "Connection": "keep-alive"
    }

def fetch_soup_requests(url: str, timeout: int = 15) -> Optional[BeautifulSoup]:
    try:
        response = requests.get(url, headers=get_random_headers(), timeout=timeout)
        response.raise_for_status()
        return BeautifulSoup(response.content, "html.parser")
    except Exception as e:
        return None

def generate_event_id(source: str, title: str, date: str) -> str:
    content = f"{source}-{title}-{date}"
    return f"web-{source}-{hashlib.md5(content.encode()).hexdigest()[:12]}"

def detect_category_smart(title: str, description: str = "") -> str:
    title_lower = title.lower()
    desc_lower = description.lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(re.search(rf"\b{kw}\b", title_lower) for kw in keywords):
            return category
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(re.search(rf"\b{kw}\b", desc_lower) for kw in keywords):
            return category
    return "CULTURE"

def parse_date_advanced(date_str: str) -> Optional[str]:
    """Convierte fechas relativas y formatos de texto a AAAA-MM-DD estricto."""
    if not date_str: return None
    
    date_str = date_str.lower().strip()
    # ✅ FIX 1: Limpiar horas comunes antes del parseo
    date_str = re.sub(r",?\s*\d{1,2}:\d{2}\s*h?\.?", "", date_str, flags=re.IGNORECASE)
    # Limpiar rangos de fechas: "Del11 Sep.al12 Sep. 2026" → "11 Sep. 2026"
    if "al" in date_str.lower():
        date_str = re.sub(r"^Del", "", date_str, flags=re.IGNORECASE).split("al")[0].strip()
    # Remover horas adicionales: "a las XX:XXh."
    date_str = re.sub(r"a las \d{1,2}:\d{2}h?\.", "", date_str, flags=re.IGNORECASE).strip()
    
    today = datetime.now()

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

    # ✅ FIX 2: Agregar soporte para meses abreviados con punto
    meses = {"enero": "01", "febrero": "02", "marzo": "03", "abril": "04", "mayo": "05", "junio": "06",
             "julio": "07", "agosto": "08", "septiembre": "09", "octubre": "10", "noviembre": "11", "diciembre": "12",
             "ene": "01", "ene.": "01", "feb": "02", "feb.": "02", "mar": "03", "mar.": "03",
             "abr": "04", "abr.": "04", "may": "05", "may.": "05", "jun": "06", "jun.": "06", 
             "jul": "07", "jul.": "07", "ago": "08", "ago.": "08", "sep": "09", "sep.": "09",
             "oct": "10", "oct.": "10", "nov": "11", "nov.": "11", "dic": "12", "dic.": "12"}
    
    date_normalized = date_str
    for mes_nombre, mes_num in meses.items():
        date_normalized = re.sub(rf"\b{mes_nombre}\b", mes_num, date_normalized)

    formatos = ["%d/%m/%Y", "%d-%m-%Y", "%Y-%m-%d", "%d de %m de %Y", "%d %m %Y", "%d %m. %Y", "%d.%m.%Y"]
    for fmt in formatos:
        try:
            return datetime.strptime(date_normalized, fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue

    match = re.search(r"(\d{1,2})[/\-\s]+(?:de\s+)?(\d{1,2})[/\-\s\.]+(?:de\s+)?(\d{4})", date_normalized)
    if match:
        day, month, year = match.groups()
        try:
            return datetime(int(year), int(month), int(day)).strftime("%Y-%m-%d")
        except ValueError:
            pass

    # ✅ FIX 3: Formato sin año: "11 09." → asumir año actual o siguiente
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
    if not filepath.exists(): return set()
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            return {e.get("id") for e in json.load(f) if "id" in e}
    except Exception:
        return set()

def deduplicate_events(events: List[Dict], historical_ids: Set[str]) -> List[Dict]:
    seen_exact, seen_fuzzy = set(), set()
    unique_events = []
    TITLE_BLACKLIST = ["secrets night", "secrets mallorca"]

    for event in events:
        if any(bl in event.get("title", "").lower() for bl in TITLE_BLACKLIST): continue

        parsed_date = parse_date_advanced(event.get("start_date", ""))
        if not parsed_date: continue
        event["start_date"] = parsed_date

        event_id = generate_event_id(event["source"], event["title"], event["start_date"])
        if event_id in historical_ids: continue
        event["id"] = event_id

        title_raw = event["title"].lower().strip()
        exact_key = (title_raw, event["start_date"])
        if exact_key in seen_exact: continue
        
        fuzzy_key = (_normalize_title(event["title"])[:60], event["start_date"])  # ✅ FIX: Aumentado de 40 a 60 caracteres
        if fuzzy_key in seen_fuzzy: continue

        seen_exact.add(exact_key)
        seen_fuzzy.add(fuzzy_key)
        unique_events.append(event)

    return unique_events

# ============================================================================
# SCRAPERS (Incluye mallorca.com adaptado para extracción de imágenes)
# ============================================================================

def scrape_auditorium_palma() -> List[Dict]:
    events = []
    url = "https://auditoriumpalma.com/es/"
    print("   🔍 Scraping: auditorium_palma...")
    try:
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

                    date_str = card.select_one(".elementor-post-date").get_text(strip=True) if card.select_one(".elementor-post-date") else None
                    text = card.get_text()
                    is_free, price = (False, "Consultar web") if "comprar entrada" in text.lower() else extract_price(text)

                    if title and date_str:
                        events.append({
                            "source": "auditorium",
                            "title": title,
                            "start_date": date_str,
                            "municipality": "Palma",
                            "category": detect_category_smart(title, text),
                            "is_free": is_free,
                            "price": price,
                            "website_url": link_elem.get("href", url),
                        })
                except Exception: continue
        print(f"      ✅ auditorium_palma: {len(events)} eventos")
    except Exception: pass
    return events


def scrape_firesifestes() -> List[Dict]:
    events = []
    base_url = "https://firesifestes.es"
    today = datetime.now()
    print("   🔍 Scraping: firesifestes...")
    try:
        api_base = f"{base_url}/wp-json/tribe/events/v1/events"
        start_dt = today.strftime("%Y-%m-%d")
        end_date = (today.replace(year=today.year + 1)).strftime("%Y-%m-%d")
        page_num, per_page = 1, 50
        while True:
            params = {"start_date": start_dt, "end_date": end_date, "per_page": per_page, "page": page_num, "status": "publish"}
            try:
                resp = requests.get(api_base, params=params, headers=get_random_headers(), timeout=15)
                if resp.status_code != 200: break
                data = resp.json()
            except Exception: break
            
            items = data.get("events", [])
            if not items: break

            for ev in items:
                url = ev.get("url", "")
                title = ev.get("title", "").strip()
                start_date_raw = ev.get("start_date", "")[:10]
                if not url or not title or not start_date_raw: continue

                venue = ev.get("venue", {}) or {}
                city = venue.get("city", "") or ""
                desc_clean = re.sub(r"<[^>]+>", " ", ev.get("description", "") or "")[:300]
                is_free, price = extract_price(desc_clean + " " + title)

                events.append({
                    "source": "firesifestes",
                    "title": title[:100],
                    "start_date": start_date_raw,
                    "municipality": normalize_municipality(city + " " + title) or "Mallorca",
                    "category": detect_category_smart(title, desc_clean),
                    "is_free": is_free,
                    "price": price,
                    "website_url": url,
                })
            
            if page_num >= data.get("total_pages", 1): break
            page_num += 1
        print(f"      ✅ firesifestes: {len(events)} eventos")
    except Exception: pass
    return events


def scrape_mallorcafiestas() -> List[Dict]:
    events = []
    base_url = "https://mallorcafiestas.com"
    print("   🔍 Scraping: mallorcafiestas...")
    try:
        today = datetime.now()
        urls_to_scrape = [base_url]
        current_date = today
        for _ in range(6):
            urls_to_scrape.append(f"{base_url}/{current_date.year}/{current_date.month:02d}/")
            current_date = current_date.replace(year=current_date.year + 1, month=1, day=1) if current_date.month == 12 else current_date.replace(month=current_date.month + 1, day=1)

        for url in urls_to_scrape:
            try:
                soup = fetch_soup_requests(url)
                if not soup: continue
                for link in soup.find_all("a", href=True):
                    href = link.get("href", "")
                    title = link.get_text(strip=True)
                    if not title or len(title) < 3 or re.match(r"^[\w.-]+\.(com|es|net|org|info)\.?$", title, re.IGNORECASE): continue

                    parent = link.parent
                    text = parent.get_text() if parent else ""
                    municipality = normalize_municipality(text) or "Mallorca"
                    if municipality != "Mallorca": title = re.sub(r"\s*" + re.escape(municipality) + r"\s*$", "", title, flags=re.IGNORECASE).strip()
                    
                    date_match = re.search(r"/(\d{4})/(\d{2})/(\d{2})/", href)
                    if date_match:
                        date_str = f"{date_match.group(1)}-{date_match.group(2)}-{date_match.group(3)}"
                    elif "fiestas" in href or "eventos" in href:
                        date_str = today.strftime("%Y-%m-%d")
                    else: continue

                    events.append({
                        "source": "mallorcafiestas",
                        "title": title[:100],
                        "start_date": date_str,
                        "municipality": municipality,
                        "category": detect_category_smart(title, text),
                        "is_free": extract_price(text)[0],
                        "price": extract_price(text)[1],
                        "website_url": href if href.startswith("http") else f"{base_url}{href}",
                    })
            except Exception: continue
        print(f"      ✅ mallorcafiestas: {len(events)} eventos")
    except Exception: pass
    return events


def scrape_mallorcamusicmagazine() -> List[Dict]:
    events = []
    url = "https://mallorcamusicmagazine.com/conciertos/"
    print("   🔍 Scraping: mallorcamusicmagazine...")
    try:
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
                        if muni in text.lower(): municipality = muni.title(); break

                    events.append({
                        "source": "mallorcamusicmagazine",
                        "title": title,
                        "start_date": date_str,
                        "municipality": municipality,
                        "category": detect_category_smart(title, text),
                        "is_free": extract_price(text)[0],
                        "price": extract_price(text)[1] or "Consultar web",
                        "website_url": link_elem.get("href", url),
                    })
                except Exception: continue
        print(f"      ✅ mallorcamusicmagazine: {len(events)} eventos")
    except Exception: pass
    return events


def scrape_conciertos_club() -> List[Dict]:
    events = []
    url = "https://conciertos.club/baleares"
    print("   🔍 Scraping: conciertos.club...")
    try:
        soup = fetch_soup_requests(url)
        if soup:
            for item in soup.select('article li[itemprop="itemListElement"]'):
                try:
                    text = item.get_text()
                    lines = [line.strip() for line in text.split("\n") if line.strip()]
                    link_elem = item.select_one('a[href^="/baleares/conciertos/"]')
                    if not link_elem: continue
                    
                    title = next((line for line in lines if 2 < len(line) < 100 and not re.match(r"^\d{2}:\d{2}$", line) and not line.startswith("/") and "Palma" not in line and "Baleares" not in line and "Comprar" not in line), None)
                    if not title: continue

                    date_str = None
                    parent_article = item.find_parent("article")
                    if parent_article and parent_article.select_one(".tit"):
                        date_str = parent_article.select_one(".tit").get_text(strip=True) + f" {datetime.now().year}"

                    if title and date_str:
                        events.append({
                            "source": "conciertos.club",
                            "title": title,
                            "start_date": date_str,
                            "municipality": normalize_municipality(text) or "Palma",
                            "category": detect_category_smart(title, text),
                            "is_free": extract_price(text)[0],
                            "price": extract_price(text)[1] or "Consultar web",
                            "website_url": "https://conciertos.club" + link_elem.get("href", ""),
                        })
                except Exception: continue
        print(f"      ✅ conciertos.club: {len(events)} eventos")
    except Exception: pass
    return events


def scrape_mallorca_com() -> List[Dict]:
    events = []
    print("   🔍 Scraping: mallorca.com (Múltiples categorías, scroll profundo y concurrencia)...")

    # 1. Lista de categorías explícitas (se excluye /gastronomia para evitar duplicados)
    categorias = [
        "https://www.mallorca.com/es/eventos/conciertos",
        "https://www.mallorca.com/es/eventos/teatro",
        "https://www.mallorca.com/es/eventos/deportes",
        "https://www.mallorca.com/es/eventos/familia",
        "https://www.mallorca.com/es/eventos/arte-exposiciones",
        "https://www.mallorca.com/es/eventos/fiestas",
        "https://www.mallorca.com/es/eventos/mercados",
        "https://www.mallorca.com/es/eventos/vida-nocturna"
    ]

    try:
        event_links = set()

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=random.choice(USER_AGENTS))

            for categoria_url in categorias:
                try:
                    page.goto(categoria_url, wait_until="networkidle", timeout=60000)
                    page.wait_for_timeout(2000)

                    # 2. Scroll profundo: 15 clics para cargar más contenido
                    for _ in range(15):
                        try:
                            load_more = page.query_selector('button:has-text("Más"), button:has-text("Cargar más")')
                            if load_more and load_more.is_visible():
                                load_more.click()
                                page.wait_for_timeout(1500)
                            else:
                                break
                        except:
                            break

                    soup_main = BeautifulSoup(page.content(), "html.parser")

                    # 3. Filtro estricto con regex: /es/eventos/titulo o /es/eventos/categoria/titulo
                    for a_tag in soup_main.find_all("a", href=True):
                        href = a_tag.get("href")
                        if href and (re.match(r"^/es/eventos/[^/]+$", href) or re.match(r"^/es/eventos/[a-z]+/[^/]+$", href)):
                            full_url = f"https://www.mallorca.com{href}"
                            event_links.add(full_url)
                except Exception as e:
                    print(f"      ⚠️ Error en categoría {categoria_url}: {e}")
                    continue

            browser.close()

        print(f"      ▶ Encontrados {len(event_links)} enlaces en mallorca.com. Extrayendo detalles a alta velocidad...")

        # 4. Función de extracción para procesar concurrentemente
        def process_event(event_url):
            try:
                soup_event = fetch_soup_requests(event_url)
                if not soup_event: return None

                title_elem = soup_event.find("h1")
                if not title_elem: return None
                title = title_elem.get_text(strip=True)

                fecha_exacta = None
                municipio = "Mallorca"
                precio = "Consultar web"
                imagen_url = None

                meta_image = soup_event.find("meta", property="og:image")
                if meta_image and meta_image.get("content"):
                    imagen_url = meta_image.get("content")
                else:
                    main_img = soup_event.find("img")
                    if main_img and main_img.get("src"):
                        src = main_img.get("src")
                        imagen_url = src if src.startswith("http") else f"https://www.mallorca.com{src}"

                texto_pagina = soup_event.get_text(" ", strip=True)

                # 5. Búsqueda de fechas por la palabra clave CUÁNDO con fallback robusto
                match_cuando = re.search(r"CUÁNDO\s+(.*?)(?:DÓNDE|PRECIO|MÁS INFORMACIÓN|COMPRAR|$)", texto_pagina, re.IGNORECASE)
                if match_cuando:
                    fecha_exacta = parse_date_advanced(match_cuando.group(1))

                # Fallback por si la web no usa "CUÁNDO"
                if not fecha_exacta:
                    match_fallback = re.search(r"(\d{1,2}\s+de\s+[a-zA-Z]+(?:\s+de\s+\d{4})?)", texto_pagina, re.IGNORECASE)
                    if match_fallback:
                        fecha_exacta = parse_date_advanced(match_fallback.group(1))

                match_donde = re.search(r"DÓNDE\s+([a-zA-ZáéíóúÁÉÍÓÚñÑ]+)", texto_pagina, re.IGNORECASE)
                if match_donde: municipio = normalize_municipality(match_donde.group(1)) or municipio

                match_precio = re.search(r"PRECIO\s+([^€]+€|Entrada gratuita|Gratis)", texto_pagina, re.IGNORECASE)
                if match_precio:
                    val_precio = match_precio.group(1).strip()
                    is_free, precio_extraido = extract_price(val_precio)
                    precio = "0,00€" if is_free else (precio_extraido or val_precio)
                else:
                    is_free = False

                if fecha_exacta:
                    return {
                        "source": "mallorca.com",
                        "title": title[:100],
                        "start_date": fecha_exacta,
                        "municipality": municipio,
                        "category": detect_category_smart(title, texto_pagina),
                        "is_free": is_free,
                        "price": precio,
                        "website_url": event_url,
                        "image_url": imagen_url
                    }
            except Exception: pass
            return None

        # 6. Procesamiento en paralelo de los detalles (10 workers)
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
            results = executor.map(process_event, event_links)
            for res in results:
                if res: events.append(res)

        print(f"      ✅ mallorca.com: {len(events)} eventos detallados extraídos")
    except Exception as e: print(f"      ❌ Error mallorca.com: {e}")
    return events


def scrape_mallorca_com_gastronomia() -> List[Dict]:
    events = []
    url = "https://www.mallorca.com/es/eventos/gastronomia"
    print("   🔍 Scraping: mallorca_com_gastronomia (Scroll profundo y concurrencia)...")
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=random.choice(USER_AGENTS))
            page.goto(url, wait_until="networkidle", timeout=60000)
            page.wait_for_timeout(2000)

            # Ampliado a 15 clics
            for _ in range(15):
                try:
                    load_more = page.query_selector('button:has-text("Más"), button:has-text("Cargar más")')
                    if load_more and load_more.is_visible():
                        load_more.click()
                        page.wait_for_timeout(1500)
                    else:
                        break
                except:
                    break

            soup_main = BeautifulSoup(page.content(), "html.parser")
            browser.close()

            event_links = set()
            for a_tag in soup_main.find_all("a", href=True):
                href = a_tag.get("href")
                # Filtro estricto con regex
                if href and (re.match(r"^/es/eventos/[^/]+$", href) or re.match(r"^/es/eventos/[a-z]+/[^/]+$", href)):
                    full_url = f"https://www.mallorca.com{href}"
                    event_links.add(full_url)

        print(f"      ▶ Encontrados {len(event_links)} enlaces en gastronomia. Extrayendo detalles...")

        def process_gastro_event(event_url):
            try:
                soup_event = fetch_soup_requests(event_url)
                if not soup_event: return None

                title_elem = soup_event.find("h1")
                if not title_elem: return None
                title = title_elem.get_text(strip=True)

                fecha_exacta = None
                municipio = "Mallorca"
                precio = "Consultar web"
                imagen_url = None

                meta_image = soup_event.find("meta", property="og:image")
                if meta_image and meta_image.get("content"):
                    imagen_url = meta_image.get("content")
                else:
                    main_img = soup_event.find("img")
                    if main_img and main_img.get("src"):
                        src = main_img.get("src")
                        imagen_url = src if src.startswith("http") else f"https://www.mallorca.com{src}"

                texto_pagina = soup_event.get_text(" ", strip=True)

                # Busca todo el bloque posterior a CUÁNDO, apoyándose en parse_date_advanced
                match_cuando = re.search(r"CUÁNDO\s+(.*?)(?:DÓNDE|PRECIO|MÁS INFORMACIÓN|COMPRAR|$)", texto_pagina, re.IGNORECASE)
                if match_cuando:
                    fecha_exacta = parse_date_advanced(match_cuando.group(1))

                if not fecha_exacta:
                    match_fallback = re.search(r"(\d{1,2}\s+de\s+[a-zA-Z]+(?:\s+de\s+\d{4})?)", texto_pagina, re.IGNORECASE)
                    if match_fallback:
                        fecha_exacta = parse_date_advanced(match_fallback.group(1))

                match_donde = re.search(r"DÓNDE\s+([a-zA-ZáéíóúÁÉÍÓÚñÑ]+)", texto_pagina, re.IGNORECASE)
                if match_donde: municipio = normalize_municipality(match_donde.group(1)) or municipio

                match_precio = re.search(r"PRECIO\s+([^€]+€|Entrada gratuita|Gratis)", texto_pagina, re.IGNORECASE)
                if match_precio:
                    val_precio = match_precio.group(1).strip()
                    is_free, precio_extraido = extract_price(val_precio)
                    precio = "0,00€" if is_free else (precio_extraido or val_precio)
                else:
                    is_free = False

                if fecha_exacta:
                    return {
                        "source": "mallorca.com_gastro",
                        "title": title[:100],
                        "start_date": fecha_exacta,
                        "municipality": municipio,
                        "category": detect_category_smart(title, "gastronomía"),
                        "is_free": is_free,
                        "price": precio,
                        "website_url": event_url,
                        "image_url": imagen_url
                    }
            except Exception: pass
            return None

        # Procesamiento en paralelo de los detalles (10 workers)
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
            results = executor.map(process_gastro_event, event_links)
            for res in results:
                if res: events.append(res)

        print(f"      ✅ mallorca_com_gastronomia: {len(events)} eventos detallados extraídos")
    except Exception as e: print(f"      ❌ Error mallorca_com_gastronomia: {e}")
    return events



def scrape_ime_palma() -> List[Dict]:
    events = []
    base_url = "https://ime.palma.es/es/eventos"
    print("   🔍 Scraping: ime_palma (Navegación Playwright + extracción detallada)...")

    try:
        event_links = set()

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=random.choice(USER_AGENTS))

            page.goto(base_url, wait_until="networkidle", timeout=60000)
            page.wait_for_timeout(3000)

            # Hacer scroll 5 veces para cargar contenido lazy
            for _ in range(5):
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                page.wait_for_timeout(1500)

            soup_main = BeautifulSoup(page.content(), "html.parser")

            # Extraer enlaces de eventos: /-/ o /evento/ o slugs bajo /es/eventos/
            for a_tag in soup_main.find_all("a", href=True):
                href = a_tag.get("href")
                if not href:
                    continue
                # Filtrar enlaces de eventos: /-/ o /evento/ o terminan en slug bajo /es/eventos/
                if ("/-/" in href or "/evento/" in href or
                    (href.startswith("/es/eventos/") and len(href.split("/")) >= 4)):
                    full_url = href if href.startswith("http") else f"https://ime.palma.es{href}"
                    event_links.add(full_url)

            browser.close()

        # Limitar a 60 enlaces máximo
        event_links = list(event_links)[:60]

        # Visitar cada enlace y extraer detalles
        for event_url in event_links:
            try:
                soup_event = fetch_soup_requests(event_url)
                if not soup_event:
                    continue

                # Extraer título (h1 o título principal)
                title_elem = soup_event.find("h1")
                if not title_elem:
                    title_elem = soup_event.find(["h2", "h3"], class_=lambda x: x and ("title" in x.lower() or "titulo" in x.lower()))
                if not title_elem:
                    continue
                title = title_elem.get_text(strip=True)

                if not title or len(title) < 5:
                    continue

                # Extraer fecha con regex y parse_date_advanced()
                texto_pagina = soup_event.get_text(separator=" ", strip=True)
                match_fecha = re.search(r"(\d{1,2}\s+de\s+[a-zA-Z]+\s+de\s+\d{4})", texto_pagina, re.IGNORECASE)

                start_date = None
                if match_fecha:
                    fecha_str = match_fecha.group(1)
                    start_date = parse_date_advanced(fecha_str)

                if not start_date:
                    continue

                # Extraer image_url (meta og:image)
                image_url = None
                og_image = soup_event.find("meta", property="og:image")
                if og_image and og_image.get("content"):
                    image_url = og_image["content"]
                    if not image_url.startswith("http"):
                        image_url = f"https://ime.palma.es{image_url}"

                # Extraer descripción (primer párrafo de texto relevante)
                description = ""
                for p in soup_event.find_all("p"):
                    text = p.get_text(strip=True)
                    if text and len(text) > 30:
                        description = text[:200]
                        break

                # Detectar categoría
                category = detect_category_smart(title, description)

                events.append({
                    "source": "ime_palma",
                    "title": title[:100],
                    "start_date": start_date,
                    "municipality": "Palma",
                    "category": category,
                    "is_free": True,
                    "price": None,
                    "website_url": event_url,
                    "image_url": image_url
                })

            except Exception:
                continue

        print(f"      ✅ ime_palma: {len(events)} eventos")

    except Exception as e:
        print(f"      ❌ Error ime_palma: {e}")

    return events


def scrape_wepartynow() -> List[Dict]:
    events = []
    url = "https://wepartynow.com/es/es/mallorca/esta-noche"
    print("   🔍 Scraping: wepartynow...")
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=random.choice(USER_AGENTS))
            page.goto(url, wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(3000)

            for _ in range(10): 
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                page.wait_for_timeout(1000)

            soup = BeautifulSoup(page.content(), "html.parser")
            browser.close()

            for link in soup.find_all("a", href=True):
                if "/es/es/mallorca/events/" not in link.get("href", ""): continue
                title = link.select_one("h3").get_text(strip=True) if link.select_one("h3") else ""
                if not title: continue
                date_badge = link.select_one(".inline-flex.items-center.rounded-full")
                if not date_badge: continue

                events.append({
                    "source": "wepartynow",
                    "title": title[:100],
                    "start_date": date_badge.get_text(strip=True) + f" {datetime.now().year}",
                    "municipality": "Palma",
                    "location": link.select_one("span.truncate").get_text(strip=True) if link.select_one("span.truncate") else "",
                    "category": detect_category_smart(title, "fiesta discoteca"),
                    "is_free": False,
                    "price": "Consultar web",
                    "website_url": link.get("href", "") if link.get("href", "").startswith("http") else f"https://wepartynow.com{link.get('href', '')}",
                })
        print(f"      ✅ wepartynow: {len(events)} eventos")
    except Exception: pass
    return events

# ============================================================================
# ORQUESTADOR PRINCIPAL, HTML Y SERVER
# ============================================================================

def scrape_all_sources() -> List[Dict]:
    print("🌐 Iniciando extracción masiva de eventos (Multihilo)...")
    historical_ids = load_historical_ids(APPROVED_EVENTS_FILE)
    if historical_ids:
        print(f"   ℹ️  Cargados {len(historical_ids)} eventos históricos para omitir.")
    print("=" * 60)
    
    all_events = []
    
    # ⚠️ SE HAN ELIMINADO 'scrape_firesifestes' y 'scrape_ime_palma' 
    # de esta lista para evitar extracción en vivo y datos duplicados.
    scrapers = [
        scrape_auditorium_palma,
        scrape_mallorcafiestas,
        scrape_mallorcamusicmagazine,
        scrape_conciertos_club,
        scrape_mallorca_com_gastronomia,
        scrape_mallorca_com,
        scrape_wepartynow
    ]

    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(scraper): scraper.__name__ for scraper in scrapers}
        for future in concurrent.futures.as_completed(futures):
            try: all_events.extend(future.result())
            except Exception as e: print(f"❌ Fallo en Worker {futures[future]}: {e}")

    for file_name in ["ime_events_fixed.json", "firesifestes_output.json", "thecalendar_events.json"]:
        file_path = TOOLS_DIR / file_name
        if file_path.exists():
            with open(file_path, "r", encoding="utf-8") as _f:
                cache = json.load(_f)
                all_events.extend(cache)
                print(f"   ✅ Cache recuperada: {len(cache)} eventos de {file_name}")

    all_events = deduplicate_events(all_events, historical_ids)
    
    today = datetime.now().date()
    # ✅ FIX: Try/except para evitar crash con fechas mal formateadas
    future_events = []
    for e in all_events:
        if not e.get("start_date"): continue
        try:
            if datetime.strptime(e["start_date"], "%Y-%m-%d").date() >= today:
                future_events.append(e)
        except ValueError:
            continue  # Skip eventos con fechas mal formateadas

    print("=" * 60)
    print(f"✅ Total eventos procesados y validados: {len(future_events)}")
    return future_events


def generate_review_html(events: List[Dict]):
    print(f"📝 Generando página de revisión interactiva...")
    events.sort(key=lambda x: x.get("start_date", "9999-99-99"))

    html_content = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Web Events Review</title>
    <style>
        body {{ font-family: sans-serif; background: #f5f5f5; padding: 20px; }}
        .header {{ background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }}
        .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }}
        .card {{ background: white; padding: 15px; border-radius: 8px; }}
        .card.approved {{ border: 2px solid green; }}
        .card.rejected {{ border: 2px solid red; opacity: 0.6; }}
        button {{ padding: 10px; margin: 5px; cursor: pointer; }}
    </style>
</head>
<body>
    <div class="header">
        <h1>Eventos Extraídos ({len(events)})</h1>
        <button onclick="downloadApproved()" style="background:blue;color:white;font-weight:bold;">Guardar Aprobados</button>
    </div>
    <div class="grid" id="grid"></div>
    <script>
        const events = {json.dumps(events, ensure_ascii=False)};
        const approved = new Set();
        
        function render() {{
            const grid = document.getElementById('grid');
            grid.innerHTML = '';
            events.forEach(e => {{
                const d = document.createElement('div');
                d.className = 'card ' + (approved.has(e.id) ? 'approved' : '');
                d.innerHTML = `<h3>${{e.title}}</h3><p>📅 ${{e.start_date}} | 📍 ${{e.municipality}}</p><p>🏷 ${{e.category}} | 💰 ${{e.price}}</p>
                <button onclick="toggle('${{e.id}}')">Aprobar / Deshacer</button>`;
                grid.appendChild(d);
            }});
        }}
        
        function toggle(id) {{ approved.has(id) ? approved.delete(id) : approved.add(id); render(); }}
        
        function downloadApproved() {{
            const final = events.filter(e => approved.has(e.id));
            fetch('http://localhost:8765/save', {{ method:'POST', body: JSON.stringify(final) }})
            .then(() => alert('Guardado!'))
            .catch(e => alert('Error de conexión'));
        }}
        render();
    </script>
</body></html>"""
    with open(REVIEW_HTML, "w", encoding="utf-8") as f:
        f.write(html_content)


def run_review_server(output_path):
    saved = _th.Event()
    class H(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/":
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(open(output_path, "rb").read())
            else: self.send_response(404); self.end_headers()
        def do_OPTIONS(self):
            self.send_response(200)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "POST")
            self.end_headers()
        def do_POST(self):
            if self.path == "/save":
                d = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                with open(APPROVED_EVENTS_FILE, "w", encoding="utf-8") as f:
                    json.dump(d, f, ensure_ascii=False, indent=2)
                self.send_response(200)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                saved.set()
    
    srv = HTTPServer(("localhost", 8765), H)
    _th.Thread(target=srv.serve_forever, daemon=True).start()
    print("\n🌐 Servidor: http://localhost:8765 (Presiona Ctrl+C para salir)")
    webbrowser.open("http://localhost:8765")
    try:
        saved.wait()
        srv.shutdown()
        print("✅ Guardado finalizado correctamente.")
    except KeyboardInterrupt:
        srv.shutdown()


if __name__ == "__main__":
    events = scrape_all_sources()
    if events:
        generate_review_html(events)
        if "--auto-approve" in sys.argv or "--auto" in sys.argv:  # ✅ FIX: Aceptar ambos flags
            with open(APPROVED_EVENTS_FILE, "w", encoding="utf-8") as f:
                json.dump(events, f, ensure_ascii=False, indent=2)
            print("🤖 Auto-guardado completado.")
        else:
            run_review_server(REVIEW_HTML)
    else:
        print("⚠️ No hay eventos para procesar.")