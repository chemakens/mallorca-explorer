#!/usr/bin/env python3
"""Scraper para thecalendarmallorca.com"""
import json, re, sys, time, hashlib
from datetime import datetime, date
try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("ERROR: pip3 install playwright && python3 -m playwright install chromium")
    sys.exit(1)

BASE_URL = "https://thecalendarmallorca.com"
OUTPUT_FILE = "/Users/usuario/Code/mallorca-explorer/tools/thecalendar_events.json"

MESES_ES = {
    "enero":"01","febrero":"02","marzo":"03","abril":"04","mayo":"05","junio":"06",
    "julio":"07","agosto":"08","septiembre":"09","octubre":"10","noviembre":"11","diciembre":"12",
}

TEXT_CATEGORY_MAP = {
    "conciertos de musica": "MUSIC", "música y conciertos": "MUSIC", "concierto": "MUSIC",
    "comer / beber": "GASTRONOMY", "comer/beber": "GASTRONOMY", "gastronomía": "GASTRONOMY",
    "mercados locales": "MARKET", "mercado": "MARKET",
    "cosas de niños": "FAMILY", "familia": "FAMILY",
    "deportes": "SPORT", "deporte": "SPORT",
    "tradiciones vivas": "CULTURE", "cultura": "CULTURE",
    "fiestas de la vida nocturna": "NIGHTLIFE", "vida nocturna": "NIGHTLIFE",
    "cultura de las artes": "ART", "arte": "ART",
    "talleres": "WORKSHOP",
}

CATEGORY_URLS = [
    "/es/categoría-de-evento/conciertos-de-musica/",
    "/es/categor%C3%ADa-de-evento/conciertos-de-musica/",
    "/es/categoría-de-evento/comer-beber/",
    "/es/categor%C3%ADa-de-evento/comer-beber/",
    "/es/categoría-de-evento/mercados-locales/",
    "/es/categor%C3%ADa-de-evento/mercados-locales/",
    "/es/categoría-de-evento/cultura-de-las-artes/",
    "/es/categor%C3%ADa-de-evento/cultura-de-las-artes/",
    "/es/categoría-de-evento/deportes/",
    "/es/categor%C3%ADa-de-evento/deportes/",
    "/es/categoría-de-evento/tradiciones-vivas/",
    "/es/categor%C3%ADa-de-evento/tradiciones-vivas/",
    "/es/categoría-de-evento/cosas-de-ninos/",
    "/es/categor%C3%ADa-de-evento/cosas-de-ni%C3%B1os/",
    "/es/categoría-de-evento/fiestas-de-la-vida-nocturna/",
    "/es/categor%C3%ADa-de-evento/fiestas-de-la-vida-nocturna/",
]

MUNICIPALITY_KEYWORDS = {
    "palma": "Palma", "alcúdia": "Alcúdia", "alcudia": "Alcúdia",
    "pollença": "Pollença", "pollenca": "Pollença",
    "sóller": "Sóller", "soller": "Sóller",
    "inca": "Inca", "manacor": "Manacor", "felanitx": "Felanitx",
    "llucmajor": "Llucmajor", "calvià": "Calvià", "calvia": "Calvià",
    "andratx": "Andratx", "valldemossa": "Valldemossa",
    "sineu": "Sineu", "petra": "Petra", "artà": "Artà", "arta": "Artà",
    "capdepera": "Capdepera", "santanyí": "Santanyí", "santanyi": "Santanyí",
    "campos": "Campos", "algaida": "Algaida", "binissalem": "Binissalem",
    "marratxí": "Marratxí", "marratxi": "Marratxí",
    "bunyola": "Bunyola", "alaró": "Alaró", "alaro": "Alaró",
    "deià": "Deià", "deia": "Deià",
    "magaluf": "Calvià", "santa ponça": "Calvià", "paguera": "Calvià",
    "porto cristo": "Manacor", "cala ratjada": "Capdepera",
    "port de pollença": "Pollença", "port d'alcúdia": "Alcúdia",
    "port de sóller": "Sóller", "s'arenal": "Llucmajor",
    "bendinat": "Calvià", "meliá calvià": "Calvià", "melia calvia": "Calvià",
    "son marroig": "Deià", "son sardina": "Palma",
    "el terreno": "Palma", "santa catalina": "Palma", "son espases": "Palma",
    "es molinar": "Palma", "camp de mar": "Andratx",
}

