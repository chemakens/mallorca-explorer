#!/usr/bin/env python3
"""
Delete All Web-Scraped Events from Firestore
---------------------------------------------
Removes all events with IDs starting with 'web-' (scraped events).
Ticketmaster events (tm-) and sports events are kept.
Run this before uploading fresh scraped data to start clean.
"""

import sys
from pathlib import Path

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Install with: pip install firebase-admin")
    sys.exit(1)

PROJECT_ROOT = Path(__file__).parent.parent
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"

if not SERVICE_ACCOUNT_KEY.exists():
    print(f"❌ No se encuentra: {SERVICE_ACCOUNT_KEY}")
    sys.exit(1)

cred = credentials.Certificate(str(SERVICE_ACCOUNT_KEY))
firebase_admin.initialize_app(cred)
db = firestore.client()

print("🔍 Buscando eventos web-* en Firestore...")

all_docs = db.collection("events").stream()
to_delete = []

for doc in all_docs:
    if doc.id.startswith("web-"):
        data = doc.to_dict()
        to_delete.append({
            "id": doc.id,
            "title": data.get("title", "?")[:60],
            "start_date": data.get("start_date", "?"),
            "source": data.get("source", "?"),
        })

print(f"\n📊 Encontrados {len(to_delete)} eventos web-* en Firestore")

if not to_delete:
    print("✅ Nada que borrar.")
    sys.exit(0)

# Mostrar resumen por fuente
from collections import Counter
by_source = Counter(e["source"] for e in to_delete)
print("\nPor fuente:")
for src, count in sorted(by_source.items()):
    print(f"  {src}: {count}")

# Mostrar algunos ejemplos
print("\nEjemplos:")
for e in to_delete[:5]:
    print(f"  {e['start_date']} [{e['source']}] {e['title']}")
if len(to_delete) > 5:
    print(f"  ... y {len(to_delete) - 5} más")

print(f"\n⚠️  Se borrarán {len(to_delete)} eventos web scrapeados.")
print("⚠️  Los eventos de Ticketmaster (tm-) y deportes NO se tocan.")
resp = input("\n¿Continuar? (yes/no): ").strip().lower()

if resp != "yes":
    print("❌ Cancelado.")
    sys.exit(0)

# Borrar en lotes de 500 (límite Firestore)
BATCH_SIZE = 500
deleted = 0
for i in range(0, len(to_delete), BATCH_SIZE):
    batch = db.batch()
    chunk = to_delete[i:i + BATCH_SIZE]
    for e in chunk:
        batch.delete(db.collection("events").document(e["id"]))
    batch.commit()
    deleted += len(chunk)
    print(f"  ✅ Borrados {deleted}/{len(to_delete)}...", flush=True)

print(f"\n✅ ¡Listo! {deleted} eventos web-* eliminados de Firestore.")
print("Ahora puedes subir los eventos nuevos con upload_approved_events.py")
