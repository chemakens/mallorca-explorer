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
    # 1. CULTURE primero para que charlas/conferencias no caigan en SPORT
    "CULTURE": [
        "charla", "conferència", "conferencia", "coloquio", "col·loqui", "lectura", "llibre", "libro",
        "poesía", "poesia", "literatura", "exposición", "exposició", "exhibition",
        "teatro", "teatre", "theatre", "danza", "dansa", "dance", "museo", "museu",
        "arte", "cine", "película", "documental", "visita guiada", "patrimonio",
    ],
    "FAMILY": [
        "infantil", "familiar", "familia", "niños", "nins", "kids", "children",
        "cuentacuentos", "contacontes", "marionetas", "titelles", "títeres",
        "espectacle familiar",
    ],
    "FESTIVAL": [
        "festival", "feria", "fira", "fiesta", "festes", "festa", "celebración",
        "verbena", "revetla", "gegants", "gigantes", "moros i cristians",
        "sant antoni", "sant sebastià", "fogueró", "reyes magos", "cavalcada",
        "carnaval", "rua", "rueta",
    ],
    "CONCERT": [
        "concierto", "concert", "música", "musica", "recital", "jazz", "rock", "pop",
        "orquestra", "sinfónica", "banda de música", "coral", "coro", "acústico",
        "tributo", "tribute", "cantante", "guitarra",
    ],
    "SPORT": [
        "triatlón", "triatlo", "maratón", "marato", "ciclismo", "running", "trail",
        "cursa", "carrera popular", "carrera deportiva", "torneo", "campeonato",
        "pádel", "padel", "natación", "natacio", "vela", "regata", "voleibol",
        "taekwondo", "halterofilia", "fitness", "yoga", "ioga", "pilates", "caminata", "senderismo", "excursió", "excursion",
    ],
    "NIGHTLIFE": [
        "party", "club", "tardeo", "megatardeo", "dj set", "techno", "house", "reggaeton",
        "discoteca", "closing party", "opening party",
    ],
    "GASTRONOMY": [
        "gastronómico", "gastronomia", "cata", "tasting", "degustación", "degustacio",
        "tapas", "tast", "vino", "maridaje", "bodega", "celler", "restaurante", "brunch",
        "barbacoa", "bbq", "cuina", "cocina", "llampuga",
    ],
    "MARKET": [
        "mercadillo", "mercat de", "mercado de", "rastro", "fira del disc", "market",
    ],
}

# Palabras que indican evento fuera de Mallorca
FOREIGN_ISLAND_PATTERNS = ["ibiza", "eivissa", "menorca", "formentera", "ciutadella"]

# Títulos que siempre van a CULTURE (prioridad máxima, antes que SPORT)
CULTURE_PRIORITY_PATTERNS = [
    r"charla", r"charles", r"conferencia", r"lectura",
    r"club de lectura", r"coloquio", r"col·loqui",
    r"llibre", r"libro", r"presentació", r"presentación",
]