# Títulos genéricos de festivos nacionales/internacionales a descartar
BANNED_KEYWORDS = [
    "día del padre", "día de la madre", "festivo nacional", "asunción",
    "hispanidad", "constitución", "españa", "spain"
]

HOLIDAY_PATTERNS = [
    r"día del padre",
    r"día de la madre",
    r"día de la mujer",
    r"día del trabajador",
    r"día de la hispanidad",
    r"día de reyes",
    r"nochebuena",
    r"nochevieja",
    r"día de todos los santos",
    r"día de la constitución",
    r"día de la inmaculada",
    r"año nuevo",
    r"navidad",
    r"epifanía",
    r"festivo nacional",
    r"asunción",
    r"\(españa\)",
    r"\(spain\)",
    r"día de sant jordi",
    r"día de la comunitat",
    r"día de la comunidad",
    r"día de santiago",
    r"día de la asunción",
]


def is_generic_holiday(title, location=""):
    """Devuelve True si el título parece un festivo nacional genérico sin ubicación en Mallorca."""
    tl = title.lower()

    # Filtro rápido con banned keywords
    for keyword in BANNED_KEYWORDS:
        if keyword in tl:
            return True

    # Filtro con patrones regex
    for pattern in HOLIDAY_PATTERNS:
        if re.search(pattern, tl):
            return True

    return False


def parse_date(text):
    if not text:
        return None
    m = re.search(r"(\d{1,2})\s+de\s+([a-záéíóúñ]+)\s+de\s+(\d{4})", text.lower())
    if m:
        d, mes, y = m.groups()
        mo = MESES_ES.get(mes)
        if mo:
            return f"{y}-{mo}-{d.zfill(2)}"
    m = re.search(r"(\d{4}-\d{2}-\d{2})", text)
    if m:
        return m.group(1)
    return None


def extract_municipality(text):
    """
    Extrae el municipio del texto usando coincidencia de palabras completas.
    Similar a normalize_municipality() de fetch_web_events.py
    """
    if not text:
        return None

    tl = text.lower()

    # Prioridad 1: Buscar en paréntesis (ej: "Evento (Palma)")
    for m in re.finditer(r"\(([^)]+)\)", tl):
        content = m.group(1)
        for kw, muni in MUNICIPALITY_KEYWORDS.items():
            if re.search(rf"\b{re.escape(kw)}\b", content):
                return muni

    # Prioridad 2: Buscar en todo el texto con word boundaries
    for kw, muni in MUNICIPALITY_KEYWORDS.items():
        if re.search(rf"\b{re.escape(kw)}\b", tl):
            return muni

    # Prioridad 3: Coincidencia parcial (fallback)
    for kw, muni in MUNICIPALITY_KEYWORDS.items():
        if kw in tl:
            return muni

    return None  # None = sin municipio conocido


def map_category_from_text(body_text):
    lines = [l.strip().lower() for l in body_text.split("\n")[:25] if l.strip()]
    for line in lines:
        for kw, cat in TEXT_CATEGORY_MAP.items():
            if kw in line:
                return cat
    return "OTHER"


def parse_is_free(text):
    if not text:
        return None
    tl = text.lower()
    if "gratis" in tl or "gratuito" in tl or "entrada libre" in tl:
        return True
    if "€" in text or re.search(r"\d+\s*eur", tl):
        return False
    return None


def extract_price(text):
    if not text:
        return None
    if parse_is_free(text):
        return None
    m = re.search(r"desde\s+(\d+(?:[.,]\d+)?)\s*(EUR|€)", text, re.IGNORECASE)
    if m:
        return f"Desde {m.group(1)}€"
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*€", text)
    if m:
        return f"{m.group(1)}€"
    return None


