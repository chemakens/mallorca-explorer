#!/usr/bin/env python3
"""
Web Scraping Events for Mallorca
---------------------------------
Scrapes multiple Mallorca event websites with specific selectors
for accurate date and price extraction.
"""

import sys
import json
import hashlib
import re
import threading as _th
import webbrowser
import time
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional, Set
from http.server import HTTPServer, BaseHTTPRequestHandler

try:
    import requests
    from bs4 import BeautifulSoup
    from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeout
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install requests beautifulsoup4 lxml playwright")
    print("   playwright install chromium")
    sys.exit(1)

# Paths
TOOLS_DIR = Path(__file__).parent
REVIEW_HTML = TOOLS_DIR / "review.html"

# Mallorca municipalities
MALLORCA_MUNICIPALITIES = {
    "palma", "calvià", "manacor", "llucmajor", "marratxí", "inca", "alcúdia",
    "felanitx", "pollença", "sóller", "sa pobla", "artà", "campos", "santanyí",
    "ses salines", "capdepera", "andratx", "santa margalida", "petra", "muro",
    "alaró", "binissalem", "sant llorenç", "santa maria", "esporles", "sineu",
    "porreres", "algaida", "consell", "búger", "costitx", "ariany", "banyalbufar",
    "campanet", "deià", "escorca", "estellencs", "fornalutx", "lloret", "lloseta",
    "maria de la salut", "montuïri", "puigpunyent", "sant joan", "selva", "sencelles",
    "vilafranca"
}

# Category keywords
CATEGORY_KEYWORDS = {
    "CONCERT": ["concierto", "concert", "música", "music", "recital", "jazz", "rock", "pop", "orquestra", "sinfónica"],
    "FESTIVAL": ["festival", "feria", "fiesta", "festa", "celebración"],
    "SPORT": ["deporte", "sport", "triatlón", "maratón", "ciclismo", "running", "vela", "regata"],
    "CULTURE": ["exposición", "exhibition", "teatro", "theatre", "danza", "dance", "conferencia", "obra"],
    "GASTRONOMY": ["gastronómico", "gastronomic", "cata", "tasting", "culinaria"],
    "MARKET": ["mercado", "market", "mercat", "artesanía", "fira"],
    "NIGHTLIFE": ["fiesta", "party", "noche", "night", "discoteca", "club"],
    "FAMILY": ["familia", "family", "niños", "kids", "children", "infantil", "familiar"],
}


def generate_event_id(source: str, title: str, date: str) -> str:
    """Generate unique event ID."""
    content = f"{source}-{title}-{date}"
    hash_obj = hashlib.md5(content.encode())
    return f"web-{source}-{hash_obj.hexdigest()[:12]}"


def detect_category(text: str) -> str:
    """Detect category from text."""
    text_lower = text.lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(keyword in text_lower for keyword in keywords):
            return category
    return "CULTURE"


def normalize_municipality(location: str) -> Optional[str]:
    """Normalize location to Mallorca municipality."""
    if not location:
        return None

    location_lower = location.lower().strip()

    if location_lower in MALLORCA_MUNICIPALITIES:
        return location.title()

    for municipality in MALLORCA_MUNICIPALITIES:
        if municipality in location_lower:
            return municipality.title()

    if "palma" in location_lower:
        return "Palma"

    return None


def parse_date(date_str: str) -> Optional[str]:
    """Parse date string to ISO format (YYYY-MM-DD)."""
    if not date_str:
        return None

    # Clean up the date string
    date_str = date_str.strip()

    # Spanish month names
    months_es = {
        "enero": "01", "febrero": "02", "marzo": "03", "abril": "04",
        "mayo": "05", "junio": "06", "julio": "07", "agosto": "08",
        "septiembre": "09", "octubre": "10", "noviembre": "11", "diciembre": "12",
        "ene": "01", "feb": "02", "mar": "03", "abr": "04", "may": "05",
        "jun": "06", "jul": "07", "ago": "08", "sep": "09", "oct": "10",
        "nov": "11", "dic": "12"
    }

    date_normalized = date_str.lower()
    for month_name, month_num in months_es.items():
        date_normalized = date_normalized.replace(month_name, month_num)

    # Try common formats
    formats = [
        "%d/%m/%Y", "%d-%m-%Y", "%Y-%m-%d",
        "%d de %m de %Y", "%d %m %Y", "%d.%m.%Y"
    ]

    for fmt in formats:
        try:
            dt = datetime.strptime(date_normalized, fmt)
            return dt.strftime("%Y-%m-%d")
        except ValueError:
            continue

    # Extract with regex (dd/mm/yyyy or dd-mm-yyyy)
    match = re.search(r'(\d{1,2})[/-](\d{1,2})[/-](\d{4})', date_str)
    if match:
        day, month, year = match.groups()
        try:
            dt = datetime(int(year), int(month), int(day))
            return dt.strftime("%Y-%m-%d")
        except ValueError:
            pass

    return None


def extract_price(text: str) -> tuple[bool, Optional[str]]:
    """Extract price from text."""
    text_lower = text.lower()

    if any(word in text_lower for word in ["gratis", "gratuito", "free", "entrada libre", "sin coste"]):
        return True, None

    price_match = re.search(r'(\d+(?:[.,]\d{2})?)\s*€', text)
    if price_match:
        price_str = price_match.group(1).replace(',', '.')
        return False, f"{price_str}€"

    return True, None


# ============================================================================
# SPECIFIC SCRAPERS WITH ACCURATE SELECTORS
# ============================================================================