# Títulos de sub-eventos genéricos de programas festivos a descartar
SUBEVENT_BLACKLIST = {
    "pasacalles", "animación infantil", "refresco popular", "juegos populares",
    "fin de fiesta con castillo de fuegos artificiales", "animacion infantil",
    "muestra de baile", "ball de bot", "actuació musical",
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
    t_lower = title.lower()
    combined = f"{title} {description}".lower()

    # 0. Reglas especiales prioritarias (antes de todo)

    # 0a. Clubs/discotecas → NIGHTLIFE (antes de venues y artistas)
    club_keywords = ["masia club", "club:", "discoteca", "nightclub"]
    if any(kw in t_lower for kw in club_keywords):
        return "NIGHTLIFE"

    # 0b. Venue rule: Es Gremi → CONCERT salvo que sea puro nightlife
    if "es gremi" in t_lower:
        if not any(w in t_lower for w in ["nightlife", "amok", "rewind", "callejeo", "trip", "crush", "parao"]):
            return "CONCERT"

    # 0c. Lista blanca de artistas → CONCERT
    concert_artists = [
        "davide ranaldi", "homenaje a robe", "roo panes", "abba the new experience",
        "i love u2", "tribut a u2", "this is michael", "y sin embargo", "tribut a sabina",
        "niña pastori", "sofia ellar", "david navarro", "despistaos", "albert pla",
        "lamine thior", "gloosito"
    ]
    if any(artist in t_lower for artist in concert_artists):
        return "CONCERT"

    # 0d. Musicales infantiles → FAMILY
    if any(w in t_lower for w in ["la familia addams", "rapunzel el musical"]):
        return "FAMILY"

    # 0e. Corre Vins → SPORT (carrera popular de vinos)
    if "corre vins" in t_lower:
        return "SPORT"

    # 1. Reglas directas prioritarias (título)

    # 1a. Deportes específicos (MÁXIMA PRIORIDAD)
    if any(w in t_lower for w in ["corredores", "carreras", "run club", "atletisme", "cursa", "triatlón", "triatlo",
                                   "maratón", "marató", "torneo", "trofeu", "trofeo", "trail running"]):
        return "SPORT"

    # 1b. Nightlife y tardeos (AMPLIADO)
    if any(w in t_lower for w in ["tardeo", "megatardeo"]):
        return "NIGHTLIFE"

    # 1c. Actividades infantiles/tecnológicas (ANTES que actividades culturales - AMPLIADO)
    if any(w in t_lower for w in ["lego", "robòtica", "robotica", "espai de joc", "joc infantil", "titelles",
                                   "infantil", "per a nadons", "per a infants", "títeres"]):
        return "FAMILY"

    # 1d. Markets y mercadillos (PRIORIDAD MÁXIMA - CORREGIDO)
    if "rata market" in t_lower:
        return "MARKET"
    if any(w in t_lower for w in ["mercadillo", "mercat de", "mercado de"]) and "fira" not in t_lower:
        return "MARKET"

    # 1e. Musicales (ANTES que conciertos - AMPLIADO)
    if any(w in t_lower for w in ["musical", "teatre musical", "el musical"]):
        # Distinguir entre musical (cultura/familia) y concierto
        if "infantil" in t_lower or "familia" in t_lower:
            return "FAMILY"
        return "CULTURE"

    # 1f. Actividades culturales específicas (MÁXIMA PRIORIDAD - antes de diccionarios)
    # Presentaciones de libros (patrones completos)
    if any(pattern in t_lower for pattern in [
        "presentació del llibre", "presentacio de llibre", "presentación del libro",
        "presentació llibre", "presentacion libro", "presentación de libro"
    ]):
        return "CULTURE"

    # Rutas y paseos: distinguir cultural vs naturaleza/deporte
    if any(w in t_lower for w in ["ruta", "passeig", "ruta guiada", "passeig guiat"]):
        cultural_route_kws = [
            "històriques", "modernista", "art", "arquitectura", "dones",
            "patrimoni", "literària", "literaria", "gòtic", "gothic",
            "cultural", "guiada", "guiat", "historic", "història"
        ]
        sport_route_kws = [
            "senderisme", "senderismo", "muntanya", "montaña", "pedra en sec",
            "trail", "trekking", "excursió de muntanya", "cims"
        ]
        if any(kw in t_lower for kw in cultural_route_kws):
            return "CULTURE"
        elif any(kw in t_lower for kw in sport_route_kws):
            return "SPORT"

    # Visitas culturales
    if any(pattern in t_lower for pattern in ["visita guiada", "visita cultural"]):
        return "CULTURE"

    # Cursos y talleres culturales específicos (evitar cursos deportivos)
    if any(pattern in t_lower for pattern in [
        "curs d'iniciació a la fotografia", "curs de fotografia", "taller de fotografia",
        "curs d'iniciació", "taller sensorial", "manualitats", "workshop"
    ]):
        # Solo si NO es deportivo
        if not any(w in t_lower for w in ["vela", "natació", "natacion", "cursa", "marató", "maratón", "trail", "running", "atletisme"]):
            return "CULTURE"

    # Fotografía como actividad cultural (evitar cursos deportivos)
    if any(pattern in t_lower for pattern in ["fotografia", "fotografía"]):
        # Solo CULTURE si NO es deporte
        if not any(w in t_lower for w in ["vela", "natació", "natacion", "cursa", "marató", "maratón", "trail", "running", "atletisme"]):
            return "CULTURE"

    # 1g. Conciertos y eventos musicales (prioridad alta - antes de CULTURE genérico)
    if any(pattern in t_lower for pattern in [
        "jazz", "concert", "concierto", "música", "musica",
        "orquestra", "sinfònica", "sinfonica", "recital"
    ]):
        return "CONCERT"

    # 1h. Artistas musicales y de jazz conocidos (AMPLIADO)
    if any(artist in t_lower for artist in [
        "ainhoa arteta", "joan alcover", "antonio orozco", "cécile mclorin salvant",
        "salvant", "chucho valdés", "valdés", "toquinho", "chambao",
        "silvia pérez cruz", "buika", "miguel poveda", "estrella morente"
    ]):
        return "CONCERT"

    # 1j. Charlas y conferencias
    if any(w in t_lower for w in ["charla", "conferencia", "conferència", "col·loqui", "lectura"]):
        return "CULTURE"

    # 1k. Festivales tradicionales (AMPLIADO para tardeos)
    if any(w in t_lower for w in ["gegants", "gigantes", "sant antoni", "moros i cristians", "revetla", "fogueró"]):
        return "FESTIVAL"

    # 1l. Bandas/artistas específicos
    if "tierra santa" in t_lower:
        return "CONCERT"
    if "candlelight" in t_lower:
        return "CONCERT"

    # 2. Evaluación ordenada por diccionario
    for category, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if re.search(rf"\b{re.escape(kw)}\b", combined):
                return category

    return "CULTURE"


def parse_date_advanced(date_str: str) -> Optional[str]:
    """Convierte fechas relativas y formatos de texto a AAAA-MM-DD estricto."""
    if not date_str: return None

    date_str = date_str.lower().strip()
    date_str = re.sub(r",?\s*\d{1,2}:\d{2}\s*h?\.?", "", date_str, flags=re.IGNORECASE)
    date_str = re.sub(r"a las \d{1,2}:\d{2}h?\.", "", date_str, flags=re.IGNORECASE).strip()

    match_rango = re.search(r"del\s+(\d{1,2})\s+al\s+\d{1,2}\s+(?:de\s+)?([a-zç]+)(?:\s+de\s+)?(\d{4})?", date_str)
    if match_rango:
        d, m, y = match_rango.groups()
        y = y if y else str(datetime.now().year)
        date_str = f"{d} {m} {y}"
    elif "al" in date_str:
        date_str = re.sub(r"^del\s+\d{1,2}\s+al\s+", "", date_str, flags=re.IGNORECASE).strip()

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

    match_no_year = re.search(r"(\d{1,2})[/\-\s\.]+(\d{1,2})[/\-\s\.]*$", date_normalized)
    if match_no_year:
        day, month = match_no_year.groups()
        try:
            candidate = datetime(today.year, int(month), int(day))
            if candidate.date() < today.date():
                candidate = datetime(today.year + 1, int(month), int(day))
            return candidate.strftime("%Y-%m-%d")
        except ValueError:
            pass

    return None

# Municipios de Mallorca que contienen referencias a otras islas (falsos positivos)
FOREIGN_ISLAND_PATTERNS = ["ibiza", "eivissa", "menorca", "ciutadella", "maó", "mahón", "formentera"]

def normalize_municipality(text: str, title: str = "") -> Optional[str]:
    combined = f"{text} {title}".strip()
    if not combined:
        return None

    # Excluir otras islas
    if re.search(r"\b(ibiza|eivissa|menorca|formentera)\b", combined, re.IGNORECASE):
        return "FUERA_DE_MALLORCA"

    combined_lower = combined.lower()

    # Buscar municipio con preposiciones o como palabra suelta
    for muni in MALLORCA_MUNICIPALITIES:
        pattern = rf"\b(?:a|en|de|d')\s+{re.escape(muni)}\b|\b{re.escape(muni)}\b"
        if re.search(pattern, combined_lower):
            if muni == "palma":
                return "Palma"
            return muni.title()

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
    import unicodedata
    t = t.lower().strip()
    # Normalizar palabras pegadas: insertar espacio entre minúscula-Mayúscula
    t = re.sub(r"([a-zàèìòùáéíóú])([A-ZÀÈÌÒÙÁÉÍÓÚ])", r"\1 \2", t)
    # Eliminar salas y ciudades añadidas al título
    t = re.sub(r"\s*(?:en|at)\s+(?:es gremi|trui teatre|auditorium|la movida|pueblo español|sala la fornal|intergalactic bar)[^$]*$", "", t)
    t = re.sub(r"\s*\((?:palma|mallorca|calvià|esporles|ibiza|inca|manacor)\)", "", t)
    t = re.sub(r"\s*[-–|·]\s*mallorca.*$", "", t)
    t = re.sub(r"\s+en\s+mallorca.*$", "", t)
    # Eliminar barrios o subtítulos con preposición
    t = re.sub(r"\s+en\s+(?:pere garau|santa catalina|el molinar|la lonja|son espanyol|el terreno).*$", "", t)
    # Eliminar prefijos de pases y marcas
    t = re.sub(r"\b(?:1[ºª]?|2[ºª]?|primer|segundo)\s+pase\b", "", t)
    t = re.sub(r"candlelight[:\s]*", "", t, flags=re.IGNORECASE)
    # Eliminar coletillas de edición, volúmenes y aniversarios
    t = re.sub(r"\bvol\.?\s*\d+\b", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\bedici[oó]n\b", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\b\d+[ºª]?\s*aniversario\b", "", t, flags=re.IGNORECASE)
    # Limpiar puntuación
    t = re.sub(r"[^\w\s]", "", t)
    return "".join(c for c in unicodedata.normalize("NFD", t) if unicodedata.category(c) != "Mn").strip()


def load_historical_ids(filepath: Path) -> Set[str]:
    if not filepath.exists(): return set()
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            return {e.get("id") for e in json.load(f) if "id" in e}
    except Exception:
        return set()

def deduplicate_events(events: List[Dict], historical_ids: Set[str]) -> List[Dict]:
    from difflib import SequenceMatcher

    seen_exact, seen_fuzzy = set(), set()
    unique_events = []
    TITLE_BLACKLIST = [
        "secrets night", "secrets mallorca", "pase diario", "day pass",
        "museos y lugares culturales favoritos", "pase de un día en",
        "academia de pilates", "academia de", "clases de pilates",
        # Traducciones rotas y errores evidentes
        "domingo/sunday", "amanecer partea", "el ritual final nu mallorca",
        # Falsos eventos (frases completas específicas)
        "clubes de corredores carreras sociales",
        "verbenes de mallorcas 2026",
    ]

    for event in events:
        title_low = event.get("title", "").lower().strip()
        muni = event.get("municipality", "") or ""
        # Descartar eventos de otras islas
        if muni == "FUERA_DE_MALLORCA": continue
        # Descartar sub-eventos genéricos sin municipio preciso
        if muni in ("", "Mallorca") and title_low in SUBEVENT_BLACKLIST: continue
        if any(bl in title_low for bl in TITLE_BLACKLIST): continue

        parsed_date = parse_date_advanced(event.get("start_date", ""))
        if not parsed_date: continue
        event["start_date"] = parsed_date

        event_id = generate_event_id(event["source"], event["title"], event["start_date"])
        if event_id in historical_ids: continue
        event["id"] = event_id

        title_raw = event["title"].lower().strip()
        exact_key = (title_raw, event["start_date"])
        if exact_key in seen_exact: continue

        fuzzy_key = (_normalize_title(event["title"])[:35], event["start_date"])  # ✅ FIX: Aumentado de 40 a 60 caracteres
        if fuzzy_key in seen_fuzzy: continue

        # Deduplicación difusa CONSERVADORA: solo duplicados confirmados
        normalized_title = _normalize_title(event["title"])
        is_similar_duplicate = False
        for existing_event in unique_events:
            # Solo comparar eventos en misma fecha Y mismo municipio/sala
            if existing_event["start_date"] == event["start_date"]:
                # Verificar si son misma ubicación (conservador: permitir si ubicaciones diferentes)
                same_location = (event.get("municipality") == existing_event.get("municipality"))

                existing_normalized = _normalize_title(existing_event["title"])

                # Solo descartar si es substring Y misma ubicación
                if same_location and (normalized_title in existing_normalized or existing_normalized in normalized_title):
                    # Verificar que la diferencia de longitud no sea significativa
                    len_diff = abs(len(normalized_title) - len(existing_normalized))
                    if len_diff < 5:  # Solo si son prácticamente idénticos
                        is_similar_duplicate = True
                        break

                # Similitud muy alta (0.92 para ser conservador)
                if same_location:
                    similarity = SequenceMatcher(None, normalized_title, existing_normalized).ratio()
                    if similarity > 0.92:
                        is_similar_duplicate = True
                        break

        if is_similar_duplicate:
            continue

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

    # URLs con paginación: calendario + eventos-mallorca (5 págs) + ferias-y-fiestas (5 págs)
    urls_to_scrape = [
        f"{base_url}/es/calendario/",
        f"{base_url}/es/eventos-mallorca/",
        f"{base_url}/es/eventos-mallorca/page/2/",
        f"{base_url}/es/eventos-mallorca/page/3/",
        f"{base_url}/es/eventos-mallorca/page/4/",
        f"{base_url}/es/eventos-mallorca/page/5/",
        f"{base_url}/es/evento-tipo/ferias-y-fiestas/",
        f"{base_url}/es/evento-tipo/ferias-y-fiestas/page/2/",
        f"{base_url}/es/evento-tipo/ferias-y-fiestas/page/3/",
        f"{base_url}/es/evento-tipo/ferias-y-fiestas/page/4/",
        f"{base_url}/es/evento-tipo/ferias-y-fiestas/page/5/",
    ]

    seen_urls = set()

    try:
        for page_url in urls_to_scrape:
            try:
                # Intentar cargar la página y manejar 404 elegantemente
                try:
                    response = requests.get(page_url, headers=get_random_headers(), timeout=15)
                    if response.status_code == 404:
                        continue  # Página no existe, continuar con la siguiente
                    response.raise_for_status()
                    soup = BeautifulSoup(response.content, "html.parser")
                except requests.exceptions.HTTPError:
                    continue  # Error HTTP, continuar con la siguiente

                if not soup:
                    continue

                # Buscar artículos que NO sean eventos pasados
                articles = soup.select("article.mec-event-article")
                future_articles = [a for a in articles if 'mec-past-event' not in a.get('class', [])]

                for card in future_articles:
                    try:
                        # Extraer título
                        title_elem = card.find(["h2", "h3", "h4"])
                        if not title_elem:
                            continue

                        link_elem = title_elem.find("a", href=True)
                        if not link_elem:
                            continue

                        title = link_elem.get_text(strip=True)
                        event_url = link_elem.get("href", "")

                        if not title or len(title) < 3:
                            continue

                        # Filtrar mercados y mercadillos semanales recurrentes
                        title_lower = title.lower()
                        if any(term in title_lower for term in [
                            "mercado semanal", "mercat semanal", "mercat setmanal",
                            "mercado tradicional", "mercat tradicional",
                            "mercat de segona mà", "rastro",
                            "cada lunes", "cada martes", "cada miércoles", "cada jueves",
                            "cada viernes", "cada sábado", "cada domingo",
                            "tots els dilluns", "tots els dimarts", "tots els dimecres",
                            "tots els dijous", "tots els divendres", "tots els dissabtes",
                            "mercado dominical", "mercadillo dominical",
                            "mercat dels dijous", "mercat dels divendres", "mercat dels dissabtes",
                            "hay mercado", "hi ha mercat",
                        ]):
                            continue

                        # Evitar duplicados
                        if event_url in seen_urls:
                            continue
                        seen_urls.add(event_url)

                        # Extraer lugar
                        place_elem = card.find("div", class_="mec-event-loc-place")
                        place_text = place_elem.get_text(strip=True) if place_elem else ""

                        # Normalizar municipio
                        municipality = normalize_municipality(title + " " + place_text)
                        if municipality == "FUERA_DE_MALLORCA":
                            continue
                        if not municipality:
                            municipality = "Mallorca"

                        # Cargar página del evento para obtener fecha del JSON-LD
                        event_soup = fetch_soup_requests(event_url, timeout=10)
                        if not event_soup:
                            continue

                        # Buscar JSON-LD con datos del evento
                        start_date = None
                        scripts = event_soup.find_all("script", type="application/ld+json")

                        for script in scripts:
                            try:
                                import json
                                data = json.loads(script.string)

                                # Manejar tanto objetos simples como @graph
                                items = data.get("@graph", [data]) if isinstance(data, dict) else [data]

                                for item in items:
                                    if isinstance(item, dict) and item.get("@type") == "Event":
                                        start_date = item.get("startDate", "")
                                        if start_date:
                                            # startDate puede ser "2026-03-01" o "2026-03-01T19:00:00"
                                            start_date = start_date.split("T")[0]
                                            break

                                if start_date:
                                    break

                            except:
                                continue

                        if not start_date:
                            continue

                        # Validar fecha y descartar eventos pasados
                        try:
                            event_date = datetime.strptime(start_date, "%Y-%m-%d")
                            if event_date.date() < today.date():
                                continue
                        except:
                            continue

                        # Extraer todo el texto para análisis
                        card_text = card.get_text(separator=" ", strip=True)

                        # Detectar categoría
                        category = detect_category_smart(title, card_text)

                        # Extraer precio
                        is_free, price = extract_price(card_text)

                        events.append({
                            "source": "firesifestes",
                            "title": title[:100],
                            "start_date": start_date,
                            "municipality": municipality,
                            "category": category,
                            "is_free": is_free,
                            "price": price,
                            "website_url": event_url,
                        })

                    except Exception:
                        continue

            except Exception:
                continue

        print(f"      ✅ firesifestes: {len(events)} eventos")
    except Exception:
        pass

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
                    if "mercado" in title.lower() or "mercat" in title.lower(): continue

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
                            "category": "CONCERT",  # Base category for conciertos.club
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

                # Filtrar mercados semanales recurrentes (ruido de UX)
                if "mercado semanal" in title.lower():
                    return None

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
                        "category": "GASTRONOMY",
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
                match_fecha = re.search(r"(\d{1,2}\s+de\s+[a-zA-Z]+\s+de\s+\d{4}|\d{1,2}[/\-]\d{1,2}[/\-]\d{4})", texto_pagina, re.IGNORECASE)

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
                    "category": "NIGHTLIFE",  # Base category for wepartynow
                    "is_free": False,
                    "price": "Consultar web",
                    "website_url": link.get("href", "") if link.get("href", "").startswith("http") else f"https://wepartynow.com{link.get('href', '')}",
                })
        print(f"      ✅ wepartynow: {len(events)} eventos")
    except Exception: pass
    return events


