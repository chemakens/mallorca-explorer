#!/usr/bin/env python3
"""
Delete Past Events from Firestore
----------------------------------
Removes all events with start_date before today (2026-09-07).
"""

import sys
from pathlib import Path
from datetime import datetime, date

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

# Initialize Firebase Admin SDK
if not SERVICE_ACCOUNT_KEY.exists():
    print(f"❌ Error: Service account key not found at {SERVICE_ACCOUNT_KEY}")
    sys.exit(1)

try:
    cred = credentials.Certificate(str(SERVICE_ACCOUNT_KEY))
    firebase_admin.initialize_app(cred)
    print("✅ Connected to Firestore")
    print(f"📁 Using credentials: {SERVICE_ACCOUNT_KEY}")
except Exception as e:
    print(f"❌ Error initializing Firebase: {e}")
    sys.exit(1)

# Get Firestore client
db = firestore.client()

# Today's date (dynamic - uses current system date)
TODAY = datetime.now().date()
TODAY_STR = TODAY.strftime('%Y-%m-%d')

print("\n" + "=" * 80)
print(f"SEARCHING FOR EVENTS BEFORE {TODAY_STR}")
print("=" * 80)

try:
    events_ref = db.collection('events')

    # Get all events
    all_docs = events_ref.stream()

    events_to_delete = []

    for doc in all_docs:
        event_data = doc.to_dict()
        start_date_str = event_data.get('start_date', '')

        if not start_date_str:
            continue

        # Parse date
        try:
            event_date = datetime.strptime(start_date_str, '%Y-%m-%d').date()
        except:
            # Invalid date format, skip
            continue

        # Check if event is in the past
        if event_date < TODAY:
            events_to_delete.append({
                'id': doc.id,
                'title': event_data.get('title', 'N/A'),
                'start_date': start_date_str,
                'category': event_data.get('category', 'N/A'),
                'municipality': event_data.get('municipality', 'N/A'),
            })

    print(f"\n✅ Found {len(events_to_delete)} past events to delete")

    if not events_to_delete:
        print(f"\n✅ No past events found. All events are on or after {TODAY_STR}!")
        sys.exit(0)

    # Group by date for better visibility
    from collections import defaultdict
    events_by_date = defaultdict(list)
    for event in events_to_delete:
        events_by_date[event['start_date']].append(event)

    # Show summary by date
    print("\n" + "=" * 80)
    print("EVENTS TO DELETE (grouped by date):")
    print("=" * 80)

    for event_date in sorted(events_by_date.keys()):
        events = events_by_date[event_date]
        print(f"\n📅 {event_date} ({len(events)} events):")
        for i, event in enumerate(events[:3], 1):  # Show first 3 per date
            print(f"   {i}. {event['title'][:60]} [{event['category']}]")
        if len(events) > 3:
            print(f"   ... and {len(events) - 3} more events on this date")

    # Ask for confirmation
    print("\n" + "=" * 80)
    print(f"⚠️  WARNING: This will DELETE {len(events_to_delete)} past events from Firestore!")
    print(f"⚠️  Events with start_date < {TODAY_STR} will be removed.")
    print("=" * 80)

    response = input("\nProceed with deletion? (yes/no): ").strip().lower()

    if response != 'yes':
        print("\n❌ Deletion cancelled by user.")
        sys.exit(0)

    # Delete events
    print("\n" + "=" * 80)
    print("DELETING PAST EVENTS...")
    print("=" * 80)

    deleted_count = 0

    for event in events_to_delete:
        try:
            events_ref.document(event['id']).delete()
            if deleted_count < 5:  # Show first 5
                print(f"  ✅ Deleted: {event['start_date']} - {event['title'][:50]}")
            deleted_count += 1
        except Exception as e:
            print(f"  ❌ Failed to delete {event['id']}: {e}")

    if deleted_count > 5:
        print(f"  ... and {deleted_count - 5} more events deleted")

    print("\n" + "=" * 80)
    print(f"✅ Deleted {deleted_count}/{len(events_to_delete)} events")
    print("=" * 80)

    print(f"\n✅ All events before {TODAY_STR} have been removed!")
    print(f"✅ Done!")

except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