def scrape_auditorium_palma() -> List[Dict]:
    """Scrape Auditorium Palma with specific selectors."""
    events = []
    url = "https://auditoriumpalma.com/es/"

    try:
        print(f"   🔍 Scraping (Playwright): auditorium...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(2000)

            html_content = page.content()
            browser.close()

            soup = BeautifulSoup(html_content, 'html.parser')

            # Auditorium uses Elementor cards
            event_cards = soup.select('article.elementor-post')

            for card in event_cards:  # Process ALL events, not just first 40
                # Title - find the link in the card
                link_elem = card.select_one('a[href*="/espectaculo/"]')
                if not link_elem:
                    continue

                title_elem = card.select_one('h3, h2, .elementor-post__title')
                if not title_elem:
                    # Try to get all text from the card as title
                    text_div = card.select_one('.elementor-post__text')
                    if text_div:
                        lines = [line.strip() for line in text_div.stripped_strings]
                        title = lines[0] if lines else None
                    else:
                        continue
                else:
                    title = title_elem.get_text(strip=True)

                event_url = link_elem.get('href', url)

                # Date - extract from .elementor-post-date
                date_elem = card.select_one('.elementor-post-date')
                date_str = None

                if date_elem:
                    date_text = date_elem.get_text(strip=True)
                    # Format: "Del 11 Sep. al 12 Sep. 2026" or "11 Sep. 2026"
                    # Extract the date
                    match = re.search(r'(\d{1,2})\s+(\w+)\.?\s+(\d{4})', date_text)
                    if match:
                        day, month, year = match.groups()
                        # Convert Spanish month abbreviation
                        month_map = {
                            'ene': '01', 'feb': '02', 'mar': '03', 'abr': '04',
                            'may': '05', 'jun': '06', 'jul': '07', 'ago': '08',
                            'sep': '09', 'oct': '10', 'nov': '11', 'dic': '12'
                        }
                        month_num = month_map.get(month.lower()[:3], '01')
                        date_str = f"{year}-{month_num}-{day.zfill(2)}"

                # Price - check for "Comprar entrada" button (means paid)
                text = card.get_text()
                has_buy_button = 'comprar entrada' in text.lower()

                if has_buy_button:
                    is_free = False
                    price = "Consultar web"
                else:
                    is_free, price = extract_price(text)

                if title and date_str:
                    events.append({
                        "source": "auditorium",
                        "title": title,
                        "start_date": date_str,
                        "municipality": "Palma",
                        "category": detect_category(title + " " + text),
                        "is_free": is_free,
                        "price": price,
                        "website_url": event_url,
                    })

        print(f"      ✅ Found {len(events)} events")
    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_firesifestes() -> List[Dict]:
    """Scrape Fires i Festes with specific selectors."""
    events = []
    url = "https://firesifestes.es/es/"

    try:
        print(f"   🔍 Scraping: firesifestes...")

        session = requests.Session()
        session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
        })

        response = session.get(url, timeout=15)
        soup = BeautifulSoup(response.content, 'html.parser')

        # Find event items
        event_items = soup.select('article, .event, .fiesta, .post')

        for item in event_items:  # Process ALL events
            # Title
            title_elem = item.select_one('h2, h3, .entry-title, .title')
            if not title_elem:
                continue

            title = title_elem.get_text(strip=True)
            text = item.get_text()

            # Date
            date_elem = item.select_one('.date, .fecha, time, .event-date')
            date_str = None
            if date_elem:
                date_str = parse_date(date_elem.get_text(strip=True))

            if not date_str:
                date_str = parse_date(text)

            # Municipality
            municipality = normalize_municipality(text)

            # Link
            link_elem = item.select_one('a[href]')
            event_url = link_elem.get('href') if link_elem else url
            if event_url and not event_url.startswith('http'):
                from urllib.parse import urljoin
                event_url = urljoin(url, event_url)

            # Price
            is_free, price = extract_price(text)

            if title and municipality:
                events.append({
                    "source": "firesifestes",
                    "title": title,
                    "start_date": date_str or "2026-09-01",
                    "municipality": municipality,
                    "category": "FESTIVAL",
                    "is_free": is_free,
                    "price": price,
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")
    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_mallorcafiestas() -> List[Dict]:
    """Scrape Mallorca Fiestas with specific selectors."""
    events = []
    url = "https://mallorcafiestas.com"

    try:
        print(f"   🔍 Scraping: mallorcafiestas...")

        session = requests.Session()
        session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
        })

        response = session.get(url, timeout=15)
        soup = BeautifulSoup(response.content, 'html.parser')

        event_items = soup.select('article, .fiesta, .event')

        for item in event_items:  # Process ALL events
            title_elem = item.select_one('h2, h3, .title')
            if not title_elem:
                continue

            title = title_elem.get_text(strip=True)
            text = item.get_text()

            # Date
            date_str = parse_date(text)

            # Municipality
            municipality = normalize_municipality(text)

            # Price
            is_free, price = extract_price(text)

            if municipality:
                events.append({
                    "source": "mallorcafiestas",
                    "title": title,
                    "start_date": date_str or "2026-09-01",
                    "municipality": municipality,
                    "category": "FESTIVAL",
                    "is_free": is_free,
                    "price": price,
                    "website_url": url,
                })

        print(f"      ✅ Found {len(events)} events")
    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_mallorcamusicmagazine() -> List[Dict]:
    """Scrape Mallorca Music Magazine with specific selectors."""
    events = []
    url = "https://mallorcamusicmagazine.com/conciertos/"

    try:
        print(f"   🔍 Scraping: mallorcamusicmagazine...")

        session = requests.Session()
        session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
        })

        response = session.get(url, timeout=15)
        soup = BeautifulSoup(response.content, 'html.parser')

        event_items = soup.select('li.qodef-blog-list-item')

        for item in event_items:  # Process ALL events
            # Title and URL
            link_elem = item.select_one('a[href*="/concierto/"]')
            if not link_elem:
                continue

            event_url = link_elem.get('href', url)

            # Title is in the text content
            text = item.get_text()
            lines = [line.strip() for line in text.split('\n') if line.strip()]

            # First substantial line is usually the artist/title
            title = None
            for line in lines:
                if len(line) > 3 and line not in ['DESTACADOS', 'MÚSICA', 'DEPORTES']:
                    title = line
                    break

            if not title:
                continue

            # Date extraction - look for patterns like "9 septiembre 2026"
            date_str = None
            for line in lines:
                match = re.search(r'(\d{1,2})\s+(\w+)\s+(\d{4})', line)
                if match:
                    day, month_name, year = match.groups()
                    month_map = {
                        'enero': '01', 'febrero': '02', 'marzo': '03', 'abril': '04',
                        'mayo': '05', 'junio': '06', 'julio': '07', 'agosto': '08',
                        'septiembre': '09', 'octubre': '10', 'noviembre': '11', 'diciembre': '12'
                    }
                    month = month_map.get(month_name.lower(), '01')
                    date_str = f"{year}-{month}-{day.zfill(2)}"
                    break

            # Municipality - default to Palma, look for specific mentions
            municipality = "Palma"
            text_lower = text.lower()
            for muni in ["inca", "manacor", "alcúdia", "pollença", "sóller"]:
                if muni in text_lower:
                    municipality = muni.title()
                    break

            # Price
            is_free, price = extract_price(text)

            if title and date_str:
                events.append({
                    "source": "mallorcamusicmagazine",
                    "title": title,
                    "start_date": date_str,
                    "municipality": municipality,
                    "category": "CONCERT",
                    "is_free": is_free,
                    "price": price or "Consultar web",
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")
    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_conciertos_club() -> List[Dict]:
    """Scrape Conciertos.club Baleares with specific selectors."""
    events = []
    url = "https://conciertos.club/baleares"

    try:
        print(f"   🔍 Scraping: conciertos.club...")

        session = requests.Session()
        session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
        })

        response = session.get(url, timeout=15)
        soup = BeautifulSoup(response.content, 'html.parser')

        # Select individual event items within articles
        event_items = soup.select('article li[itemprop="itemListElement"]')

        for item in event_items:  # Process ALL events
            text = item.get_text()
            lines = [line.strip() for line in text.split('\n') if line.strip()]

            # Link
            link_elem = item.select_one('a[href^="/baleares/conciertos/"]')
            if not link_elem:
                continue

            event_url = "https://conciertos.club" + link_elem.get('href', '')

            # Title - extract artist name
            # Structure: time (20:00), artist name, genre (/ Genre), venue
            title = None
            for i, line in enumerate(lines):
                # Skip time (HH:MM pattern)
                if re.match(r'^\d{2}:\d{2}$', line):
                    continue
                # Skip genre lines (start with /)
                if line.startswith('/'):
                    continue
                # Skip venue/location lines (contain "Palma de Mallorca")
                if 'Palma de Mallorca' in line or 'Baleares' in line or 'Comprar' in line:
                    continue
                # Artist name should be a short, substantial line
                if len(line) > 2 and len(line) < 100:
                    title = line
                    break

            if not title:
                continue

            # Date - look in parent article for date
            parent_article = item.find_parent('article')
            date_str = None
            if parent_article:
                date_div = parent_article.select_one('.tit')
                if date_div:
                    date_text = date_div.get_text(strip=True)
                    match = re.search(r'(\d{1,2})\s+de\s+(\w+)', date_text)
                    if match:
                        day, month_name = match.groups()
                        month_map = {
                            'enero': '01', 'febrero': '02', 'marzo': '03', 'abril': '04',
                            'mayo': '05', 'junio': '06', 'julio': '07', 'agosto': '08',
                            'septiembre': '09', 'octubre': '10', 'noviembre': '11', 'diciembre': '12'
                        }
                        month = month_map.get(month_name.lower(), '09')
                        # Assume 2026 for future events
                        date_str = f"2026-{month}-{day.zfill(2)}"

            if not date_str:
                continue

            # Municipality
            municipality = normalize_municipality(text) or "Palma"

            # Price
            is_free, price = extract_price(text)

            if title and date_str:
                events.append({
                    "source": "conciertos.club",
                    "title": title,
                    "start_date": date_str,
                    "municipality": municipality,
                    "category": "CONCERT",
                    "is_free": is_free,
                    "price": price or "Consultar web",
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")
    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_resident_advisor() -> List[Dict]:
    """Scrape Resident Advisor events using their GraphQL API."""
    events = []
    url = "https://ra.co/graphql"

    try:
        print(f"   🔍 Scraping: Resident Advisor API...")

        # Date range: from today to end of year
        from datetime import datetime
        today = datetime.now()
        end_date = f"{today.year}-12-31"
        start_date = today.strftime("%Y-%m-%d")

        # GraphQL query for Mallorca events (area ID 31)
        query = f"""
        {{
          eventListings(
            filters: {{
              areas: {{ eq: 31 }}
              listingDate: {{ gte: "{start_date}", lte: "{end_date}" }}
            }}
            pageSize: 100
          ) {{
            data {{
              event {{
                title
                date
                venue {{
                  name
                  area {{
                    name
                  }}
                }}
              }}
            }}
          }}
        }}
        """

        response = requests.post(
            url,
            json={"query": query},
            headers={
                "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                "Referer": "https://ra.co",
                "Content-Type": "application/json"
            },
            timeout=15
        )

        if response.status_code != 200:
            print(f"      ❌ API returned status {response.status_code}")
            return events

        data = response.json()

        # Check for GraphQL errors
        if 'errors' in data:
            print(f"      ⚠️  GraphQL errors: {data['errors'][0]['message']}")
            return events

        # Extract events
        if 'data' in data and 'eventListings' in data['data']:
            api_events = data['data']['eventListings']['data']

            for item in api_events:
                evt = item['event']

                # Parse date (format: YYYY-MM-DD)
                date_str = evt['date']

                # Municipality from venue name or area
                municipality = "Palma"
                venue_name = evt['venue']['name']

                # Try to extract municipality from venue name or area
                area_name = evt['venue']['area']['name']
                if 'ibiza' in area_name.lower():
                    continue  # Skip Ibiza events
                if area_name == 'Mallorca' or 'palma' in venue_name.lower():
                    municipality = "Palma"

                # Build event URL
                # RA URLs are like: https://ra.co/events/{event-id}
                event_url = "https://ra.co"  # Base URL, specific event URL requires event ID

                events.append({
                    "source": "residentadvisor",
                    "title": evt['title'],
                    "start_date": date_str,
                    "municipality": municipality,
                    "category": "CONCERT",
                    "is_free": False,
                    "price": "Consultar web",
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_wodira() -> List[Dict]:
    """Scrape Wodira.app Mallorca calendar with Playwright."""
    events = []
    url = "https://wodira.app/es/calendar/mallorca"

    try:
        print(f"   🔍 Scraping (Playwright): wodira...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(3000)

            # Click "Mostrar más eventos" until no more available
            click_count = 0
            max_clicks = 20  # Safety limit
            while click_count < max_clicks:
                try:
                    # Look for "Mostrar más eventos" button
                    show_more = page.query_selector('button:has-text("Mostrar más eventos")')

                    if show_more and show_more.is_visible():
                        show_more.click()
                        page.wait_for_timeout(2000)  # Wait for new events to load
                        click_count += 1
                    else:
                        break  # No more button available
                except:
                    break  # Button not found or error

            html_content = page.content()
            browser.close()

            soup = BeautifulSoup(html_content, 'html.parser')

            # Wodira uses <a href="/es/events/..."> links for each event
            event_links = soup.select('a[href*="/events/"]')

            today = datetime.now().date()

            for link in event_links:
                # Title - in h3 tag
                title_elem = link.select_one('h3')
                if not title_elem:
                    continue
                title = title_elem.get_text(strip=True)

                if not title or len(title) < 3:
                    continue

                # Get full text for date and location extraction
                text = link.get_text()

                # Date - extract from Spanish date format: "domingo, 6 de septiembre de 2026"
                date_str = None
                date_match = re.search(r'(\d{1,2})\s+de\s+(\w+)\s+de\s+(\d{4})', text)
                if date_match:
                    day, month_name, year = date_match.groups()
                    # Convert Spanish month name to number
                    month_map = {
                        'enero': '01', 'febrero': '02', 'marzo': '03', 'abril': '04',
                        'mayo': '05', 'junio': '06', 'julio': '07', 'agosto': '08',
                        'septiembre': '09', 'octubre': '10', 'noviembre': '11', 'diciembre': '12'
                    }
                    month_num = month_map.get(month_name.lower(), '01')
                    date_str = f"{year}-{month_num}-{day.zfill(2)}"

                if not date_str:
                    continue

                # Filter future events only (>= today)
                try:
                    event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                    if event_date < today:
                        continue
                except:
                    continue

                # Location - extract from text (format: "CALLE X, 07XXX City, España")
                location = ""
                location_match = re.search(r'([A-ZÑÁÉÍÓÚ\s,\d-]+,\s*\d{5}\s+[A-Za-zÑñÁáÉéÍíÓóÚú\s]+),\s*España', text)
                if location_match:
                    location = location_match.group(1).strip()
                else:
                    # Try simpler pattern
                    location_match = re.search(r'(\d{5}\s+[A-Za-zÑñÁáÉéÍíÓóÚú\s]+),\s*España', text)
                    if location_match:
                        location = location_match.group(1).strip()

                # Municipality
                municipality = normalize_municipality(location) or "Mallorca"

                # Source URL
                event_url = link.get('href', '')
                if event_url.startswith('/'):
                    event_url = f"https://wodira.app{event_url}"

                # Description - get event type/category badges
                badges = link.select('.badge')
                description = ' '.join([b.get_text(strip=True) for b in badges if b.get_text(strip=True)])

                events.append({
                    "source": "wodira",
                    "title": title,
                    "description": description[:200] if description else "",
                    "start_date": date_str,
                    "municipality": municipality,
                    "location": location,
                    "category": "SPORT",
                    "is_free": True,
                    "price": None,
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_mallorca_com() -> List[Dict]:
    """Scrape mallorca.com events calendar with Playwright for lazy loading."""
    events = []
    url = "https://www.mallorca.com/es/eventos"

    try:
        print(f"   🔍 Scraping (Playwright): mallorca.com...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(3000)

            # Click "Más" button to load more events
            click_count = 0
            max_clicks = 30
            while click_count < max_clicks:
                try:
                    load_more = page.query_selector('button:has-text("Más")')
                    if load_more and load_more.is_visible():
                        load_more.click()
                        page.wait_for_timeout(2000)
                        click_count += 1
                    else:
                        break
                except:
                    break

            html_content = page.content()
            browser.close()

            soup = BeautifulSoup(html_content, 'html.parser')

            # Find JSON-LD structured data with event list
            ld_scripts = soup.find_all('script', type='application/ld+json')

            today = datetime.now().date()

            for script in ld_scripts:
                try:
                    data = json.loads(script.string)

                    # Check if it's an ItemList with events
                    if isinstance(data, dict) and data.get('@type') == 'ItemList':
                        items = data.get('itemListElement', [])

                        for item in items:
                            event_data = item.get('item', {})

                            # Verify it's an Event type
                            if event_data.get('@type') != 'Event':
                                continue

                            # Extract event details
                            title = event_data.get('name', '')
                            if not title or len(title) < 3:
                                continue

                            # Date
                            date_str = event_data.get('startDate', '')
                            if not date_str:
                                continue

                            # Filter future events only
                            try:
                                event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                                if event_date < today:
                                    continue
                            except:
                                continue

                            # Location
                            location_data = event_data.get('location', {})
                            if isinstance(location_data, dict):
                                location_name = location_data.get('name', '')
                                municipality = normalize_municipality(location_name) or location_name or "Mallorca"
                            else:
                                location_name = ""
                                municipality = "Mallorca"

                            # Category
                            category = detect_category(title + " " + event_data.get('description', ''))

                            # URL
                            event_url = event_data.get('url', url)

                            events.append({
                                "source": "mallorca.com",
                                "title": title,
                                "description": event_data.get('description', '')[:200],
                                "start_date": date_str,
                                "municipality": municipality,
                                "location": location_name,
                                "category": category,
                                "is_free": None,
                                "price": "Consultar web",
                                "website_url": event_url,
                            })

                except json.JSONDecodeError:
                    continue

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_ime_palma() -> List[Dict]:
    """Scrape IME Palma events with pagination (26 pages)."""
    events = []
    base_url = "https://ime.palma.es/es/eventos"

    try:
        print(f"   🔍 Scraping: IME Palma...")

        today = datetime.now().date()

        # Iterate through all pages (1 to 27 to ensure we get everything)
        for page_num in range(1, 28):
            try:
                # Build paginated URL (Liferay pattern)
                if page_num == 1:
                    url = base_url
                else:
                    url = f"{base_url}?p_p_id=com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_zgzD14PYJk5w&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view&_com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_zgzD14PYJk5w_delta=12&p_r_p_resetCur=false&_com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_zgzD14PYJk5w_cur={page_num}"

                response = requests.get(
                    url,
                    timeout=15,
                    headers={
                        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
                        'Accept-Language': 'es-ES,es;q=0.9'
                    }
                )

                if response.status_code != 200:
                    break

                soup = BeautifulSoup(response.text, 'html.parser')

                # Find event links (pattern: /-/ with redirect parameter)
                all_links = soup.find_all('a', href=True)
                event_links = [link for link in all_links if '/-/' in link.get('href', '') and 'redirect=' in link.get('href', '')]

                if not event_links:
                    # No events on this page, stop
                    break

                page_events = 0
                for link in event_links:
                    href = link.get('href', '')
                    title = link.get_text(strip=True)

                    if not title or len(title) < 5:
                        continue

                    # Infer date from title or surrounding text
                    date_str = None

                    # Look for month/year in title
                    month_match = re.search(r'(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\s+(\d{4})', title.lower())
                    if month_match:
                        month_name, year = month_match.groups()
                        month_map = {
                            'enero': '01', 'febrero': '02', 'marzo': '03', 'abril': '04',
                            'mayo': '05', 'junio': '06', 'julio': '07', 'agosto': '08',
                            'septiembre': '09', 'octubre': '10', 'noviembre': '11', 'diciembre': '12'
                        }
                        month_num = month_map.get(month_name, '01')
                        date_str = f"{year}-{month_num}-01"
                    elif '2026' in title or '2027' in title:
                        # Extract year and use September as default month
                        year_match = re.search(r'(202[6-9])', title)
                        if year_match:
                            date_str = f"{year_match.group(1)}-09-01"
                    else:
                        # No date in title - check parent for additional context
                        parent = link.find_parent(['div', 'li', 'article'])
                        if parent:
                            parent_text = parent.get_text()
                            # Look for year in parent
                            year_match = re.search(r'(202[6-9])', parent_text)
                            if year_match:
                                date_str = f"{year_match.group(1)}-09-01"
                            else:
                                # Default to upcoming date for IME municipal events
                                date_str = "2026-09-15"

                    # Build full URL
                    event_url = href if href.startswith('http') else f"https://ime.palma.es{href}"

                    events.append({
                        "source": "ime_palma",
                        "title": title,
                        "description": "",
                        "start_date": date_str,
                        "municipality": "Palma",
                        "location": "",
                        "category": "SPORT",
                        "is_free": True,
                        "price": None,
                        "website_url": event_url,
                    })
                    page_events += 1

                if page_events == 0:
                    break

                # Small delay between pages
                time.sleep(0.3)

            except Exception as e:
                print(f"      ⚠️  Error on page {page_num}: {e}")
                break

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_wepartynow() -> List[Dict]:
    """Scrape WePartyNow Mallorca nightlife events with infinite scroll."""
    events = []
    url = "https://wepartynow.com/es/es/mallorca/esta-noche"

    # Blacklisted venues to skip
    VENUE_BLACKLIST = [
        "SECRETS MALLORCA",
        "Megasport",
    ]

    try:
        print(f"   🔍 Scraping (Playwright): wepartynow...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(3000)

            # Infinite scroll to load all events
            prev_count = 0
            no_change_count = 0
            max_scrolls = 20

            for scroll_num in range(max_scrolls):
                # Scroll to bottom
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                page.wait_for_timeout(2000)

                # Count current events
                html = page.content()
                soup = BeautifulSoup(html, 'html.parser')

                # Find event links
                all_links = soup.find_all('a', href=True)
                event_links = [link for link in all_links if '/es/es/mallorca/events/' in link.get('href', '')]
                current_count = len(event_links)

                # Check if loaded more
                if current_count == prev_count:
                    no_change_count += 1
                    if no_change_count >= 3:
                        break  # No more events loading
                else:
                    no_change_count = 0

                prev_count = current_count

            # Final HTML after all scrolling
            html_content = page.content()
            browser.close()

            soup = BeautifulSoup(html_content, 'html.parser')

            # Find all event links
            all_links = soup.find_all('a', href=True)
            event_links = [link for link in all_links if '/es/es/mallorca/events/' in link.get('href', '')]

            today = datetime.now().date()

            for link in event_links:
                href = link.get('href', '')

                # Extract title from h3 element (clean, without venue)
                title_elem = link.select_one('h3')
                title = title_elem.get_text(strip=True) if title_elem else ""

                if not title or len(title) < 3:
                    continue

                # Extract venue from span.truncate element
                venue_elem = link.select_one('span.truncate')
                location = venue_elem.get_text(strip=True) if venue_elem else ""

                # Extract date from the date badge text
                # Look for the date badge (contains "dom, 6 sept" format)
                date_badge = link.select_one('.inline-flex.items-center.rounded-full')
                if not date_badge:
                    continue

                date_text = date_badge.get_text(strip=True)

                # Try to extract date (format: "dom, 6 sept")
                date_match = re.search(r'(\w{3}), (\d+) (\w+)', date_text)
                if not date_match:
                    continue

                day_name, day, month_abbr = date_match.groups()

                # Map Spanish month abbreviations
                month_map = {
                    'ene': '01', 'feb': '02', 'mar': '03', 'abr': '04',
                    'may': '05', 'jun': '06', 'jul': '07', 'ago': '08',
                    'sept': '09', 'oct': '10', 'nov': '11', 'dic': '12'
                }

                month = month_map.get(month_abbr.lower(), '09')

                # Infer year (events in Sept-Dec = 2026, Jan-Aug = 2027)
                month_num = int(month)
                year = '2027' if month_num <= 8 else '2026'

                date_str = f"{year}-{month}-{day.zfill(2)}"

                # Filter future events only
                try:
                    event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                    if event_date < today:
                        continue
                except:
                    continue

                # Skip blacklisted venues
                if location in VENUE_BLACKLIST:
                    continue

                # Build full URL
                event_url = href
                if href.startswith('/'):
                    event_url = f"https://wepartynow.com{href}"

                events.append({
                    "source": "wepartynow",
                    "title": title[:100],  # Limit title length
                    "description": "",
                    "start_date": date_str,
                    "municipality": "Palma",  # Most nightlife is in Palma
                    "location": location[:50] if location else "",
                    "category": "NIGHTLIFE",
                    "is_free": False,
                    "price": "Consultar web",
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_inmallorca_magazine() -> List[Dict]:
    """Scrape InMallorca Magazine festivals and fairs guide."""
    events = []
    url = "https://www.inmallorcamagazine.com/es/events-and-festivals/festivals-fairs-annual-guide"

    try:
        print(f"   🔍 Scraping (Playwright): inmallorca magazine...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="load", timeout=45000)
            page.wait_for_timeout(4000)

            html_content = page.content()
            browser.close()

            soup = BeautifulSoup(html_content, 'html.parser')

            # Month mapping
            month_map = {
                'enero': '01', 'febrero': '02', 'marzo': '03', 'abril': '04',
                'mayo': '05', 'junio': '06', 'julio': '07', 'agosto': '08',
                'septiembre': '09', 'octubre': '10', 'noviembre': '11', 'diciembre': '12'
            }

            today = datetime.now().date()
            current_year = today.year

            # Find month sections
            for month_name, month_num in month_map.items():
                # Find month heading
                month_elem = soup.find(string=lambda text: text and month_name.capitalize() in text)

                if not month_elem:
                    continue

                parent = month_elem.parent

                # Get following <p> elements until next month
                current = parent.next_sibling
                while current:
                    if hasattr(current, 'name'):
                        if current.name == 'p':
                            text = current.get_text(strip=True)

                            # Parse event from text
                            # Format: "•13-14 de febrero – Inca:Fireta dels Enamorats| Feria artesanal."
                            if '–' in text or '-' in text:
                                parts = re.split(r'[–-]', text, maxsplit=1)
                                if len(parts) >= 2:
                                    # Extract date and rest
                                    date_part = parts[0].strip().lstrip('•').strip()
                                    rest = parts[1].strip()

                                    # Extract location and title
                                    if ':' in rest:
                                        location_title = rest.split(':', 1)
                                        location = location_title[0].strip()
                                        title_desc = location_title[1].strip() if len(location_title) > 1 else rest

                                        if '|' in title_desc:
                                            title_parts = title_desc.split('|')
                                            title = title_parts[0].strip()
                                            description = title_parts[1].strip() if len(title_parts) > 1 else ""
                                        else:
                                            title = title_desc
                                            description = ""
                                    else:
                                        title = rest
                                        location = ""
                                        description = ""

                                    # Build date (use day 15 as middle of month if not specified)
                                    year = current_year
                                    if int(month_num) < today.month:
                                        year = current_year + 1

                                    date_str = f"{year}-{month_num}-15"

                                    # Filter future events
                                    try:
                                        event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                                        if event_date < today:
                                            continue
                                    except:
                                        continue

                                    if title and len(title) > 3:
                                        municipality = normalize_municipality(location) or location or "Mallorca"

                                        events.append({
                                            "source": "inmallorca_magazine",
                                            "title": title[:100],
                                            "description": description[:200],
                                            "start_date": date_str,
                                            "municipality": municipality,
                                            "location": location,
                                            "category": "FESTIVAL",
                                            "is_free": True,
                                            "price": None,
                                            "website_url": url,
                                        })

                        elif current.name in ['h1', 'h2', 'strong']:
                            # Next section - check if it's another month
                            next_text = current.get_text(strip=True).lower()
                            if any(m in next_text for m in month_map.keys()):
                                break

                    current = current.next_sibling

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_bravobaleares() -> List[Dict]:
    """Scrape Bravo Baleares events (currently returns empty - site is JavaScript-heavy)."""
    events = []
    url = "https://bravobaleares.com/"

    try:
        print(f"   🔍 Scraping: bravo baleares...")

        # Note: This site is heavily JavaScript-based and events are loaded dynamically
        # Would require complex Playwright interaction or API access
        # Returning empty for now

        print(f"      ⚠️  Site requires complex JavaScript interaction - skipping for now")
        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_mallorca_com_gastronomia() -> List[Dict]:
    """Scrape mallorca.com gastronomic events."""
    events = []
    url = "https://www.mallorca.com/es/eventos/gastronomia"

    try:
        print(f"   🔍 Scraping (Playwright): mallorca.com gastronomía...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="load", timeout=30000)
            page.wait_for_timeout(3000)

            # Click "Más" button to load all events
            click_count = 0
            max_clicks = 10
            while click_count < max_clicks:
                try:
                    load_more = page.query_selector('button:has-text("Más")')
                    if load_more and load_more.is_visible():
                        load_more.click()
                        page.wait_for_timeout(2000)
                        click_count += 1
                    else:
                        break
                except:
                    break

            html_content = page.content()
            browser.close()

            soup = BeautifulSoup(html_content, 'html.parser')

            # Find event links
            all_links = soup.select('a[href*="/eventos/"]')
            event_links = [link for link in all_links if '/eventos/gastronomia' not in link.get('href', '')]

            today = datetime.now().date()

            for link in event_links:
                href = link.get('href', '')

                if not href or href.endswith('/eventos') or href.endswith('/eventos/'):
                    continue

                # Extract title (from h3 to avoid category/location text)
                title_elem = link.select_one('h3')
                title = title_elem.get_text(strip=True) if title_elem else ""
                if not title or len(title) < 3:
                    continue

                # Extract date from URL (pattern: /eventos/title-YYYY-MM-DD or /eventos/title-YYYY-hash)
                date_str = None
                url_date_match = re.search(r'-(\d{4}-\d{2}-\d{2})', href)
                if url_date_match:
                    date_str = url_date_match.group(1)
                else:
                    # Try to find year and assume mid-year
                    year_match = re.search(r'-(\d{4})-', href)
                    if year_match:
                        year = year_match.group(1)
                        # Use September as default month
                        date_str = f"{year}-09-15"

                if not date_str:
                    continue

                # Filter future events only
                try:
                    event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                    if event_date < today:
                        continue
                except:
                    continue

                # Extract location from title (usually last word or capitalized word)
                location_match = re.search(r'(Palma|Inca|Manacor|Alcúdia|Pollença|Sóller|Calvià|Fornalutx|Valldemossa|Bunyola|[A-Z][a-záéíóú]+)(?:\s|$)', title)
                location = location_match.group(1) if location_match else ""
                municipality = normalize_municipality(location) or "Mallorca"

                # Build full URL
                event_url = href if href.startswith('http') else f"https://www.mallorca.com{href}"

                events.append({
                    "source": "mallorca.com_gastro",
                    "title": title[:100],
                    "description": "Evento gastronómico",
                    "start_date": date_str,
                    "municipality": municipality,
                    "location": location,
                    "category": "GASTRONOMY",
                    "is_free": None,
                    "price": "Consultar web",
                    "website_url": event_url,
                })

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_eventbrite_gastronomia() -> List[Dict]:
    """Scrape Eventbrite food & drink events in Balearic Islands (stub - returns empty)."""
    events = []

    try:
        print(f"   🔍 Scraping: eventbrite gastronomía...")

        # Note: Eventbrite has complex dynamic loading and requires
        # detailed interaction. Implementing a robust scraper would need:
        # - Complex Playwright interactions
        # - Handling of dynamic content loading
        # - Cookie/consent management
        # - Anti-scraping bypass
        # Returning empty for now

        print(f"      ⚠️  Eventbrite requires complex interaction - skipping for now")
        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def scrape_thecalendarmallorca() -> List[Dict]:
    """Scrape The Calendar Mallorca events."""
    events = []
    url = "https://thecalendarmallorca.com/events/"

    # Category mapping from The Calendar to our enum
    CATEGORY_MAP = {
        'arts-culture': 'CULTURE',
        'music-concerts': 'CONCERT',
        'eat-drink': 'GASTRONOMY',
        'nightlife-parties': 'NIGHTLIFE',
        'sports': 'SPORT',
        'local-markets': 'MARKET',
        'living-traditions': 'FESTIVAL',
        'community-business': 'CULTURE',
        'workshops-classes': 'CULTURE',
        'better-living': 'CULTURE',
        'camps-clinics': 'FAMILY',
        'kids-things': 'FAMILY',
    }

    try:
        print(f"   🔍 Scraping (Playwright): thecalendarmallorca...")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="networkidle", timeout=60000)
            page.wait_for_timeout(5000)

            # Scroll to load more events
            for _ in range(5):
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                page.wait_for_timeout(2000)

            html_content = page.content()
            soup = BeautifulSoup(html_content, 'html.parser')

            # Find all event containers with data-url
            event_containers = soup.find_all('div', {'data-url': True})

            # Filter to get only actual event pages (not categories)
            event_urls = set()
            for container in event_containers:
                event_url = container.get('data-url', '')
                # Individual events: /events/event-name/
                # Exclude: /event-category/, /tag/, /author/
                if '/events/' in event_url and event_url.count('/') >= 5:
                    if not any(x in event_url for x in ['/event-category/', '/tag/', '/author/', '/page/']):
                        event_urls.add(event_url)

            today = datetime.now().date()

            # Limit to avoid too many requests (sample 30 events)
            for event_url in list(event_urls)[:30]:
                try:
                    page.goto(event_url, wait_until="networkidle", timeout=30000)
                    page.wait_for_timeout(2000)

                    event_html = page.content()
                    event_soup = BeautifulSoup(event_html, 'html.parser')

                    # Extract title
                    title_elem = event_soup.select_one('h1') or event_soup.select_one('.entry-title')
                    title = title_elem.get_text(strip=True) if title_elem else ""

                    if not title or len(title) < 3:
                        continue

                    # Extract description from meta tag
                    og_desc = event_soup.find('meta', property='og:description')
                    description = og_desc.get('content', '')[:200] if og_desc else title

                    # Extract image from meta tag
                    og_image = event_soup.find('meta', property='og:image')
                    image_url = og_image.get('content', '') if og_image else None

                    # Infer category from title and description keywords
                    category = 'CULTURE'  # default
                    text_to_check = (title + ' ' + description).lower()

                    # Keyword-based category detection
                    if any(word in text_to_check for word in ['concert', 'music', 'dj', 'live music', 'band', 'jazz', 'rock']):
                        category = 'CONCERT'
                    elif any(word in text_to_check for word in ['nightlife', 'party', 'club', 'night club', 'disco']):
                        category = 'NIGHTLIFE'
                    elif any(word in text_to_check for word in ['food', 'gastronomic', 'wine', 'tapas', 'dinner', 'brunch', 'restaurant', 'culinary']):
                        category = 'GASTRONOMY'
                    elif any(word in text_to_check for word in ['market', 'flea market', 'craft market', 'farmers market']):
                        category = 'MARKET'
                    elif any(word in text_to_check for word in ['sport', 'race', 'run', 'marathon', 'triathlon', 'cycling', 'football', 'basketball']):
                        category = 'SPORT'
                    elif any(word in text_to_check for word in ['festival', 'feria', 'fair', 'celebration', 'fiesta']):
                        category = 'FESTIVAL'
                    elif any(word in text_to_check for word in ['family', 'familia', 'kids', 'children', 'niños', 'infantil', 'familiar']):
                        category = 'FAMILY'

                    # Extract date from span elements (format: "Fri 25, September, 2026")
                    date_str = None
                    for span in event_soup.find_all('span'):
                        text = span.get_text(strip=True)
                        # Look for pattern like "Fri 25, September, 2026"
                        date_match = re.search(r'\w{3}\s+(\d+),\s+(\w+),\s+(\d{4})', text)
                        if date_match:
                            day, month_name, year = date_match.groups()
                            # Convert month name to number
                            month_map = {
                                'january': '01', 'february': '02', 'march': '03', 'april': '04',
                                'may': '05', 'june': '06', 'july': '07', 'august': '08',
                                'september': '09', 'october': '10', 'november': '11', 'december': '12'
                            }
                            month = month_map.get(month_name.lower(), '01')
                            date_str = f"{year}-{month}-{day.zfill(2)}"
                            break

                    if not date_str:
                        # Skip events without dates
                        continue

                    # Filter past events
                    try:
                        event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                        if event_date < today:
                            continue
                    except:
                        continue

                    # Extract venue if available
                    venue = ""
                    venue_elem = event_soup.select_one('[class*="venue"], [class*="location"]')
                    if venue_elem:
                        venue = venue_elem.get_text(strip=True)

                    events.append({
                        "source": "thecalendarmallorca",
                        "title": title[:100],
                        "description": description,
                        "start_date": date_str,
                        "municipality": "Palma",  # Most events are in Palma
                        "location": venue[:50] if venue else "",
                        "category": category,
                        "is_free": None,
                        "price": "Consultar web",
                        "website_url": event_url,
                        "image_url": image_url,
                    })

                except Exception as e:
                    # Skip individual event errors
                    continue

            browser.close()

        print(f"      ✅ Found {len(events)} events")

    except Exception as e:
        print(f"      ❌ Error: {e}")

    return events


def deduplicate_events(events: List[Dict]) -> List[Dict]:
    """Remove duplicate events based on title + date."""
    seen: Set[tuple] = set()
    unique_events = []

    for event in events:
        # Create key from title (lowercase) + date
        key = (event["title"].lower().strip(), event["start_date"])

        if key not in seen:
            seen.add(key)
            unique_events.append(event)

    duplicates_removed = len(events) - len(unique_events)
    if duplicates_removed > 0:
        print(f"   ℹ️  Removed {duplicates_removed} duplicate events")

    return unique_events


def scrape_all_sources() -> List[Dict]:
    """Scrape all event sources."""
    print("🌐 Scraping event sources...")
    print("=" * 60)

    all_events = []

    # Use specific scrapers for working sources
    all_events.extend(scrape_auditorium_palma())
    all_events.extend(scrape_firesifestes())
    all_events.extend(scrape_mallorcafiestas())
    all_events.extend(scrape_mallorcamusicmagazine())
    all_events.extend(scrape_conciertos_club())
    all_events.extend(scrape_resident_advisor())
    all_events.extend(scrape_wodira())
    all_events.extend(scrape_mallorca_com())
    all_events.extend(scrape_ime_palma())
    all_events.extend(scrape_wepartynow())
    all_events.extend(scrape_inmallorca_magazine())
    all_events.extend(scrape_bravobaleares())
    all_events.extend(scrape_mallorca_com_gastronomia())
    all_events.extend(scrape_eventbrite_gastronomia())
    all_events.extend(scrape_thecalendarmallorca())

    # Deduplicate
    all_events = deduplicate_events(all_events)

    print("=" * 60)
    print(f"✅ Total unique events: {len(all_events)}")

    return all_events


def generate_review_html(events: List[Dict]):
    """Generate interactive HTML review page."""
    print(f"📝 Generating review page with {len(events)} events...")

    # Add unique IDs
    for event in events:
        event["id"] = generate_event_id(
            event["source"],
            event["title"],
            event["start_date"]
        )

    # Sort by date
    events.sort(key=lambda x: x["start_date"])

    html_content = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Web Events Review - Mallorca</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #f5f5f5;
            padding: 20px;
            padding-bottom: 100px;
        }}
        .header {{
            background: white;
            padding: 30px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }}
        h1 {{ color: #1a1a1a; margin-bottom: 10px; }}
        .stats {{
            display: flex;
            gap: 20px;
            margin-top: 20px;
            flex-wrap: wrap;
        }}
        .stat {{
            background: #f0f0f0;
            padding: 15px 20px;
            border-radius: 8px;
        }}
        .stat-label {{ font-size: 12px; color: #666; text-transform: uppercase; }}
        .stat-value {{ font-size: 24px; font-weight: bold; color: #1a1a1a; margin-top: 5px; }}
        .stat.approved .stat-value {{ color: #10b981; }}
        .stat.rejected .stat-value {{ color: #ef4444; }}
        .events-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
            gap: 20px;
        }}
        .event-card {{
            background: white;
            border-radius: 12px;
            padding: 20px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            transition: transform 0.2s, box-shadow 0.2s;
        }}
        .event-card:hover {{
            transform: translateY(-2px);
            box-shadow: 0 4px 16px rgba(0,0,0,0.15);
        }}
        .event-card.approved {{ border: 3px solid #10b981; }}
        .event-card.rejected {{ opacity: 0.5; border: 3px solid #ef4444; }}
        .event-title {{ font-size: 18px; font-weight: 600; margin-bottom: 10px; }}
        .event-meta {{
            display: flex;
            flex-direction: column;
            gap: 8px;
            margin-bottom: 15px;
        }}
        .event-meta-item {{ font-size: 14px; color: #666; }}
        .category-badge {{
            display: inline-block;
            padding: 4px 12px;
            background: #e5e7eb;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
        }}
        .category-CONCERT {{ background: #dbeafe; color: #1e40af; }}
        .category-FESTIVAL {{ background: #fce7f3; color: #9f1239; }}
        .category-CULTURE {{ background: #f3e8ff; color: #6b21a8; }}
        .category-SPORT {{ background: #d1fae5; color: #065f46; }}
        .source-badge {{
            display: inline-block;
            padding: 2px 8px;
            background: #f3f4f6;
            border-radius: 6px;
            font-size: 11px;
            color: #6b7280;
        }}
        .event-actions {{ display: flex; gap: 10px; }}
        button {{
            flex: 1;
            padding: 12px;
            border: none;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }}
        .btn-approve {{ background: #10b981; color: white; }}
        .btn-approve:hover {{ background: #059669; }}
        .btn-reject {{ background: #ef4444; color: white; }}
        .btn-reject:hover {{ background: #dc2626; }}
        .footer {{
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: white;
            padding: 20px;
            box-shadow: 0 -2px 8px rgba(0,0,0,0.1);
            display: flex;
            justify-content: center;
        }}
        .btn-download {{
            background: #3b82f6;
            color: white;
            padding: 16px 40px;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }}
        .btn-download:hover {{ background: #2563eb; }}
        .btn-download:disabled {{ background: #9ca3af; cursor: not-allowed; }}
    </style>
</head>
<body>
    <div class="header">
        <h1>🌐 Web Events Review - Mallorca</h1>
        <p>Eventos ordenados por fecha</p>
        <div class="stats">
            <div class="stat">
                <div class="stat-label">Total</div>
                <div class="stat-value" id="total-count">{len(events)}</div>
            </div>
            <div class="stat approved">
                <div class="stat-label">Aprobados</div>
                <div class="stat-value" id="approved-count">0</div>
            </div>
            <div class="stat rejected">
                <div class="stat-label">Rechazados</div>
                <div class="stat-value" id="rejected-count">0</div>
            </div>
        </div>
        <div style="text-align:center;margin:12px 0;display:flex;gap:12px;justify-content:center">
            <button onclick="approveAll()" style="background:#22c55e;color:white;border:none;padding:12px 28px;border-radius:8px;font-size:16px;cursor:pointer;font-weight:bold">✓ Aprobar todos</button>
            <button onclick="rejectAll()" style="background:#ef4444;color:white;border:none;padding:12px 28px;border-radius:8px;font-size:16px;cursor:pointer;font-weight:bold">✗ Rechazar todos</button>
        </div>
    </div>
    <div class="events-grid" id="events-grid"></div>
    <div class="footer">
        <button class="btn-download" id="download-btn" disabled>
            💾 Guardar seleccion (0)
        </button>
    </div>
    <script>
        const eventsData = {json.dumps(events, ensure_ascii=False, indent=2)};
        let approvedEvents = new Set();
        let rejectedEvents = new Set();

        function renderEvents() {{
            const grid = document.getElementById('events-grid');
            grid.innerHTML = '';
            eventsData.forEach(event => {{
                const card = document.createElement('div');
                card.className = 'event-card';
                card.id = `event-${{event.id}}`;
                if (approvedEvents.has(event.id)) card.classList.add('approved');
                else if (rejectedEvents.has(event.id)) card.classList.add('rejected');

                const priceText = event.is_free ? 'Gratis' : (event.price || 'Precio desconocido');
                card.innerHTML = `
                    <div class="event-title">${{event.title}}</div>
                    <div class="event-meta">
                        <div class="event-meta-item">📅 ${{event.start_date}}</div>
                        <div class="event-meta-item">
                            <span class="category-badge category-${{event.category}}">${{event.category}}</span>
                        </div>
                        <div class="event-meta-item">📍 ${{event.municipality}}</div>
                        <div class="event-meta-item">💰 ${{priceText}}</div>
                        <div class="event-meta-item">
                            <span class="source-badge">${{event.source}}</span>
                        </div>
                    </div>
                    <div class="event-actions">
                        <button class="btn-approve" onclick="approveEvent('${{event.id}}')">✓ Aprobar</button>
                        <button class="btn-reject" onclick="rejectEvent('${{event.id}}')">✗ Rechazar</button>
                    </div>
                `;
                grid.appendChild(card);
            }});
            updateStats();
        }}

        function approveEvent(eventId) {{
            approvedEvents.add(eventId);
            rejectedEvents.delete(eventId);
            updateCard(eventId);
            updateStats();
        }}

        function rejectEvent(eventId) {{
            rejectedEvents.add(eventId);
            approvedEvents.delete(eventId);
            updateCard(eventId);
            updateStats();
        }}

        function updateCard(eventId) {{
            const card = document.getElementById(`event-${{eventId}}`);
            card.className = 'event-card';
            if (approvedEvents.has(eventId)) card.classList.add('approved');
            else if (rejectedEvents.has(eventId)) card.classList.add('rejected');
        }}

        function updateStats() {{
            document.getElementById('approved-count').textContent = approvedEvents.size;
            document.getElementById('rejected-count').textContent = rejectedEvents.size;
            const downloadBtn = document.getElementById('download-btn');
            downloadBtn.disabled = approvedEvents.size === 0;
            downloadBtn.textContent = `💾 Guardar seleccion (${{approvedEvents.size}})`;
        }}

        function downloadApproved() {{
            const approved = eventsData.filter(e => approvedEvents.has(e.id));
            fetch('http://localhost:8765/save', {{
                method: 'POST',
                headers: {{'Content-Type': 'application/json'}},
                body: JSON.stringify(approved)
            }}).then(r=>r.json()).then(d=>{{
                if(d.ok) alert('Guardado. Ejecuta el Comando 2 para subir.');
            }}).catch(()=>alert('Error: asegurate de que el script esta corriendo.'));
        }}

        function approveAll() {{
            eventsData.forEach(event => {{
                approvedEvents.add(event.id);
                rejectedEvents.delete(event.id);
                updateCard(event.id);
            }});
            updateStats();
        }}

        function rejectAll() {{
            eventsData.forEach(event => {{
                rejectedEvents.add(event.id);
                approvedEvents.delete(event.id);
                updateCard(event.id);
            }});
            updateStats();
        }}

        document.getElementById('download-btn').addEventListener('click', downloadApproved);
        renderEvents();
    </script>
</body>
</html>"""

    with open(REVIEW_HTML, 'w', encoding='utf-8') as f:
        f.write(html_content)

    print(f"✅ Review page generated: {REVIEW_HTML}")
    print(f"\n📂 Open: file://{REVIEW_HTML.absolute()}")


def save_approved_events(events: List[Dict]):
    """Save approved events to JSON file."""
    output_file = TOOLS_DIR / "approved_events.json"

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(events, f, ensure_ascii=False, indent=2)

    print(f"\n✅ Eventos guardados en: {output_file}")
    print(f"📄 Archivo: {output_file.name} ({len(events)} eventos)")


def run_review_server(output_path):
    """Run local HTTP server for event review."""
    saved = _th.Event()

    class H(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def do_GET(self):
            if self.path in ('/', '/review.html'):
                d = open(output_path, 'rb').read()
                self.send_response(200)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.end_headers()
                self.wfile.write(d)
            else:
                self.send_response(404)
                self.end_headers()

        def do_OPTIONS(self):
            self.send_response(200)
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Allow-Methods', 'POST')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type')
            self.end_headers()

        def do_POST(self):
            if self.path == '/save':
                import json as j
                body = self.rfile.read(int(self.headers['Content-Length']))
                approved = j.loads(body)  # Already filtered by frontend
                out = str(output_path).replace('review.html', 'approved_events.json')
                open(out, 'w', encoding='utf-8').write(j.dumps(approved, ensure_ascii=False, indent=2))
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(b'{"ok":true}')
                print(f"\n✅ Guardados {len(approved)} eventos en approved_events.json")
                saved.set()
            else:
                self.send_response(404)
                self.end_headers()

    srv = HTTPServer(('localhost', 8765), H)
    _th.Thread(target=srv.serve_forever, daemon=True).start()
    print("\n🌐 Servidor iniciado en http://localhost:8765")
    print("   Revisa los eventos y pulsa 'Descargar aprobados' cuando termines.")
    print("   Presiona Ctrl+C para salir.")
    time.sleep(0.5)
    webbrowser.open('http://localhost:8765')

    try:
        saved.wait()
        srv.shutdown()
        print(f"\n✅ approved_events.json guardado.")
        print("\n" + "=" * 60)
        print("🎉 Siguiente paso:")
        print("   python3 tools/upload_approved_events.py")
        print("=" * 60)
    except KeyboardInterrupt:
        print("\n⚠️  Servidor detenido. No se guardaron eventos.")
        srv.shutdown()


def main():
    import sys

    # Check for --auto-approve flag
    auto_approve = '--auto-approve' in sys.argv or '--auto' in sys.argv

    print("🌐 Mallorca Web Events Scraper (Interactive)")
    print("=" * 60)

    events = scrape_all_sources()

    if not events:
        print("⚠️  No events found")
        return

    # Generate HTML for reference (optional)
    generate_review_html(events)

    # Auto-approve mode
    if auto_approve:
        print("\n🤖 Modo auto-aprobación activado")
        print(f"✅ Aprobando automáticamente {len(events)} eventos...")
        approved = events
        save_approved_events(approved)

        print("\n" + "=" * 60)
        print("🎉 Listo! Ahora ejecuta:")
        print("   python3 tools/upload_approved_events.py")
        print("=" * 60)
        return

    # Interactive approval via HTTP server
    run_review_server(REVIEW_HTML)


if __name__ == "__main__":
    main()
