#!/usr/bin/env python3
"""
Upload Approved Events to Firestore
------------------------------------
Reads approved_events.json and uploads them to Firestore.
Supports both Ticketmaster events (tm-) and web-scraped events (web-).
"""

import os
import re
import sys
import json
import unicodedata
from pathlib import Path
from datetime import datetime

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install firebase-admin")
    sys.exit(1)

# Paths
PROJECT_ROOT = Path(__file__).parent.parent
TOOLS_DIR = Path(__file__).parent
APPROVED_JSON = TOOLS_DIR / "approved_events.json"
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"

# Category mapping
CATEGORY_MAP = {
    "Music": "CONCERT",
    "Sports": "SPORT",
    "Arts & Theatre": "CULTURE",
    "Family": "FESTIVAL",
    "Festival": "FESTIVAL",
    "Miscellaneous": "CULTURE",
}

# Venue suffixes to strip when normalizing titles for deduplication
VENUE_PATTERNS = re.compile(
    r"\s+(en |at |@)\s*.+$"          # "Emanero en Es Gremi (Palma)"
    r"|\s+[-–]\s*.+$"                 # "Emanero - Es Gremi"
    r"|\s*\([^)]*\)\s*$",             # "Emanero (Palma)"
    re.IGNORECASE,
)


def normalize_title(title: str) -> str:
    """Normalize a title for fuzzy duplicate detection."""
    # Lowercase
    t = title.lower().strip()
    # Remove accents
    t = "".join(
        c for c in unicodedata.normalize("NFD", t)
        if unicodedata.category(c) != "Mn"
    )
    # Strip venue/location suffixes
    t = VENUE_PATTERNS.sub("", t).strip()
    # Collapse whitespace
    t = re.sub(r"\s+", " ", t)
    return t


def deduplicate_firestore_events(events: list) -> list:
    """
    Remove duplicate events by (normalized_title, start_date).
    When duplicates are found, keep the one with more data
    (image_url, website_url, price) — prefer Ticketmaster over web.
    """
    seen: dict = {}  # key -> index in result list
    result = []

    for event in events:
        data = event["data"]
        title_norm = normalize_title(data.get("title", ""))
        date = data.get("start_date", "")
        muni = data.get("municipality", "")
        key = f"{title_norm}||{date}||{muni}"

        if key not in seen:
            seen[key] = len(result)
            result.append(event)
        else:
            # Keep the richer record
            existing = result[seen[key]]
            existing_score = sum([
                bool(existing["data"].get("image_url")),
                bool(existing["data"].get("website_url")),
                bool(existing["data"].get("price") and not str(existing["data"].get("price","")).lower().startswith("consultar")),
                existing["id"].startswith("tm-"),  # prefer Ticketmaster
            ])
            new_score = sum([
                bool(data.get("image_url")),
                bool(data.get("website_url")),
                bool(data.get("price") and not str(data.get("price","")).lower().startswith("consultar")),
                event["id"].startswith("tm-"),
            ])
            if new_score > existing_score:
                print(f"   🔀 Dedup: kept '{event['data']['title']}' over '{existing['data']['title']}' ({date})")
                result[seen[key]] = event
            else:
                print(f"   🔀 Dedup: skipped '{data['title']}' (duplicate of '{existing['data']['title']}', {date})")

    return result


def init_firestore():
    """Initialize Firestore with service account credentials."""
    cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", SERVICE_ACCOUNT_KEY)

    if not Path(cred_path).exists():
        print(f"❌ Firebase service account key not found at {cred_path}")
        print("\n📋 To get credentials:")
        print("   1. Go to: https://console.firebase.google.com/")
        print("   2. Project Settings → Service Accounts")
        print("   3. Click 'Generate new private key'")
        print(f"   4. Save as: {SERVICE_ACCOUNT_KEY}")
        sys.exit(1)

    try:
        cred = credentials.Certificate(str(cred_path))
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"❌ Failed to initialize Firestore: {e}")
        sys.exit(1)


def map_category(tm_classification):
    """Map Ticketmaster classification to Firestore category."""
    for item in tm_classification:
        segment = item.get("segment", {}).get("name", "")
        mapped = CATEGORY_MAP.get(segment)
        if mapped:
            return mapped
    return "CULTURE"


def extract_municipality(venue):
    """Extract municipality from Ticketmaster venue."""
    city = venue.get("city", {}).get("name", "")
    if city:
        return city
    state = venue.get("state", {}).get("name", "")
    return state or "Mallorca"


def parse_date(date_str):
    """Parse Ticketmaster date to ISO format (YYYY-MM-DD)."""
    if not date_str:
        return None
    try:
        dt = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        return dt.strftime("%Y-%m-%d")
    except ValueError:
        return None


def map_web_event(approved_event):
    """Map web-scraped event to Firestore event schema."""
    event_id = approved_event.get("id")
    title = approved_event.get("title")
    start_date = approved_event.get("start_date")

    if not event_id or not title or not start_date:
        return None

    firestore_event = {
        "title": title,
        "title_es": title,
        "title_de": "",
        "title_ru": "",
        "title_zh": "",
        "description": title,
        "description_es": title,
        "description_de": "",
        "description_ru": "",
        "description_zh": "",
        "category": approved_event.get("category", "CULTURE"),
        "start_date": start_date,
        "end_date": None,
        "municipality": approved_event.get("municipality", "Mallorca"),
        "address": None,
        "is_free": approved_event.get("is_free", True),
        "price": approved_event.get("price"),
        "image_url": approved_event.get("image_url"),
        "is_recurring": False,
        "recurring_day_of_week": None,
        "website_url": approved_event.get("website_url"),
    }

    return {
        "id": event_id,
        "data": firestore_event
    }


