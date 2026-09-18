#!/usr/bin/env python3
"""
Setup verification for event sync scripts (Ticketmaster & Eventbrite).
Checks that all required credentials and dependencies are available.
"""

import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
LOCAL_PROPERTIES = PROJECT_ROOT / "local.properties"
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"

print("🔍 Checking setup for event sync scripts...")
print("=" * 60)

checks_passed = 0
checks_total = 5

# 1. Check Python dependencies
print("\n1️⃣  Checking Python dependencies...")
try:
    import firebase_admin
    import requests
    print("   ✅ firebase-admin: installed")
    print("   ✅ requests: installed")
    checks_passed += 1
except ImportError as e:
    print(f"   ❌ Missing dependency: {e}")
    print("   → Install with: pip install -r tools/requirements.txt")

# 2. Check Ticketmaster API key
print("\n2️⃣  Checking Ticketmaster API key...")
if LOCAL_PROPERTIES.exists():
    with open(LOCAL_PROPERTIES) as f:
        content = f.read()
        if "TICKETMASTER_API_KEY=" in content:
            key = [line for line in content.split('\n') if line.startswith("TICKETMASTER_API_KEY=")]
            if key and len(key[0].split('=', 1)[1].strip()) > 0:
                print("   ✅ TICKETMASTER_API_KEY: configured")
                checks_passed += 1
            else:
                print("   ❌ TICKETMASTER_API_KEY is empty")
                print("   → Add your API key from https://developer.ticketmaster.com/")
        else:
            print("   ❌ TICKETMASTER_API_KEY not found")
            print("   → Add to local.properties: TICKETMASTER_API_KEY=your_key_here")
else:
    print(f"   ❌ local.properties not found at {LOCAL_PROPERTIES}")

# 3. Check Eventbrite API key
print("\n3️⃣  Checking Eventbrite API key...")
if LOCAL_PROPERTIES.exists():
    with open(LOCAL_PROPERTIES) as f:
        content = f.read()
        if "EVENTBRITE_API_KEY=" in content:
            key = [line for line in content.split('\n') if line.startswith("EVENTBRITE_API_KEY=")]
            if key and len(key[0].split('=', 1)[1].strip()) > 0:
                print("   ✅ EVENTBRITE_API_KEY: configured")
                checks_passed += 1
            else:
                print("   ❌ EVENTBRITE_API_KEY is empty")
                print("   → Add your Private Token from https://www.eventbrite.com/platform/api")
        else:
            print("   ❌ EVENTBRITE_API_KEY not found")
            print("   → Add to local.properties: EVENTBRITE_API_KEY=your_private_token_here")
else:
    print(f"   ❌ local.properties not found at {LOCAL_PROPERTIES}")

# 4. Check serviceAccountKey.json
print("\n4️⃣  Checking Firebase credentials...")
if SERVICE_ACCOUNT_KEY.exists():
    print(f"   ✅ serviceAccountKey.json: found")
    checks_passed += 1
else:
    print(f"   ❌ serviceAccountKey.json not found")
    print("   → Download from Firebase Console (see tools/README.md)")

# 5. Check .gitignore
print("\n5️⃣  Checking .gitignore...")
gitignore = PROJECT_ROOT / ".gitignore"
if gitignore.exists():
    with open(gitignore) as f:
        content = f.read()
        if "serviceAccountKey.json" in content and "local.properties" in content:
            print("   ✅ Sensitive files are in .gitignore")
            checks_passed += 1
        else:
            print("   ⚠️  serviceAccountKey.json or local.properties not in .gitignore")
else:
    print("   ⚠️  .gitignore not found")

# Summary
print("\n" + "=" * 60)
print(f"📊 Setup check: {checks_passed}/{checks_total} passed")

if checks_passed == checks_total:
    print("\n✅ All checks passed! You're ready to run:")
    print("   python tools/fetch_ticketmaster_events.py")
    print("   python tools/fetch_eventbrite_events.py")
else:
    print("\n⚠️  Some checks failed. Fix the issues above before running the scripts.")
    print("\n📖 See tools/README.md for detailed setup instructions.")

print("=" * 60)
