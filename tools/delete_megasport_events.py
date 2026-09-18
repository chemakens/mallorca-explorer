#!/usr/bin/env python3
"""
Delete Megasport Events from Firestore
---------------------------------------
Removes all events from Megasport venue or with related terms in title.
"""

import sys
from pathlib import Path

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

print("\n" + "=" * 80)
print("SEARCHING FOR MEGASPORT EVENTS")
print("=" * 80)

try:
    events_ref = db.collection('events')

    # Get all events to filter locally
    all_docs = events_ref.stream()

    events_to_delete = []

    # Terms to search for
    TITLE_TERMS = ["Megasport", "DiarioMegasport", "Pase Diario"]
    VENUE_TERM = "Megasport"

    for doc in all_docs:
        event_data = doc.to_dict()
        title = event_data.get('title', '')
        location = event_data.get('location', '')

        # Check if should be deleted
        should_delete = False
        reason = ""

        if location == VENUE_TERM:
            should_delete = True
            reason = f"venue: {location}"
        else:
            # Check if title contains any of the terms
            for term in TITLE_TERMS:
                if term in title:
                    should_delete = True
                    reason = f"title contains '{term}'"
                    break

        if should_delete:
            events_to_delete.append({
                'id': doc.id,
                'title': title,
                'location': location,
                'category': event_data.get('category', 'N/A'),
                'start_date': event_data.get('start_date', 'N/A'),
                'reason': reason,
            })

    print(f"\n✅ Found {len(events_to_delete)} events to delete")

    if not events_to_delete:
        print("\n✅ No Megasport events found. Database is clean!")
        sys.exit(0)

    # Show events to delete
    print("\n" + "=" * 80)
    print("EVENTS TO DELETE:")
    print("=" * 80)

    for i, event in enumerate(events_to_delete, 1):
        print(f"\n{i}. {event['title']}")
        print(f"   Location: {event['location']}")
        print(f"   Category: {event['category']}")
        print(f"   Date: {event['start_date']}")
        print(f"   Reason: {event['reason']}")
        print(f"   ID: {event['id'][:50]}")

    # Ask for confirmation
    print("\n" + "=" * 80)
    print(f"⚠️  WARNING: This will DELETE {len(events_to_delete)} events from Firestore!")
    print("=" * 80)

    response = input("\nProceed with deletion? (yes/no): ").strip().lower()

    if response != 'yes':
        print("\n❌ Deletion cancelled by user.")
        sys.exit(0)

    # Delete events
    print("\n" + "=" * 80)
    print("DELETING EVENTS...")
    print("=" * 80)

    deleted_count = 0

    for event in events_to_delete:
        try:
            events_ref.document(event['id']).delete()
            print(f"  ✅ Deleted: {event['title'][:60]}")
            deleted_count += 1
        except Exception as e:
            print(f"  ❌ Failed to delete {event['id']}: {e}")

    print("\n" + "=" * 80)
    print(f"✅ Deleted {deleted_count}/{len(events_to_delete)} events")
    print("=" * 80)

    print("\n✅ Done!")

except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