def scrape_ticketib() -> List[Dict]:
    """
    Scraper para TicketIB - Plataforma de eventos de Baleares
    URL base: https://ticketib.com/es/events
    Scraping: requests + BeautifulSoup (HTML estático)
    Paginación: ?page=N hasta que no devuelva tarjetas
    """
    events = []
    base_url = "https://ticketib.com/es/events"
    print("   🔍 Scraping: ticketib...")

    try:
        page_num = 1
        max_pages = 10  # Límite de seguridad

        while page_num <= max_pages:
            url = f"{base_url}?page={page_num}" if page_num > 1 else base_url

            soup = fetch_soup_requests(url, timeout=15)
            if not soup:
                print(f"      ⚠️ No se pudo cargar página {page_num}, deteniendo paginación")
                break

            # Buscar tarjetas de eventos
            event_cards = soup.select('a.event-card')

            if not event_cards:
                print(f"      ℹ️ No hay más eventos en página {page_num}, finalizando")
                break

            print(f"      ▶ Procesando página {page_num}: {len(event_cards)} eventos...")

            for card in event_cards:
                try:
                    # Extraer título
                    title_elem = card.select_one('.event-title')
                    if not title_elem:
                        continue
                    title = title_elem.get_text(strip=True)

                    # Extraer fecha
                    date_elem = card.select_one('.date')
                    if not date_elem:
                        continue
                    date_text = date_elem.get_text(strip=True).lower()

                    # Filtrar eventos con fechas recurrentes/indefinidas
                    if any(skip_word in date_text for skip_word in ["diverses", "varias"]):
                        continue

                    # Normalizar la fecha
                    start_date = parse_date_advanced(date_text)
                    if not start_date:
                        continue

                    # Extraer ubicación (venue format: "Local, Municipio")
                    municipality = "Mallorca"  # Fallback
                    venue_elem = card.select_one('[class*="venue"]')
                    if venue_elem:
                        venue_text = venue_elem.get_text(strip=True)
                        # Intentar extraer el municipio (después de la coma)
                        if ',' in venue_text:
                            parts = venue_text.split(',')
                            if len(parts) >= 2:
                                municipality_raw = parts[-1].strip()
                                municipality = normalize_municipality(municipality_raw) or municipality
                        else:
                            municipality = normalize_municipality(venue_text) or municipality

                    # Extraer enlace del evento
                    event_href = card.get('href', '')
                    if not event_href:
                        continue
                    website_url = event_href if event_href.startswith('http') else f"https://ticketib.com{event_href}"

                    # Extraer imagen (picture > source[srcset])
                    image_url = None
                    img_source = card.select_one('picture source[srcset]')
                    if img_source and img_source.get('srcset'):
                        srcset = img_source['srcset']
                        # El srcset puede tener formato "url 1x, url 2x" - tomar el primero
                        image_url = srcset.split()[0] if srcset else None
                        # Normalizar URL (CDN de TicketIB usa protocol-relative URLs)
                        if image_url and image_url.startswith('//'):
                            image_url = f"https:{image_url}"

                    # Crear evento con categoría base CULTURE
                    events.append({
                        "source": "ticketib",
                        "title": title[:100],
                        "start_date": start_date,
                        "municipality": municipality,
                        "category": "CULTURE",  # detect_category_smart() la re-categorizará después
                        "is_free": False,
                        "price": "Consultar web",
                        "website_url": website_url,
                        "image_url": image_url
                    })

                except Exception as e:
                    # Error en un evento individual no detiene el scraper
                    continue

            page_num += 1
            time.sleep(0.5)  # Pausa cortés entre páginas

        print(f"      ✅ ticketib: {len(events)} eventos extraídos de {page_num - 1} página(s)")

    except Exception as e:
        print(f"      ❌ Error ticketib: {e}")

    return events