def map_ticketmaster_event(approved_event):
    """Map Ticketmaster event to Firestore event schema."""
    tm_event = approved_event.get("raw", {})

    event_id = tm_event.get("id")
    name = tm_event.get("name")

    if not event_id or not name:
        return None

    dates = tm_event.get("dates", {})
    start_info = dates.get("start", {})
    start_date = parse_date(start_info.get("dateTime") or start_info.get("localDate"))

    if not start_date:
        return None

    classifications = tm_event.get("classifications", [])
    category = map_category(classifications)

    venues = tm_event.get("_embedded", {}).get("venues", [])
    venue = venues[0] if venues else {}
    municipality = extract_municipality(venue)
    address = venue.get("address", {}).get("line1")

    price_ranges = tm_event.get("priceRanges", [])
    is_free = not price_ranges
    price = None
    if price_ranges:
        min_price = price_ranges[0].get("min")
        max_price = price_ranges[0].get("max")
        if min_price and max_price:
            price = f"€{min_price}-{max_price}"
        elif min_price:
            price = f"€{min_price}"

    images = tm_event.get("images", [])
    image_url = images[0].get("url") if images else None
    website_url = tm_event.get("url")

    info = tm_event.get("info", "")
    description = info[:500] if info else f"Event in {municipality}"

    firestore_event = {
        "title": name,
        "title_es": name,
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
        "end_date": None,
        "municipality": municipality,
        "address": address,
        "is_free": is_free,
        "price": price,
        "image_url": image_url,
        "is_recurring": False,
        "recurring_day_of_week": None,
        "website_url": website_url,
    }

    return {
        "id": f"tm-{event_id}",
        "data": firestore_event
    }


def delete_all_events(db):
    """
    Delete ALL documents from the events collection.
    Uses batched deletes (500 docs per batch) to handle large collections efficiently.
    This ensures a clean slate before uploading fresh events from the scraper.
    """
    collection = db.collection("events")

    print(f"🗑️  Eliminando TODOS los eventos de Firestore...")

    try:
        # Get all documents in the collection
        all_events = collection.stream()

        # Batch delete (Firestore limit: 500 operations per batch)
        batch = db.batch()
        count = 0

        for doc in all_events:
            batch.delete(doc.reference)
            count += 1

            # Commit every 500 deletes
            if count % 500 == 0:
                batch.commit()
                batch = db.batch()
                print(f"   ⏳ {count} eventos eliminados...")

        # Commit remaining deletes
        if count % 500 != 0:
            batch.commit()

        if count > 0:
            print(f"   ✅ {count} eventos eliminados (colección limpia)")
        else:
            print(f"   ℹ️  La colección ya estaba vacía")

        return count

    except Exception as e:
        print(f"   ⚠️  Error al eliminar eventos: {e}")
        print(f"   ℹ️  Continuando con la subida de nuevos eventos...")
        return 0


def upload_to_firestore(db, events):
    """Upload events to Firestore."""
    if not events:
        print("ℹ️  No events to upload")
        return 0

    print(f"📤 Uploading {len(events)} events to Firestore...")

    collection = db.collection("events")
    uploaded = 0

    for event in events:
        try:
            doc_id = event["id"]
            event_data = event["data"]

            collection.document(doc_id).set(event_data)
            uploaded += 1
            print(f"   ✓ {doc_id}: {event_data['title']}")

        except Exception as e:
            print(f"   ✗ Failed to upload {event.get('id')}: {e}")

    print(f"✅ Successfully uploaded {uploaded}/{len(events)} events")
    return uploaded


def main():
    print("📤 Upload Approved Events to Firestore")
    print("=" * 60)

    if not APPROVED_JSON.exists():
        print(f"❌ File not found: {APPROVED_JSON}")
        print("\n💡 First run: python3 tools/fetch_events_review.py")
        print("   Then approve events and download approved_events.json")
        print(f"   Move it to: {APPROVED_JSON}")
        sys.exit(1)

    print(f"📥 Reading {APPROVED_JSON.name}...")
    with open(APPROVED_JSON, 'r', encoding='utf-8') as f:
        approved_events = json.load(f)

    print(f"✅ Loaded {len(approved_events)} approved events")

    db = init_firestore()
    print("✅ Connected to Firestore")

    # Delete ALL events before uploading fresh ones (complete replacement)
    delete_all_events(db)

    print("🔄 Mapping events to Firestore schema...")
    firestore_events = []
    web_count = 0
    tm_count = 0

    for event in approved_events:
        event_id = event.get("id", "")

        if event_id.startswith("web-"):
            mapped = map_web_event(event)
            web_count += 1
        elif event_id.startswith("tm-") or event.get("raw"):
            mapped = map_ticketmaster_event(event)
            tm_count += 1
        else:
            print(f"   ⚠️  Unknown event type: {event_id}")
            continue

        if mapped:
            firestore_events.append(mapped)
        else:
            print(f"   ⚠️  Skipped invalid event: {event.get('title', event.get('name', 'Unknown'))}")

    print(f"   📊 Web events: {web_count}, Ticketmaster events: {tm_count}")
    print(f"✅ Mapped {len(firestore_events)} events")

    # Deduplicate before uploading
    print("🔍 Deduplicando eventos similares...")
    before = len(firestore_events)
    firestore_events = deduplicate_firestore_events(firestore_events)
    removed = before - len(firestore_events)
    if removed:
        print(f"   ✂️  {removed} duplicados eliminados → {len(firestore_events)} eventos únicos")
    else:
        print(f"   ✅ Sin duplicados detectados")

    uploaded = upload_to_firestore(db, firestore_events)

    print("=" * 60)
    print(f"🎉 Done! {uploaded} events uploaded to Firestore")
    print("\n📱 Events will appear in the app after next sync")


if __name__ == "__main__":
    main()
