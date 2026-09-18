#!/usr/bin/env python3
"""
Eventbrite Events Sync to Firestore
------------------------------------
Fetches events from Eventbrite API for Mallorca
and syncs them to Firestore (upsert, preserving manual events).

Requirements:
- pip install firebase-admin requests

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

# Eventbrite API
EVENTBRITE_API_URL = "https://www.eventbriteapi.com/v3/events/search/"
MALLORCA_LAT = 39.6953
MALLORCA_LNG = 3.0176
SEARCH_RADIUS_KM = 50

# Category mapping: Eventbrite category_id → Firestore
CATEGORY_MAP = {
    "103": "CONCERT",           # Music
    "108": "SPORT",             # Sports & Fitness
    "105": "CULTURE",           # Performing & Visual Arts
    "107": "CULTURE",           # Science & Technology
    "102": "CULTURE",           # Science
    "104": "CULTURE",           # Film, Media & Entertainment
    "110": "FESTIVAL",          # Food & Drink
    "111": "FESTIVAL",          # Travel & Outdoor
    "113": "CULTURE",           # Community & Culture
}

# EventCategory enum from Event.kt
VALID_CATEGORIES = {"MARKET", "FESTIVAL", "CONCERT", "CULTURE", "SPORT", "NIGHTLIFE"}


def read_api_key() -> str:
    """Read EVENTBRITE_API_KEY from local.properties."""
    if not LOCAL_PROPERTIES.exists():
        logger.error(f"❌ local.properties not found at {LOCAL_PROPERTIES}")
        sys.exit(1)

    with open(LOCAL_PROPERTIES) as f:
        for line in f:
            line = line.strip()
            if line.startswith("EVENTBRITE_API_KEY="):
                key = line.split("=", 1)[1].strip()
                if key:
                    return key

    logger.error("❌ EVENTBRITE_API_KEY not found in local.properties")
    logger.info("   Add: EVENTBRITE_API_KEY=your_key_here")
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


def fetch_eventbrite_events(api_key: str, limit: int = 200) -> List[Dict]:
    """
    Fetch events from Eventbrite API for Mallorca.

    API Docs: https://www.eventbrite.com/platform/api
    """
    logger.info("🔍 Fetching events from Eventbrite API...")

    headers = {
        "Authorization": f"Bearer {api_key}",
    }

    params = {
        "location.address": "Mallorca",
        "location.latitude": MALLORCA_LAT,
        "location.longitude": MALLORCA_LNG,
        "location.within": f"{SEARCH_RADIUS_KM}km",
        "expand": "venue,category",
        "page_size": min(limit, 200),  # Eventbrite max is 200 per page
    }

    all_events = []
    continuation = None

    try:
        while True:
            if continuation:
                params["continuation"] = continuation

            response = requests.get(
                EVENTBRITE_API_URL,
                headers=headers,
                params=params,
                timeout=30
            )
            response.raise_for_status()
            data = response.json()

            events = data.get("events", [])
            all_events.extend(events)

            # Check for pagination
            pagination = data.get("pagination", {})
            continuation = pagination.get("continuation")
            has_more = pagination.get("has_more_items", False)

            if not has_more or not continuation or len(all_events) >= limit:
                break

        logger.info(f"✅ Fetched {len(all_events)} events from Eventbrite")
        return all_events

    except requests.exceptions.RequestException as e:
        logger.error(f"❌ Failed to fetch from Eventbrite: {e}")
        return []


def map_category(eb_category: Optional[Dict]) -> str:
    """Map Eventbrite category to Firestore category."""
    if not eb_category:
        return "CULTURE"

    category_id = eb_category.get("id", "")
    mapped = CATEGORY_MAP.get(category_id)

    if mapped:
        return mapped

    # Default fallback
    return "CULTURE"


def extract_municipality(venue: Optional[Dict]) -> str:
    """Extract municipality from Eventbrite venue."""
    if not venue:
        return "Mallorca"

    address = venue.get("address", {})
    city = address.get("city")
    if city:
        return city

    # Fallback to region
    region = address.get("region")
    if region:
        return region

    return "Mallorca"


def parse_datetime(datetime_str: str) -> Optional[str]:
    """
    Parse Eventbrite datetime to ISO date (YYYY-MM-DD).
    Eventbrite format: "2026-09-15T19:00:00"
    """
    if not datetime_str:
        return None

    try:
        # Parse ISO format
        dt = datetime.fromisoformat(datetime_str.replace("Z", "+00:00"))
        return dt.strftime("%Y-%m-%d")
    except ValueError:
        logger.warning(f"⚠️  Invalid date format: {datetime_str}")
        return None


def map_eventbrite_event(eb_event: Dict) -> Optional[Dict]:
    """Map Eventbrite event to Firestore event schema."""
    try:
        # Required fields
        event_id = eb_event.get("id")
        name = eb_event.get("name", {}).get("text")

        if not event_id or not name:
            logger.warning("⚠️  Skipping event without id or name")
            return None

        # Dates
        start = eb_event.get("start", {})
        end = eb_event.get("end", {})

        start_date = parse_datetime(start.get("local") or start.get("utc"))
        end_date = parse_datetime(end.get("local") or end.get("utc"))

        if not start_date:
            logger.warning(f"⚠️  Skipping event without start date: {name}")
            return None

        # Category
        category_obj = eb_event.get("category")
        category = map_category(category_obj)

        # Venue → location
        venue = eb_event.get("venue")
        municipality = extract_municipality(venue)

        address = None
        if venue:
            venue_address = venue.get("address", {})
            address_parts = [
                venue_address.get("address_1"),
                venue_address.get("address_2"),
            ]
            address = ", ".join(filter(None, address_parts)) or None

        # Price
        is_free = eb_event.get("is_free", True)
        price = None

        if not is_free:
            # Eventbrite doesn't always provide price in search results
            # Try to extract from ticket_availability
            ticket_availability = eb_event.get("ticket_availability", {})
            min_price = ticket_availability.get("minimum_ticket_price")
            max_price = ticket_availability.get("maximum_ticket_price")

            if min_price and max_price:
                currency = min_price.get("currency", "EUR")
                min_val = min_price.get("value", 0) / 100  # Eventbrite uses cents
                max_val = max_price.get("value", 0) / 100

                if min_val == max_val:
                    price = f"€{min_val:.2f}"
                else:
                    price = f"€{min_val:.2f}-€{max_val:.2f}"

        # Image
        logo = eb_event.get("logo")
        image_url = logo.get("url") if logo else None

        # URL
        website_url = eb_event.get("url")

        # Description
        description_obj = eb_event.get("description", {})
        description = description_obj.get("text", "")

        # Eventbrite descriptions can be very long, truncate
        if description:
            description = description[:500].strip()
            if len(description_obj.get("text", "")) > 500:
                description += "..."
        else:
            description = f"Event in {municipality}"

        # Build Firestore document
        # ID: prefix with "eb-" to distinguish from manual and Ticketmaster events
        firestore_event = {
            "id": f"eb-{event_id}",
            "title": name,
            "title_es": name,  # Eventbrite doesn't provide translations
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
            "end_date": end_date,  # May be same day or None
            "municipality": municipality,
            "address": address,
            "is_free": is_free,
            "price": price,
            "image_url": image_url,
            "is_recurring": False,  # Eventbrite events are single occurrences
            "recurring_day_of_week": None,
            "website_url": website_url,
        }

        return firestore_event

    except Exception as e:
        logger.warning(f"⚠️  Failed to map event: {e}")
        return None


def sync_to_firestore(db: firestore.Client, events: List[Dict]) -> int:
    """
    Upsert events to Firestore (overwrite existing Eventbrite events only).
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
    logger.info("🚀 Eventbrite → Firestore Event Sync")
    logger.info("=" * 60)

    # Read API key
    api_key = read_api_key()
    logger.info(f"✅ Loaded Eventbrite API key from {LOCAL_PROPERTIES.name}")

    # Initialize Firestore
    db = init_firestore()
    logger.info("✅ Connected to Firestore")

    # Fetch from Eventbrite
    eb_events = fetch_eventbrite_events(api_key)

    if not eb_events:
        logger.warning("⚠️  No events fetched. Exiting.")
        return

    # Map to Firestore schema
    logger.info("🔄 Mapping events to Firestore schema...")
    firestore_events = []
    for eb_event in eb_events:
        mapped = map_eventbrite_event(eb_event)
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