# ============================================================================
# ORQUESTADOR PRINCIPAL, HTML Y SERVER
# ============================================================================

def scrape_faib_atletisme() -> List[Dict]:
    """FAIB - Federació d'Atletisme de les Illes Balears. HTML estático."""
    import re
    URL = "https://www.faib.es/competicions/"
    HEADERS = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "es-ES,es;q=0.9,ca;q=0.8",
        "Referer": "https://www.google.com/",
    }
    MONTH_MAP = {
        "ene":"01","feb":"02","mar":"03","abr":"04","may":"05","jun":"06",
        "jul":"07","ago":"08","sep":"09","oct":"10","nov":"11","dic":"12",
        "gen":"01","set":"09","des":"12",
    }
    events = []
    seen = set()
    try:
        resp = requests.get(URL, headers=HEADERS, timeout=12)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "html.parser")
        now = datetime.now()
        current_year = now.year

        for row in soup.find_all(["tr", "li", "div", "article"]):
            text = row.get_text(separator=" ", strip=True)
            if not text or len(text) < 8:
                continue
            match = re.search(
                r"\b(\d{1,2})\s+(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic|gen|set|des)\b",
                text, re.IGNORECASE
            )
            if not match:
                continue
            day = match.group(1).zfill(2)
            month = MONTH_MAP.get(match.group(2).lower()[:3])
            if not month:
                continue
            year = current_year if int(month) >= now.month - 1 else current_year + 1
            date_str = f"{year}-{month}-{day}"
            title = re.sub(
                r"\b\d{1,2}\s+(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic|gen|set|des)\b",
                "", text, flags=re.IGNORECASE
            ).strip(" |-|·|/")
            title = re.sub(r"\s+", " ", title).strip()
            if not title or len(title) < 4:
                continue
            key = (title[:50].lower(), date_str)
            if key in seen:
                continue
            seen.add(key)
            events.append({
                "title": title[:120],
                "date": date_str,
                "location": "Mallorca",
                "category": "SPORT",
                "source": "FAIB Atletisme",
                "url": URL,
                "description": "",
            })
    except Exception as e:
        print(f"[FAIB] Error: {e}")
    print(f"[FAIB] {len(events)} eventos encontrados")
    return events




