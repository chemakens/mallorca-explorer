#!/usr/bin/env python3
"""
Ticketmaster Events Sync to Firestore
--------------------------------------
Fetches events from Ticketmaster Discovery API for Mallorca/Baleares
and syncs them to Firestore (upsert, preserving manual events).

Requirements:
- pip install firebase-admin requests python-dotenv

Firestore credentials:
1. Go to Firebase Console → Project Settings → Service Accounts
2. Click "Generate new private key"
3. Save as serviceAccountKey.json in project root
   OR set GOOGLE_APPLICATION_CREDENTIALS env var to the JSON path
"""

import os
import sys
import json
import logging
from datetime import datetime, timezone
from typing import Dict, List, Optional
from pathlib import Path

try:
    import requests
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install firebase-admin requests")
    sys.exit(1)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# Paths
PROJECT_ROOT = Path(__file__).parent.parent
LOCAL_PROPERTIES = PROJECT_ROOT / "local.properties"
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"

# Ticketmaster API
TICKETMASTER_API_URL = "https://app.ticketmaster.com/discovery/v2/events.json"

# Category mapping: Ticketmaster → Firestore
CATEGORY_MAP = {
    "Music": "CONCERT",
    "Sports": "SPORT",
    "Arts & Theatre": "CULTURE",
    "Family": "FESTIVAL",
    "Festival": "FESTIVAL",
    "Miscellaneous": "CULTURE",
}

# EventCategory enum from Event.kt
VALID_CATEGORIES = {"MARKET", "FESTIVAL", "CONCERT", "CULTURE", "SPORT", "NIGHTLIFE"}


def read_api_key() -> str:
    """Read TICKETMASTER_API_KEY from local.properties."""
    if not LOCAL_PROPERTIES.exists():
        logger.error(f"❌ local.properties not found at {LOCAL_PROPERTIES}")
        sys.exit(1)

    with open(LOCAL_PROPERTIES) as f:
        for line in f:
            line = line.strip()
            if line.startswith("TICKETMASTER_API_KEY="):
                key = line.split("=", 1)[1].strip()
                if key:
                    return key

    logger.error("❌ TICKETMASTER_API_KEY not found in local.properties")
    logger.info("   Add: TICKETMASTER_API_KEY=your_key_here")
    sys.exit(1)


def init_firestore() -> firestore.Client:
    """Initialize Firestore with service account credentials."""
    cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", SERVICE_ACCOUNT_KEY)

    if not Path(cred_path).exists():
        logger.error(f"❌ Firebase service account key not found at {cred_path}")
        logger.info("\n📋 To get credentials:")
        logger.info("   1. Go to: https://console.firebase.google.com/")
        logger.info("   2. Select your project")
        logger.info("   3. Project Settings → Service Accounts")
        logger.info("   4. Click 'Generate new private key'")
        logger.info(f"   5. Save as: {SERVICE_ACCOUNT_KEY}")
        logger.info("\n   OR set env var: export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json")
        sys.exit(1)

    try:
        cred = credentials.Certificate(str(cred_path))
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        logger.error(f"❌ Failed to initialize Firestore: {e}")
        sys.exit(1)


def fetch_ticketmaster_events(api_key: str, limit: int = 200) -> List[Dict]:
    """
    Fetch events from Ticketmaster Discovery API for Mallorca/Baleares.

    API Docs: https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
    """
    logger.info("🔍 Fetching events from Ticketmaster API...")

    params = {
        "apikey": api_key,
        "countryCode": "ES",
        "stateCode": "IB",  # Illes Balears
        "size": limit,
        "sort": "date,asc",
    }

    try:
        response = requests.get(TICKETMASTER_API_URL, params=params, timeout=30)
        response.raise_for_status()
        data = response.json()

        embedded = data.get("_embedded", {})
        events = embedded.get("events", [])

        logger.info(f"✅ Fetched {len(events)} events from Ticketmaster")
        return events

    except requests.exceptions.RequestException as e:
        logger.error(f"❌ Failed to fetch from Ticketmaster: {e}")
        return []


def map_category(tm_classification: List[Dict]) -> str:
    """Map Ticketmaster classification to Firestore category."""
    for item in tm_classification:
        segment = item.get("segment", {}).get("name", "")
        mapped = CATEGORY_MAP.get(segment)
        if mapped:
            return mapped

    # Default fallback
    return "CULTURE"


def extract_municipality(venue: Dict) -> str:
    """Extract municipality from Ticketmaster venue."""
    city = venue.get("city", {}).get("name", "")
    if city:
        return city

    # Fallback: state name (should be "Illes Balears")
    state = venue.get("state", {}).get("name", "")
    return state or "Mallorca"


def parse_date(date_str: str) -> Optional[str]:
    """
    Parse Ticketmaster date to ISO format (YYYY-MM-DD).
    Ticketmaster format: "2026-09-15T19:00:00Z"
    """
    if not date_str:
        return None

    try:
        # Remove timezone suffix and parse
        dt = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        return dt.strftime("%Y-%m-%d")
    except ValueError:
        logger.warning(f"⚠️  Invalid date format: {date_str}")
        return None


