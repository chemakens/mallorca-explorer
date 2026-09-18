#!/usr/bin/env python3
"""
Dedup Firestore Events
----------------------
Lee todos los eventos de Firestore, detecta duplicados por
(titulo_normalizado || fecha || municipio) y borra los de menor puntuación.
"""

import re
import sys
import unicodedata
from pathlib import Path

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Instala: pip install firebase-admin")
    sys.exit(1)

PROJECT_ROOT = Path(__file__).parent.parent
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"

VENUE_PATTERNS = re.compile(
    r"\s+(en |at |@)\s*.+$"
    r"|\s+[-–]\s*.+$"
    r"|\s*\([^)]*\)\s*$",
    re.IGNORECASE,
)

def normalize_title(title: str) -> str:
    t = VENUE_PATTERNS.sub("", title).strip().lower()
    t = "".join(
        c for c in unicodedata.normalize("NFD", t)
        if unicodedata.category(c) != "Mn"
    )
    return re.sub(r"[^a-z0-9\s]", "", t).strip()

def real_price(price):
    """True sólo si es un precio real, no 'Consultar...'"""
    if not price:
        return False
    return not str(price).lower().startswith("consultar")

def score(doc_id, data):
    return sum([
        bool(data.get("image_url")),
        bool(data.get("website_url")),
        real_price(data.get("price")),
        doc_id.startswith("tm-"),
    ])

def main():
    dry_run = "--dry-run" in sys.argv
    if dry_run:
        print("🔍 MODO DRY-RUN — no se borrará nada\n")

    cred = credentials.Certificate(str(SERVICE_ACCOUNT_KEY))
    firebase_admin.initialize_app(cred)
    db = firestore.client()

    print("📥 Leyendo eventos de Firestore...")
    docs = list(db.collection("events").stream())
    print(f"   Total: {len(docs)} eventos\n")

    # Agrupar por clave de dedup
    groups = {}  # key -> list of (doc_id, data)
    for doc in docs:
        data = doc.to_dict()
        title_norm = normalize_title(data.get("title", ""))
        date = data.get("start_date", "")
        muni = data.get("municipality", "")
        key = f"{title_norm}||{date}||{muni}"
        groups.setdefault(key, []).append((doc.id, data))

    duplicates = {k: v for k, v in groups.items() if len(v) > 1}
    print(f"🔎 Grupos con duplicados: {len(duplicates)}\n")

    to_delete = []
    for key, entries in duplicates.items():
        # Ordenar: mayor score primero; en empate, tm- primero
        entries.sort(key=lambda x: (score(x[0], x[1]), x[0].startswith("tm-")), reverse=True)
        winner = entries[0]
        losers = entries[1:]
        print(f"  ✅ KEEPER : [{winner[0]}] {winner[1].get('title')} (score={score(winner[0],winner[1])})")
        for doc_id, data in losers:
            print(f"  🗑️  DELETE : [{doc_id}] {data.get('title')} (score={score(doc_id,data)})")
            to_delete.append(doc_id)
        print()

    print(f"Total a borrar: {len(to_delete)} eventos")

    if not to_delete:
        print("✅ No hay duplicados.")
        return

    if dry_run:
        print("\n⚠️  Dry-run: no se ha borrado nada. Ejecuta sin --dry-run para borrar.")
        return

    confirm = input(f"\n¿Borrar {len(to_delete)} duplicados de Firestore? (s/n): ")
    if confirm.lower() != "s":
        print("Cancelado.")
        return

    print("\n🗑️  Borrando...")
    for doc_id in to_delete:
        db.collection("events").document(doc_id).delete()
        print(f"   Borrado: {doc_id}")

    print(f"\n✅ Limpieza completada. {len(to_delete)} duplicados eliminados.")

if __name__ == "__main__":
    main()