def scrape_fourvenues() -> List[Dict]:
    """Fourvenues — BCM Mallorca, Fitz Mallorca y Amok Mallorca. Schema.org JSON-LD."""
    import re
    VENUES = [
        ("BCM Mallorca",  "https://www.fourvenues.com/es/bcm-mallorca",  "Calvià"),
        ("Fitz Mallorca", "https://www.fourvenues.com/es/fitz-mallorca", "Palma"),
        ("Amok Mallorca", "https://site.fourvenues.com/es/discoteca-amok-mallorca@amok", "Palma"),
    ]
    HEADERS_FV = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "es-ES,es;q=0.9",
        "Referer": "https://www.google.com/",
    }
    GENERIC_BLACKLIST = []
    all_events = []
    for venue_name, url, location in VENUES:
        seen = set()
        try:
            resp = requests.get(url, headers=HEADERS_FV, timeout=12)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "html.parser")
            for tag in soup.find_all("script", type="application/ld+json"):
                try:
                    data = json.loads(tag.string or "")
                    items = []
                    if isinstance(data, dict):
                        if data.get("@type") == "ItemList":
                            items = data.get("itemListElement", [])
                        elif data.get("@type") in ("Event", "MusicEvent"):
                            items = [data]
                    elif isinstance(data, list):
                        items = data
                    for item in items:
                        if isinstance(item, dict) and item.get("@type") == "ListItem":
                            item = item.get("item", {})
                        if not isinstance(item, dict):
                            continue
                        if item.get("@type") not in ("Event", "MusicEvent"):
                            continue
                        title = (item.get("name") or "").strip()
                        date_raw = item.get("startDate") or ""
                        date_str = date_raw[:10]
                        if not title or not date_str:
                            continue
                        # Filtrar sesiones genéricas
                        title_lower = title.lower()
                        if any(bl in title_lower for bl in GENERIC_BLACKLIST):
                            continue
                        # Limpiar prefijos de fecha del título
                        title = re.sub(r"^\d{1,2}[\s/\-\.]+(?:ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)\S*\s*", "", title, flags=re.IGNORECASE).strip()
                        key = (title[:50].lower(), date_str)
                        if key in seen:
                            continue
                        seen.add(key)
                        all_events.append({
                            "title": title[:120],
                            "date": date_str,
                            "location": location,
                            "category": "NIGHTLIFE",
                            "source": venue_name,
                            "url": url,
                            "description": "",
                        })
                except Exception:
                    pass
        except Exception as e:
            print(f"[Fourvenues] Error {venue_name}: {e}")
        print(f"[Fourvenues] {venue_name}: {len([e for e in all_events if e['source'] == venue_name])} eventos")
    print(f"[Fourvenues] Total: {len(all_events)} eventos")
    return all_events



