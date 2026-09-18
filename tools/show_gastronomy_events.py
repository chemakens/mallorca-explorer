#!/usr/bin/env python3
"""
Show GASTRONOMY Events from Firestore
--------------------------------------
Connects to Firestore and displays the first 3 events with category GASTRONOMY
showing all their fields exactly as stored in Firestore.
"""

import sys
import json
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
print("QUERYING: events WHERE category == 'GASTRONOMY'")
print("=" * 80)

# Query for GASTRONOMY events
try:
    events_ref = db.collection('events')
    query = events_ref.where('category', '==', 'GASTRONOMY').limit(3)
    docs = query.stream()

    events = []
    for doc in docs:
        event_data = doc.to_dict()
        event_data['_firestore_id'] = doc.id  # Add document ID
        events.append(event_data)

    print(f"\n✅ Found {len(events)} GASTRONOMY events (showing first 3)")

    if not events:
        print("\n⚠️  No GASTRONOMY events found in Firestore")
        sys.exit(0)

    # Display each event with all its fields
    for i, event in enumerate(events, 1):
        print("\n" + "=" * 80)
        print(f"EVENTO #{i}")
        print("=" * 80)

        # Sort keys for consistent display
        sorted_keys = sorted(event.keys())

        for key in sorted_keys:
            value = event[key]

            # Format value for display
            if value is None:
                display_value = "null"
            elif isinstance(value, bool):
                display_value = str(value).lower()
            elif isinstance(value, str):
                # Truncate very long strings
                if len(value) > 100:
                    display_value = f'"{value[:97]}..."'
                else:
                    display_value = f'"{value}"'
            elif isinstance(value, (int, float)):
                display_value = str(value)
            elif isinstance(value, dict):
                display_value = json.dumps(value, ensure_ascii=False, indent=2)
            elif isinstance(value, list):
                display_value = json.dumps(value, ensure_ascii=False)
            else:
                display_value = str(value)

            # Print field
            print(f"  {key:20s}: {display_value}")

    # Summary
    print("\n" + "=" * 80)
    print("RESUMEN DE CAMPOS")
    print("=" * 80)

    if events:
        all_fields = set()
        for event in events:
            all_fields.update(event.keys())

        print(f"\nCampos presentes en eventos GASTRONOMY:")
        for field in sorted(all_fields):
            # Count how many events have this field
            count = sum(1 for e in events if field in e and e[field] is not None)
            print(f"  • {field:25s}: {count}/{len(events)} eventos")

    print("\n✅ Done!")

except Exception as e:
    print(f"\n❌ Error querying Firestore: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