def collect_event_urls(page, url):
    urls = set()
    try:
        page.goto(url, wait_until="networkidle", timeout=25000)
        page.wait_for_timeout(2000)
        for link in page.query_selector_all("a[href]"):
            href = link.get_attribute("href") or ""
            if (
                "thecalendarmallorca.com" in href
                and re.search(r"/eventos?/[^/]+/?$", href)
                and "?" not in href
                and not re.search(r"/eventos?/?$", href)
            ):
                urls.add(href.rstrip("/"))
    except Exception:
        pass
    return urls


def extract_title(detail, event_url=""):
    """
    Extrae el título principal del evento evitando capturar items de agenda
    (ej: "18:00-20:00 ANIMACIÓN INFANTIL") o títulos genéricos del sitio.

    Estrategia por prioridad:
    1. Contenedor principal del evento (h3 a, .tribe-events-calendar-month__calendar-event-title-link)
    2. Elementor heading widget que no sea schedule item ni genérico
    3. Primer h1 válido no genérico
    4. <title> del documento limpiado
    5. URL slug como último recurso
    """
    TIME_PATTERN = re.compile(r"^\d{1,2}:\d{2}")
    SCHEDULE_PATTERN = re.compile(r"^\d{1,2}:\d{2}[-\s]+\d{1,2}:\d{2}")
    SKIP_TITLES = {"Eventos", "The Calendar Mallorca", "Events", "Calendario", "Calendar", "Actividad infantil"}
    GENERIC_TITLES = {
        "Arte y Cultura", "Arte y cultura", "Cultura", "Eventos",
        "Vivir mejor", "Vivir Mejor", "Campamentos y Clínicas",
        "Campamentos Y Clínicas", "campamentos y clínicas"
    }

    # 1. Selector estricto del título principal (contenedor de evento)
    main_title_selectors = [
        "h3.tribe-events-calendar-month__calendar-event-title a",
        ".tribe-events-calendar-month__calendar-event-title-link",
        "h2.tribe-events-list-event-title a",
        ".tribe-events-event-title a",
    ]
    for selector in main_title_selectors:
        el = detail.query_selector(selector)
        if el:
            t = el.inner_text().strip()
            if t and len(t) > 5 and t not in SKIP_TITLES and t not in GENERIC_TITLES and not TIME_PATTERN.match(t) and not SCHEDULE_PATTERN.match(t):
                return t

    # 2. Elementor heading widget (más semántico para JetEngine+Elementor)
    for el in detail.query_selector_all(".elementor-heading-title"):
        t = el.inner_text().strip()
        if t and len(t) > 5 and t not in SKIP_TITLES and t not in GENERIC_TITLES and not TIME_PATTERN.match(t) and not SCHEDULE_PATTERN.match(t):
            return t

    # 3. h1 (primer heading real)
    for el in detail.query_selector_all("h1"):
        t = el.inner_text().strip()
        if t and len(t) > 5 and t not in SKIP_TITLES and t not in GENERIC_TITLES and not TIME_PATTERN.match(t) and not SCHEDULE_PATTERN.match(t):
            return t

    # 4. Page <title> limpiado
    page_title = detail.title()
    if page_title:
        cleaned = re.sub(r"\s*[-–|].*?(The Calendar|Mallorca).*", "", page_title, flags=re.IGNORECASE).strip()
        if cleaned and len(cleaned) > 5 and cleaned not in GENERIC_TITLES:
            return cleaned

    # 5. Último recurso: extraer de la URL slug
    if event_url:
        slug = event_url.rstrip("/").split("/")[-1]
        # Decodificar URL encoding y limpiar
        from urllib.parse import unquote
        title_from_slug = unquote(slug).replace("-", " ").replace("_", " ")
        # Capitalizar cada palabra
        title_from_slug = " ".join(word.capitalize() for word in title_from_slug.split())
        if title_from_slug and len(title_from_slug) > 5:
            return title_from_slug

    return ""