def scrape_amok() -> List[Dict]:
    """Amok Mallorca — Extracción desde web oficial con Playwright (JS-rendered)."""
    events = []
    url = "https://www.amokmallorca.com/events"
    print("   🔍 Scraping: amok_mallorca...")

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=random.choice(USER_AGENTS))
            page.goto(url, wait_until="networkidle", timeout=60000)
            page.wait_for_timeout(3000)  # Esperar a que cargue el contenido JS

            # Obtener texto de la página renderizada
            text = page.content()
            soup = BeautifulSoup(text, "html.parser")
            page_text = soup.get_text()

            browser.close()

        # Mapeo de meses
        month_map = {
            "JAN": "01", "FEB": "02", "MAR": "03", "APR": "04",
            "MAY": "05", "JUN": "06", "JUL": "07", "AUG": "08",
            "SEP": "09", "OCT": "10", "NOV": "11", "DEC": "12"
        }

        current_year = datetime.now().year
        seen = set()

        # Pattern para extraer eventos
        # Formato compacto: "WED · 23 SEPWED 23 SEP21:30 – 04:00Title"
        import re

        # Buscar bloques de eventos con regex
        # Pattern: [DAY] · [DD] [MONTH][DAY] [DD] [MONTH][HH:MM] – [HH:MM][TITLE]
        pattern = r'([A-Z]{3})\s*·\s*(\d{1,2})\s+([A-Z]{3})[A-Z]{3}\s+\d{1,2}\s+[A-Z]{3}(\d{2}:\d{2})\s*[–-]\s*\d{2}:\d{2}([^T]+?)(?=TICKETS|TABLES|[A-Z]{3}\s*·|\Z)'

        matches = re.findall(pattern, page_text, re.DOTALL)

        for day_abbr, day_num, month_abbr, start_time, title in matches:
            title = title.strip()

            # Limpiar título
            title = re.sub(r'\s+', ' ', title)
            title = title.title()  # Title case

            if not title or len(title) < 3:
                continue

            # Construir fecha
            month = month_map.get(month_abbr, "01")
            day = day_num.zfill(2)
            date_str = f"{current_year}-{month}-{day}"

            # Evitar duplicados
            key = (date_str, title.lower())
            if key in seen:
                continue
            seen.add(key)

            # Validar fecha
            try:
                datetime.strptime(date_str, "%Y-%m-%d")
            except:
                continue

            events.append({
                "source": "amok_mallorca",
                "title": title[:100],
                "start_date": date_str,
                "start_time": start_time,
                "municipality": "Palma",
                "location": "Amok Mallorca",
                "category": "NIGHTLIFE",
                "is_free": False,
                "price": "Consultar web",
                "website_url": url,
            })

        print(f"      ✅ amok_mallorca: {len(events)} eventos")
    except Exception as e:
        print(f"      ❌ amok_mallorca error: {e}")

    return events