def map_ticketmaster_event(tm_event: Dict) -> Optional[Dict]:
    """Map Ticketmaster event to Firestore event schema."""
    try:
        # Required fields
        event_id = tm_event.get("id")
        name = tm_event.get("name")

        if not event_id or not name:
            logger.warning("⚠️  Skipping event without id or name")
            return None

        # Dates
        dates = tm_event.get("dates", {})
        start_info = dates.get("start", {})
        start_date = parse_date(start_info.get("dateTime") or start_info.get("localDate"))

        if not start_date:
            logger.warning(f"⚠️  Skipping event without start date: {name}")
            return None

        # Classification → category
        classifications = tm_event.get("classifications", [])
        category = map_category(classifications)

        # Venue → location
        venues = tm_event.get("_embedded", {}).get("venues", [])
        venue = venues[0] if venues else {}

        municipality = extract_municipality(venue)
        address = venue.get("address", {}).get("line1")

        # Price
        price_ranges = tm_event.get("priceRanges", [])
        is_free = not price_ranges
        price = None
        if price_ranges:
            min_price = price_ranges[0].get("min")
            max_price = price_ranges[0].get("max")
            currency = price_ranges[0].get("currency", "EUR")
            if min_price and max_price:
                price = f"€{min_price}-{max_price}"
            elif min_price:
                price = f"€{min_price}"

        # Images
        images = tm_event.get("images", [])
        image_url = images[0].get("url") if images else None

        # URL
        website_url = tm_event.get("url")

        # Description (Ticketmaster often doesn't provide one)
        info = tm_event.get("info", "")
        description = info[:500] if info else f"Event in {municipality}"

        # Build Firestore document
        # ID: prefix with "tm-" to distinguish from manual events
        firestore_event = {
            "id": f"tm-{event_id}",
            "title": name,
            "title_es": name,  # Ticketmaster doesn't provide translations
            "title_de": "",
            "title_ru": "",
            "title_zh": "",
            "description": description,
            "description_es": description,
            "description_de": "",
            "description_ru": "",
            "description_zh": "",
            "category": category,
            "start_date": start_date,
            "end_date": None,  # Ticketmaster events are typically single-day
            "municipality": municipality,
            "address": address,
            "is_free": is_free,
            "price": price,
            "image_url": image_url,
            "is_recurring": False,  # Ticketmaster events are single occurrences
            "recurring_day_of_week": None,
            "website_url": website_url,
        }

        return firestore_event

    except Exception as e:
        logger.warning(f"⚠️  Failed to map event: {e}")
        return None


def sync_to_firestore(db: firestore.Client, events: List[Dict]) -> int:
    """
    Upsert events to Firestore (overwrite existing Ticketmaster events only).
    """
    if not events:
        logger.info("ℹ️  No events to sync")
        return 0

    logger.info(f"📤 Syncing {len(events)} events to Firestore...")

    collection = db.collection("events")
    synced = 0

    for event in events:
        try:
            doc_id = event["id"]
            # Remove 'id' from document data (Firestore uses it as doc ID)
            event_data = {k: v for k, v in event.items() if k != "id"}

            # Upsert: set will create or overwrite
            collection.document(doc_id).set(event_data)
            synced += 1
            logger.debug(f"   ✓ {doc_id}: {event['title']}")

        except Exception as e:
            logger.warning(f"⚠️  Failed to sync event {event.get('id')}: {e}")

    logger.info(f"✅ Successfully synced {synced}/{len(events)} events to Firestore")
    return synced


def main():
    logger.info("🚀 Ticketmaster → Firestore Event Sync")
    logger.info("=" * 60)

    # Read API key
    api_key = read_api_key()
    logger.info(f"✅ Loaded Ticketmaster API key from {LOCAL_PROPERTIES.name}")

    # Initialize Firestore
    db = init_firestore()
    logger.info("✅ Connected to Firestore")

    # Fetch from Ticketmaster
    tm_events = fetch_ticketmaster_events(api_key)

    if not tm_events:
        logger.warning("⚠️  No events fetched. Exiting.")
        return

    # Map to Firestore schema
    logger.info("🔄 Mapping events to Firestore schema...")
    firestore_events = []
    for tm_event in tm_events:
        mapped = map_ticketmaster_event(tm_event)
        if mapped:
            firestore_events.append(mapped)

    logger.info(f"✅ Mapped {len(firestore_events)} events")

    # Sync to Firestore
    synced = sync_to_firestore(db, firestore_events)

    logger.info("=" * 60)
    logger.info(f"🎉 Done! {synced} events synced to Firestore")
    logger.info(f"   Events will appear in the app after next sync (automatic)")


if __name__ == "__main__":
    main()
