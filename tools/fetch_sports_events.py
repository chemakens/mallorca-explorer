#!/usr/bin/env python3
"""
Fetch Sports Events from Consell de Mallorca
---------------------------------------------
Scrapes ALL events from https://esports.conselldemallorca.es/es/agenda
using Playwright to handle JavaScript-based pagination.

Generates JSON compatible with approved_events.json format.
"""

import sys
import json
import re
import time
from pathlib import Path
from datetime import datetime

try:
    from bs4 import BeautifulSoup
    from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeout
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install beautifulsoup4 lxml playwright")
    print("   playwright install chromium")
    sys.exit(1)

# Paths
TOOLS_DIR = Path(__file__).parent
OUTPUT_FILE = TOOLS_DIR / "sports_events.json"

# Base URL
BASE_URL = "https://esports.conselldemallorca.es"
AGENDA_URL = f"{BASE_URL}/es/agenda"

# Today's date for filtering
TODAY = datetime.now().date()


def parse_date(date_str: str) -> str:
    """Parse date from DD/MM/YYYY to YYYY-MM-DD."""
    if not date_str:
        return None

    try:
        # Format: "06/09/2026" or "06/09/2026 - 06/09/2026"
        date_part = date_str.split('-')[0].strip()
        day, month, year = date_part.split('/')
        return f"{year}-{month.zfill(2)}-{day.zfill(2)}"
    except Exception:
        return None


def parse_date_range(date_str: str) -> tuple:
    """Parse date range from 'DD/MM/YYYY - DD/MM/YYYY' to (start, end)."""
    if not date_str or '-' not in date_str:
        single_date = parse_date(date_str)
        return single_date, single_date

    try:
        parts = date_str.split('-')
        start_date = parse_date(parts[0].strip())
        end_date = parse_date(parts[1].strip()) if len(parts) > 1 else start_date
        return start_date, end_date
    except Exception:
        return None, None


def extract_municipality(location: str) -> str:
    """Extract municipality from location string."""
    if not location:
        return "Mallorca"

    location_lower = location.lower()

    # Common municipalities
    municipalities = [
        "palma", "calvià", "manacor", "llucmajor", "inca", "alcúdia",
        "pollença", "sóller", "felanitx", "santanyí", "artà", "capdepera",
        "andratx", "petra", "binissalem", "marratxí", "muro", "sa pobla"
    ]

    for municipality in municipalities:
        if municipality in location_lower:
            return municipality.title()

    return "Mallorca"


def scrape_page_with_playwright(page) -> list:
    """Extract events from current page state."""
    events = []

    html = page.content()
    soup = BeautifulSoup(html, 'html.parser')

    # Find all event items
    event_items = soup.select('div.item')

    for item in event_items:
        # Title
        title_elem = item.select_one('h3.title')
        if not title_elem:
            continue
        title = title_elem.get_text(strip=True)

        # Location/subtitle
        location_elem = item.select_one('h4.subtitle')
        location = location_elem.get_text(strip=True) if location_elem else ""

        # Date
        date_elem = item.find('p')
        date_text = date_elem.get_text(strip=True) if date_elem else ""
        start_date, end_date = parse_date_range(date_text)

        if not start_date:
            continue

        # Filter future events only
        try:
            event_date = datetime.strptime(start_date, '%Y-%m-%d').date()
            if event_date < TODAY:
                continue
        except:
            continue

        # Link
        link_elem = item.select_one('a[href]')
        event_url = ""
        if link_elem:
            href = link_elem.get('href', '')
            if href.startswith('/'):
                event_url = BASE_URL + href
            elif href.startswith('http'):
                event_url = href
            else:
                event_url = BASE_URL + '/' + href

        # Build event
        event = {
            "source": "consell_esports",
            "title": title,
            "start_date": start_date,
            "end_date": end_date if end_date != start_date else None,
            "municipality": extract_municipality(location),
            "location": location,
            "category": "SPORT",
            "is_free": True,  # Most sports events are free
            "price": None,
            "website_url": event_url,
        }

        events.append(event)

    return events


