#!/usr/bin/env python3
"""
Delete Blacklisted Events from Firestore
-----------------------------------------
Removes inappropriate events based on venue and title patterns.

Deletion rules:
1. Venue = "SECRETS MALLORCA" → always delete
2. Venue = "Megasport" → delete only if title contains generic passes
   ("pase diario", "acceso diario", "day pass", "entrada diaria")
   DO NOT delete tournaments, competitions, or real sports events
"""

import sys
from pathlib import Path
import re

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
except Exception as e:
    print(f"❌ Error initializing Firebase: {e}")
    sys.exit(1)

# Get Firestore client
db = firestore.client()

# Blacklist patterns for Megasport generic passes
MEGASPORT_GENERIC_PATTERNS = [
    r'pase\s+diario',
    r'acceso\s+diario',
    r'day\s+pass',
    r'entrada\s+diaria',
    r'pase\s+de\s+día',
    r'acceso\s+de\s+día',
]

print("\n" + "=" * 80)
print("SEARCHING FOR BLACKLISTED EVENTS")
print("=" * 80)

try:
    events_ref = db.collection('events')
    all_docs = events_ref.stream()

    events_to_delete = []

    for doc in all_docs:
        event_data = doc.to_dict()
        title = event_data.get('title', '')
        location = event_data.get('location', '')

        should_delete = False
        reason = ""

        # Rule 1: SECRETS MALLORCA venue → always delete
        if location == "SECRETS MALLORCA":
            should_delete = True
            reason = "Venue: SECRETS MALLORCA"

        # Rule 2: Megasport → delete only generic passes
        elif location == "Megasport" or "Megasport" in location:
            title_lower = title.lower()
            for pattern in MEGASPORT_GENERIC_PATTERNS:
                if re.search(pattern, title_lower, re.IGNORECASE):
                    should_delete = True
                    reason = f"Megasport generic pass: '{pattern}'"
                    break

        if should_delete:
            events_to_delete.append({
                'id': doc.id,
                'title': title,
                'location': location,
                'start_date': event_data.get('start_date', 'N/A'),
                'category': event_data.get('category', 'N/A'),
                'reason': reason,
            })

    print(f"\n✅ Found {len(events_to_delete)} blacklisted events")

    if not events_to_delete:
        print("\n✅ No blacklisted events found. Database is clean!")
        sys.exit(0)

    # Show what will be deleted
    print("\n" + "=" * 80)
    print("EVENTS TO DELETE:")
    print("=" * 80)

    for i, event in enumerate(events_to_delete, 1):
        print(f"\n{i}. {event['title'][:70]}")
        print(f"   Location: {event['location']}")
        print(f"   Date: {event['start_date']}")
        print(f"   Reason: {event['reason']}")

    # Delete events (auto-proceed without confirmation)
    print("\n" + "=" * 80)
    print("DELETING BLACKLISTED EVENTS...")
    print("=" * 80)

    deleted_count = 0

    for event in events_to_delete:
        try:
            events_ref.document(event['id']).delete()
            print(f"  ✅ Deleted: {event['title'][:60]} ({event['reason']})")
            deleted_count += 1
        except Exception as e:
            print(f"  ❌ Failed to delete {event['id']}: {e}")

    print("\n" + "=" * 80)
    print(f"✅ Deleted {deleted_count}/{len(events_to_delete)} blacklisted events")
    print("=" * 80)
    print(f"\n✅ Done!")

except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