def scrape_all_sources() -> List[Dict]:
    print("🌐 Iniciando extracción masiva de eventos (Multihilo)...")

    # Reset historial para evitar filtrado acumulativo
    approved_path = TOOLS_DIR / "approved_events.json"
    with open(approved_path, "w", encoding="utf-8") as f:
        json.dump([], f)

    historical_ids = load_historical_ids(APPROVED_EVENTS_FILE)
    if historical_ids:
        print(f"   ℹ️  Cargados {len(historical_ids)} eventos históricos para omitir.")
    print("=" * 60)
    
    all_events = []
    
    scrapers = [
        scrape_auditorium_palma,
        scrape_mallorcafiestas,
        scrape_mallorcamusicmagazine,
        scrape_conciertos_club,
        scrape_mallorca_com_gastronomia,
        scrape_mallorca_com,
        scrape_wepartynow,
        scrape_ime_palma,
        scrape_firesifestes,
        scrape_ticketib,
        scrape_fourvenues,
        scrape_faib_atletisme,
        scrape_amok,
    ]

    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(scraper): scraper.__name__ for scraper in scrapers}
        for future in concurrent.futures.as_completed(futures):
            try: all_events.extend(future.result())
            except Exception as e: print(f"❌ Fallo en Worker {futures[future]}: {e}")

    # Cargar y categorizar cachés de fuentes dedicadas
    cache_files = ["thecalendar_events.json", "ime_events_fixed.json", "sports_events.json"]
    for file_name in cache_files:
        file_path = TOOLS_DIR / file_name
        if file_path.exists():
            with open(file_path, "r", encoding="utf-8") as _f:
                cache = json.load(_f)
                for ev in cache:
                    titulo = ev.get("title", "")
                    descripcion = ev.get("description", "")
                    if file_name == "sports_events.json":
                        # Sports events: detect_category_smart decide libremente
                        # Si devuelve CULTURE por defecto y no hay keywords culturales, asignar SPORT
                        cat = detect_category_smart(titulo, descripcion)
                        cultural_kws = ["charla", "taller", "conferencia", "conferència", "col·loqui",
                                        "lectura", "exposici", "teatro", "teatre", "dansa", "danza",
                                        "ruta guiada", "passeig", "fotografia", "fotografía",
                                        "presentació", "presentacion", "curs d'iniciació", "curs de",
                                        "llibre", "club de lectura", "microteatre"]
                        titulo_low = titulo.lower()
                        if cat == "CULTURE" and not any(kw in titulo_low for kw in cultural_kws):
                            ev["category"] = "SPORT"
                        else:
                            ev["category"] = cat
                    else:
                        ev["category"] = detect_category_smart(titulo, descripcion)
                # Filtrar: eventos fuera de Mallorca y sub-eventos genéricos de fiestas
                filtered_cache = []
                for ev in cache:
                    muni = ev.get("municipality", "") or ""
                    title_ev = ev.get("title", "").lower().strip()
                    # Descartar si es de otra isla
                    if muni == "FOREIGN" or any(f in title_ev for f in FOREIGN_ISLAND_PATTERNS):
                        continue
                    # Descartar sub-eventos genéricos de programas festivos
                    if muni in ("", "Mallorca") and title_ev in SUBEVENT_BLACKLIST:
                        continue
                    filtered_cache.append(ev)
                all_events.extend(filtered_cache)
                print(f"   ✅ Cache recuperada y categorizada: {len(cache)} eventos de {file_name}")

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

    # ✅ Deduplicación por título exacto (eventos recurrentes)
    # Mantener solo la primera aparición (fecha más próxima)
    print(f"🔍 Eliminando eventos recurrentes por título exacto...")
    seen_titles = set()
    unique_events = []
    duplicates_removed = 0
    for e in future_events:
        title_normalized = e.get("title", "").lower().strip()
        if title_normalized not in seen_titles:
            seen_titles.add(title_normalized)
            unique_events.append(e)
        else:
            duplicates_removed += 1

    if duplicates_removed > 0:
        print(f"   ✂️  {duplicates_removed} eventos recurrentes eliminados → {len(unique_events)} únicos")
    else:
        print(f"   ✅ No hay eventos recurrentes detectados")

    future_events = unique_events

    DEFAULT_IMAGES = {
        "CONCERT": "https://images.unsplash.com/photo-1540039155732-6761b54cb111?q=80&w=800",
        "FESTIVAL": "https://images.unsplash.com/photo-1533174000275-192cb1a8c084?q=80&w=800",
        "SPORT": "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?q=80&w=800",
        "CULTURE": "https://images.unsplash.com/photo-1514306191717-452ec28c7814?q=80&w=800",
        "GASTRONOMY": "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?q=80&w=800",
        "MARKET": "https://images.unsplash.com/photo-1533900298318-6b8da08a523e?q=80&w=800",
        "NIGHTLIFE": "https://images.unsplash.com/photo-1566737236500-c8ac43014a67?q=80&w=800",
        "FAMILY": "https://images.unsplash.com/photo-1511895426328-dc8714191300?q=80&w=800",
    }
    for e in future_events:
        if not e.get("image_url") or "mallorca-logo.svg" in e.get("image_url", ""):
            e["image_url"] = DEFAULT_IMAGES.get(e["category"], DEFAULT_IMAGES["CULTURE"])

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
        <button onclick="approveAll()" style="background:green;color:white;font-weight:bold;margin-right:10px;">✅ Aprobar Todos</button>
        <button onclick="rejectAll()" style="background:#cc0000;color:white;font-weight:bold;margin-right:10px;">❌ Desaprobar Todos</button>
        <button onclick="downloadApproved()" style="background:blue;color:white;font-weight:bold;">💾 Guardar Aprobados</button>
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
                <button onclick="toggle('${{e.id}}')" style="background:${{approved.has(e.id)?'green':'#eee'}};color:${{approved.has(e.id)?'white':'black'}}">
    ${{approved.has(e.id)?'✅ Aprobado':'Aprobar'}}</button>`;
                grid.appendChild(d);
            }});
        }}
        
        function toggle(id) {{ approved.has(id) ? approved.delete(id) : approved.add(id); render(); }}

        function approveAll() {{ events.forEach(e => approved.add(e.id)); render(); }}
        function rejectAll() {{ approved.clear(); render(); }}

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

