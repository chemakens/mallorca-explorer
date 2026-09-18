#!/usr/bin/env python3
"""
Fix Gastronomy Event Titles in Firestore
-----------------------------------------
Deletes GASTRONOMY events with malformed titles (e.g. "TapalmaGastronomía")
where the category name is concatenated to the title.

After running this script, re-run the scraper with the fixed title extraction
and upload the clean events.
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
print("SEARCHING FOR MALFORMED GASTRONOMY EVENTS")
print("=" * 80)

try:
    events_ref = db.collection('events')

    # Get all GASTRONOMY events
    query = events_ref.where('category', '==', 'GASTRONOMY')
    docs = query.stream()

    malformed_events = []

    for doc in docs:
        event_data = doc.to_dict()
        title = event_data.get('title', '')

        # Check if title contains "Gastronomía" concatenated
        # (indicates malformed title from old scraper)
        if 'Gastronomía' in title or 'Gastronomia' in title:
            malformed_events.append({
                'id': doc.id,
                'title': title,
                'start_date': event_data.get('start_date', 'N/A'),
                'municipality': event_data.get('municipality', 'N/A'),
            })

    print(f"\n✅ Found {len(malformed_events)} malformed GASTRONOMY events")

    if not malformed_events:
        print("\n✅ No malformed events found. Database is clean!")
        sys.exit(0)

    # Show malformed events
    print("\n" + "=" * 80)
    print("MALFORMED EVENTS TO DELETE:")
    print("=" * 80)

    for i, event in enumerate(malformed_events, 1):
        print(f"\n{i}. {event['title']}")
        print(f"   ID: {event['id']}")
        print(f"   Date: {event['start_date']}")
        print(f"   Municipality: {event['municipality']}")

    # Ask for confirmation
    print("\n" + "=" * 80)
    print(f"⚠️  WARNING: This will DELETE {len(malformed_events)} events from Firestore!")
    print("=" * 80)

    response = input("\nProceed with deletion? (yes/no): ").strip().lower()

    if response != 'yes':
        print("\n❌ Deletion cancelled by user.")
        sys.exit(0)

    # Delete malformed events
    print("\n" + "=" * 80)
    print("DELETING MALFORMED EVENTS...")
    print("=" * 80)

    deleted_count = 0

    for event in malformed_events:
        try:
            events_ref.document(event['id']).delete()
            print(f"  ✅ Deleted: {event['title'][:60]}")
            deleted_count += 1
        except Exception as e:
            print(f"  ❌ Failed to delete {event['id']}: {e}")

    print("\n" + "=" * 80)
    print(f"✅ Deleted {deleted_count}/{len(malformed_events)} events")
    print("=" * 80)

    print("\n📋 NEXT STEPS:")
    print("  1. Run the scraper with fixed title extraction:")
    print("     python3 tools/fetch_web_events.py")
    print("  2. Upload the clean events to Firestore:")
    print("     python3 tools/upload_approved_events.py")
    print("  3. Verify the titles are now clean:")
    print("     python3 tools/show_gastronomy_events.py")

    print("\n✅ Done!")

except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
