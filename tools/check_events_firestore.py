#!/usr/bin/env python3
"""
Check Events in Firestore
--------------------------
Displays statistics about events currently in Firestore.
"""

import os
import sys
from pathlib import Path
from datetime import datetime
from collections import Counter

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install firebase-admin")
    sys.exit(1)

# Paths
PROJECT_ROOT = Path(__file__).parent.parent
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"


def init_firestore():
    """Initialize Firestore with service account credentials."""
    cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", SERVICE_ACCOUNT_KEY)

    if not Path(cred_path).exists():
        print(f"❌ Firebase service account key not found at {cred_path}")
        print("\n📋 Run tools/README.md for setup instructions")
        sys.exit(1)

    try:
        cred = credentials.Certificate(str(cred_path))
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"❌ Failed to initialize Firestore: {e}")
        sys.exit(1)


def get_source(event_id):
    """Determine event source from ID prefix."""
    if event_id.startswith("tm-"):
        return "Ticketmaster"
    elif event_id.startswith("eb-"):
        return "Eventbrite"
    else:
        return "Manual"


def parse_date(date_str):
    """Parse ISO date string to datetime."""
    try:
        return datetime.fromisoformat(date_str)
    except (ValueError, TypeError):
        return None


def main():
    print("🔍 Checking Firestore Events")
    print("=" * 70)

    # Initialize Firestore
    db = init_firestore()
    print("✅ Connected to Firestore\n")

    # Fetch all events
    print("📥 Fetching events from Firestore...")
    events_ref = db.collection("events")
    docs = events_ref.stream()

    events = []
    for doc in docs:
        event = doc.to_dict()
        event["id"] = doc.id
        events.append(event)

    if not events:
        print("⚠️  No events found in Firestore")
        return

    print(f"✅ Loaded {len(events)} events\n")
    print("=" * 70)

    # 1. Total events
    print(f"\n📊 TOTAL EVENTS: {len(events)}")

    # 2. By category
    print("\n📂 BY CATEGORY:")
    categories = Counter(event.get("category", "UNKNOWN") for event in events)
    for category, count in sorted(categories.items()):
        emoji = {
            "CONCERT": "🎵",
            "FESTIVAL": "🎪",
            "CULTURE": "🎭",
            "SPORT": "⚽",
            "MARKET": "🛒",
            "NIGHTLIFE": "🎶",
        }.get(category, "❓")
        percentage = (count / len(events)) * 100
        print(f"   {emoji} {category:12} {count:3} eventos ({percentage:5.1f}%)")

    # 3. By source
    print("\n📍 BY SOURCE:")
    sources = Counter(get_source(event.get("id", "")) for event in events)
    for source, count in sorted(sources.items(), key=lambda x: -x[1]):
        emoji = {
            "Ticketmaster": "🎟️ ",
            "Eventbrite": "🎫",
            "Manual": "✍️ ",
        }.get(source, "❓")
        percentage = (count / len(events)) * 100
        print(f"   {emoji} {source:13} {count:3} eventos ({percentage:5.1f}%)")

    # 4. Upcoming events (next 5 by start_date)
    print("\n📅 UPCOMING EVENTS (next 5):")

    # Filter and sort events
    now = datetime.now()
    upcoming = []

    for event in events:
        start_date_str = event.get("start_date")
        if start_date_str:
            start_date = parse_date(start_date_str)
            if start_date and start_date >= now:
                upcoming.append((start_date, event))

    # Sort by date
    upcoming.sort(key=lambda x: x[0])

    if not upcoming:
        print("   ⚠️  No upcoming events found")
    else:
        for i, (start_date, event) in enumerate(upcoming[:5], 1):
            event_id = event.get("id", "unknown")
            title = event.get("title", "No title")
            category = event.get("category", "UNKNOWN")
            municipality = event.get("municipality", "Unknown")
            source = get_source(event_id)

            # Truncate title if too long
            if len(title) > 40:
                title = title[:37] + "..."

            date_str = start_date.strftime("%Y-%m-%d")
            source_tag = source[0].upper()  # T, E, or M

            print(f"   {i}. [{date_str}] {title}")
            print(f"      {category} · {municipality} · {source}")

    # 5. Events by municipality (top 5)
    print("\n🏘️  TOP 5 MUNICIPALITIES:")
    municipalities = Counter(event.get("municipality", "Unknown") for event in events)
    for municipality, count in municipalities.most_common(5):
        percentage = (count / len(events)) * 100
        print(f"   {municipality:20} {count:3} eventos ({percentage:5.1f}%)")

    print("\n" + "=" * 70)
    print(f"✅ Analysis complete ({len(events)} events)")
    print("=" * 70)


if __name__ == "__main__":
    main()