def scrape_thecalendar():
    today = date.today()
    events_by_id = {}

    print("🔍 Scraping thecalendarmallorca.com...", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
            locale="es-ES",
        )
        page = context.new_page()

        print("   📅 Recolectando URLs de eventos...", flush=True)
        event_urls = set()

        for url in [BASE_URL + "/es/events/", BASE_URL + "/es/eventos/", BASE_URL + "/es/"]:
            event_urls |= collect_event_urls(page, url)

        print(f"   📁 Explorando categorías...", flush=True)
        for cat_path in CATEGORY_URLS:
            new = collect_event_urls(page, BASE_URL + cat_path)
            if new:
                event_urls |= new

        print(f"   🔗 {len(event_urls)} URLs únicas encontradas", flush=True)

        skipped_holiday = 0
        skipped_no_date = 0
        skipped_past = 0
        skipped_no_title = 0
        skipped_no_location = 0

        for i, event_url in enumerate(sorted(event_urls)):
            print(f"   [{i+1}/{len(event_urls)}] {event_url.split('/')[-1][:55]}", flush=True, end="\r")
            try:
                detail = context.new_page()
                detail.goto(event_url, wait_until="domcontentloaded", timeout=15000)
                detail.wait_for_timeout(800)

                body_text = detail.inner_text("body") or ""

                # Título: SIEMPRE usar URL slug (el sitio usa títulos genéricos variables en HTML)
                from urllib.parse import unquote
                url_slug = unquote(event_url.rstrip("/").split("/")[-1])
                title = " ".join(word.capitalize() for word in url_slug.replace("-", " ").replace("_", " ").split())

                # Validar que el título extraído sea válido
                if not title or len(title) < 5:
                    title = extract_title(detail, event_url)
                if not title:
                    skipped_no_title += 1
                    detail.close()
                    continue

                # Filtrar festivos genéricos nacionales/internacionales
                # SIEMPRE verificar título + URL slug (el sitio usa títulos genéricos variables)
                from urllib.parse import unquote
                url_slug = unquote(event_url.rstrip("/").split("/")[-1])
                combined_text_for_holiday = title + " " + url_slug

                if is_generic_holiday(combined_text_for_holiday):
                    skipped_holiday += 1
                    detail.close()
                    continue

                # Fecha
                start_date = parse_date(body_text)
                if not start_date:
                    skipped_no_date += 1
                    detail.close()
                    continue

                try:
                    if datetime.strptime(start_date, "%Y-%m-%d").date() < today:
                        skipped_past += 1
                        detail.close()
                        continue
                except:
                    detail.close()
                    continue

                # Location / Venue - extracción mejorada con selectores específicos
                location = ""

                # Prioridad 1: Selectores específicos de The Events Calendar
                venue_selectors = [
                    ".tribe-events-calendar-month__calendar-event-venue",
                    ".tribe-events-venue-details",
                    ".tribe-venue",
                    ".tribe-events-venue",
                    ".tribe-events-event-meta .tribe-venue",
                ]
                for selector in venue_selectors:
                    venue_el = detail.query_selector(selector)
                    if venue_el:
                        location = venue_el.inner_text().strip()
                        if location:
                            break

                # Prioridad 2: Buscar en body text "Location:"
                if not location:
                    m = re.search(r"Location:\s*([^\n]+)", body_text)
                    if m:
                        location = re.sub(r"\s*Ver en Google Maps.*", "", m.group(1)).strip()

                # Prioridad 3: Selectores genéricos de ubicación
                if not location:
                    generic_venue_selectors = [
                        ".event-venue",
                        ".venue-name",
                        "[class*='venue']",
                        "[class*='location']",
                    ]
                    for selector in generic_venue_selectors:
                        venue_el = detail.query_selector(selector)
                        if venue_el:
                            loc_text = venue_el.inner_text().strip()
                            if loc_text and len(loc_text) < 100:  # Evitar textos muy largos
                                location = loc_text
                                break

                # Municipio - usando texto combinado (similar a normalize_municipality())
                combined_text = title + " " + location + " " + body_text[:500]
                municipality = extract_municipality(combined_text)

                if municipality is None:
                    # Evento sin municipio identificable: podría ser holiday genérico
                    # Descartar si el título tiene indicios de festivo genérico
                    # pero si tiene location text, asumir Mallorca genérico
                    if not location.strip():
                        skipped_no_location += 1
                        detail.close()
                        continue
                    municipality = "Mallorca"

                # Categoría - lógica mejorada usando keywords sistemáticos
                CATEGORY_KEYWORDS = {
                    "CONCERT": [
                        "concierto", "concert", "música", "music", "recital", "jazz",
                        "rock", "pop", "flamenco", "folk", "orquestra", "sinfónica", "banda", "tributo"
                    ],
                    "FESTIVAL": [
                        "festival", "feria", "fiesta", "festa", "festes de", "fiestas de",
                        "celebración", "verbena", "revetla"
                    ],
                    "SPORT": [
                        "deporte", "sport", "triatlón", "maratón", "ciclismo", "running",
                        "vela", "regata", "pádel", "padel", "tenis", "fútbol", "futbol",
                        "natación", "senderismo", "paseo"
                    ],
                    "CULTURE": [
                        "exposición", "exhibition", "teatro", "theatre", "danza", "dance",
                        "conferencia", "obra", "diada", "tradicion", "solsticio", "poesía",
                        "arte"
                    ],
                    "GASTRONOMY": [
                        "gastronómico", "gastronomic", "cata", "tasting", "culinaria",
                        "vino", "tapas", "barbacoa", "marisco", "cena", "gastronomí"
                    ],
                    "MARKET": ["mercado", "market", "mercat", "artesanía", "fira"],
                    "NIGHTLIFE": [
                        "fiesta", "party", "noche", "night", "discoteca", "club",
                        "nocturna", "nocturno", "tardeo"
                    ],
                    "FAMILY": [
                        "familia", "family", "niños", "kids", "children", "infantil", "familiar"
                    ],
                }

                # Detectar categoría usando keywords (similar a detect_category())
                combined_text = (title + " " + location + " " + body_text[:200]).lower()
                category = None
                for cat, keywords in CATEGORY_KEYWORDS.items():
                    if any(keyword in combined_text for keyword in keywords):
                        category = cat
                        break

                # Fallback a categoría del sitio
                if not category:
                    category = map_category_from_text(body_text)

                is_free = parse_is_free(body_text)
                price = extract_price(body_text)

                # Imagen
                image_url = ""
                img_el = detail.query_selector(".elementor-widget-image img, article img, .wp-post-image")
                if img_el:
                    src = img_el.get_attribute("src") or img_el.get_attribute("data-src") or ""
                    if src and not src.startswith("data:"):
                        image_url = src

                event_id = "tcm-" + hashlib.md5(event_url.encode()).hexdigest()[:12]
                events_by_id[event_id] = {
                    "source": "thecalendar", "id": event_id, "title": title,
                    "description": "", "start_date": start_date, "end_date": start_date,
                    "municipality": municipality, "location": location or municipality,
                    "category": category, "is_free": is_free, "price": price,
                    "website_url": event_url, "image_url": image_url,
                }
                detail.close()
                time.sleep(0.3)

            except Exception:
                try:
                    detail.close()
                except:
                    pass

        browser.close()

    events = sorted(events_by_id.values(), key=lambda e: e["start_date"])
    print(f"\n   ✅ Total: {len(events)} eventos válidos", flush=True)
    print(f"   ⏩ Descartados: {skipped_past} pasados, {skipped_no_date} sin fecha, "
          f"{skipped_holiday} festivos genéricos, {skipped_no_title} sin título, "
          f"{skipped_no_location} sin ubicación", flush=True)
    return events


if __name__ == "__main__":
    events = scrape_thecalendar()
    if not events:
        print("\n⚠️  No se encontraron eventos.")
        sys.exit(1)

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(events, f, ensure_ascii=False, indent=2)

    print(f"💾 Guardado en {OUTPUT_FILE}")
    print(f"📊 {len(events)} eventos\n")
    print("📋 Primeros 10 eventos:")
    for ev in events[:10]:
        free = "gratis" if ev["is_free"] else (ev["price"] or "?")
        print(f"   • {ev['title'][:45]:45} | {ev['start_date']} | {ev['municipality']:12} | {ev['category']:12} | {free}")