def scrape_all_events() -> list:
    """Scrape all events from all pages using Playwright."""
    print("🏃 Fetching sports events from Consell de Mallorca...")
    print("=" * 60)
    print("Using Playwright to handle JavaScript pagination...")
    print()

    all_events = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()

        # Load first page
        print(f"📄 Loading page 1: {AGENDA_URL}")
        page.goto(AGENDA_URL, wait_until="networkidle", timeout=30000)
        time.sleep(1)

        # Scrape page 1
        events_p1 = scrape_page_with_playwright(page)
        all_events.extend(events_p1)
        print(f"  ✅ Found {len(events_p1)} future events on page 1")

        # Find pagination buttons
        pagination_buttons = page.query_selector_all('[onclick^="submitFormPaginador"]')

        # Extract page numbers from onclick
        page_numbers = set()
        for btn in pagination_buttons:
            onclick = btn.get_attribute('onclick')
            if onclick:
                match = re.search(r'submitFormPaginador\((\d+)\)', onclick)
                if match:
                    page_numbers.add(int(match.group(1)))

        # Remove page 1 (already scraped)
        page_numbers.discard(1)
        page_numbers = sorted(list(page_numbers))

        print(f"📊 Total pages detected: {len(page_numbers) + 1}")

        # Scrape remaining pages
        for page_num in page_numbers:
            print(f"\n📄 Loading page {page_num}...")

            # Find and click the page button
            page_btn = page.query_selector(f'[onclick="submitFormPaginador({page_num})"]')

            if page_btn:
                try:
                    # Wait for navigation after clicking
                    with page.expect_navigation(timeout=15000):
                        page_btn.click()

                    time.sleep(1)

                    # Scrape this page
                    events_pn = scrape_page_with_playwright(page)
                    all_events.extend(events_pn)
                    print(f"  ✅ Found {len(events_pn)} future events on page {page_num}")

                except Exception as e:
                    print(f"  ❌ Error loading page {page_num}: {e}")
                    continue
            else:
                print(f"  ⚠️  Page {page_num} button not found")

        browser.close()

    print("=" * 60)
    print(f"✅ Total future events scraped: {len(all_events)}")

    return all_events


def save_events(events: list):
    """Save events to JSON file."""
    print(f"\n💾 Saving events to {OUTPUT_FILE}...")

    # Sort by date
    events.sort(key=lambda x: x['start_date'])

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(events, f, ensure_ascii=False, indent=2)

    print(f"✅ Saved {len(events)} events to {OUTPUT_FILE.name}")


def main():
    print("🏃 Sports Events Scraper - Consell de Mallorca")
    print(f"📅 Filtering events from: {TODAY.strftime('%Y-%m-%d')}")
    print("=" * 60)
    print()

    # Scrape events from ALL pages
    events = scrape_all_events()

    if not events:
        print("\n⚠️  No future events found")
        return

    # Save to file
    save_events(events)

    # Show summary
    print("\n" + "=" * 60)
    print("📊 SUMMARY")
    print("=" * 60)
    print(f"Total events: {len(events)}")

    # Count by municipality
    from collections import Counter
    municipalities = Counter(e['municipality'] for e in events)
    print(f"\nBy municipality:")
    for municipality, count in municipalities.most_common(5):
        print(f"  • {municipality}: {count} events")

    # Show date range
    if events:
        first_date = events[0]['start_date']
        last_date = events[-1]['start_date']
        print(f"\nDate range:")
        print(f"  First event: {first_date}")
        print(f"  Last event: {last_date}")

    # Sample events
    print(f"\nSample events:")
    for event in events[:3]:
        print(f"  • {event['title']}")
        print(f"    Date: {event['start_date']}")
        print(f"    Location: {event['location']}")
        print(f"    Municipality: {event['municipality']}")
        print()

    print("=" * 60)
    print("✅ Done!")


if __name__ == "__main__":
    main()
