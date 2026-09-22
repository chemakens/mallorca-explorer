#!/usr/bin/env python3
"""
Dedup Firestore Events
----------------------
Lee todos los eventos de Firestore, detecta duplicados por fecha + fuzzy matching
y borra los de menor puntuación, conservando los más completos.
"""

import re
import sys
import unicodedata
from pathlib import Path
from difflib import SequenceMatcher

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Instala: pip install firebase-admin")
    sys.exit(1)

PROJECT_ROOT = Path(__file__).parent.parent
SERVICE_ACCOUNT_KEY = PROJECT_ROOT / "serviceAccountKey.json"

# Palabras de relleno a eliminar en normalización
FILLER_WORDS = [
    "en es gremi", "es gremi", "en palma", "palma", "mallorca",
    "tributo a", "tribut a", "concierto", "concert", "presenta",
    "en el", "en la", "al", "del", "de la", "de", "el", "la",
]

def normalize_title(title: str) -> str:
    """Normalización agresiva: lowercase, sin acentos, sin puntuación, sin palabras de relleno."""
    t = title.strip().lower()

    # Remover acentos
    t = "".join(
        c for c in unicodedata.normalize("NFD", t)
        if unicodedata.category(c) != "Mn"
    )

    # Remover puntuación (conservar solo letras, números y espacios)
    t = re.sub(r"[^a-z0-9\s]", " ", t)

    # Remover palabras de relleno
    for filler in FILLER_WORDS:
        t = re.sub(r"\b" + re.escape(filler) + r"\b", "", t)

    # Normalizar espacios múltiples
    t = re.sub(r"\s+", " ", t).strip()

    return t

def real_price(price):
    """True sólo si es un precio real, no 'Consultar...'"""
    if not price:
        return False
    return not str(price).lower().startswith("consultar")

def is_official_source(doc_id: str, data: dict) -> bool:
    """Detecta si proviene de fuente oficial o con enlace de venta verificado."""
    # Ticketmaster u otras fuentes con prefijo conocido
    if doc_id.startswith("tm-") or doc_id.startswith("ticketib-"):
        return True

    # URLs de venta oficiales
    url = data.get("website_url", "")
    if any(domain in url for domain in ["ticketmaster", "ticketib", "entradas", "tickets"]):
        return True

    return False

def score(doc_id: str, data: dict) -> int:
    """Puntuación de calidad: mayor = mejor evento para conservar."""
    points = 0

    # Fuente oficial (+3 puntos)
    if is_official_source(doc_id, data):
        points += 3

    # Información completa
    points += bool(data.get("image_url"))
    points += bool(data.get("website_url"))
    points += real_price(data.get("price"))

    # Descripción presente y larga
    desc = data.get("description", "")
    if desc and len(desc) > 50:
        points += 1

    # Ubicación detallada
    if data.get("location") and len(data.get("location", "")) > 10:
        points += 1

    return points

def are_duplicates(title1: str, title2: str) -> bool:
    """
    Detecta si dos títulos normalizados son duplicados.
    Criterios:
    - Fuzzy matching >= 0.82
    - O contención de subcadena (uno contiene al otro, longitud > 6)
    """
    if not title1 or not title2:
        return False

    # Fuzzy matching
    similarity = SequenceMatcher(None, title1, title2).ratio()
    if similarity >= 0.82:
        return True

    # Contención de subcadena (solo si ambos tienen longitud significativa)
    min_len = min(len(title1), len(title2))
    if min_len > 6:
        if title1 in title2 or title2 in title1:
            return True

    return False

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

    # Agrupar por fecha (agrupación estricta)
    by_date = {}  # date -> list of (doc_id, data, title_normalized)
    for doc in docs:
        data = doc.to_dict()
        date = data.get("start_date", "")
        if not date:
            continue

        title_norm = normalize_title(data.get("title", ""))
        by_date.setdefault(date, []).append((doc.id, data, title_norm))

    print(f"📅 Fechas con eventos: {len(by_date)}\n")

    # Detectar duplicados dentro de cada fecha
    to_delete = []
    duplicate_groups = []

    for date, events in by_date.items():
        if len(events) < 2:
            continue

        # Marcar eventos ya procesados
        processed = set()

        for i, (doc_id1, data1, title1) in enumerate(events):
            if i in processed:
                continue

            # Buscar duplicados de este evento
            group = [(doc_id1, data1, title1)]

            for j, (doc_id2, data2, title2) in enumerate(events):
                if j <= i or j in processed:
                    continue

                # Verificar si son duplicados
                if are_duplicates(title1, title2):
                    group.append((doc_id2, data2, title2))
                    processed.add(j)

            # Si encontramos duplicados, procesarlos
            if len(group) > 1:
                duplicate_groups.append((date, group))
                processed.add(i)

    print(f"🔎 Grupos de duplicados encontrados: {len(duplicate_groups)}\n")

    # Procesar cada grupo de duplicados
    for date, group in duplicate_groups:
        print(f"📅 Fecha: {date}")

        # Ordenar por score (mayor primero), luego por fuente oficial
        group.sort(
            key=lambda x: (score(x[0], x[1]), is_official_source(x[0], x[1])),
            reverse=True
        )

        winner = group[0]
        losers = group[1:]

        winner_score = score(winner[0], winner[1])
        winner_title = winner[1].get("title", "")

        print(f"  ✅ KEEPER : [{winner[0]}] {winner_title}")
        print(f"              Score: {winner_score} | Normalized: '{winner[2]}'")

        for doc_id, data, title_norm in losers:
            loser_score = score(doc_id, data)
            loser_title = data.get("title", "")
            print(f"  🗑️  DELETE : [{doc_id}] {loser_title}")
            print(f"              Score: {loser_score} | Normalized: '{title_norm}'")
            to_delete.append(doc_id)

        print()

    print(f"📊 Total a borrar: {len(to_delete)} eventos duplicados")

    if not to_delete:
        print("✅ No hay duplicados detectados.")
        return

    if dry_run:
        print("\n⚠️  Dry-run: no se ha borrado nada. Ejecuta sin --dry-run para borrar.")
        return

    confirm = input(f"\n¿Borrar {len(to_delete)} duplicados de Firestore? (s/n): ")
    if confirm.lower() != "s":
        print("❌ Cancelado.")
        return

    print("\n🗑️  Borrando duplicados de Firestore...")
    for doc_id in to_delete:
        db.collection("events").document(doc_id).delete()
        print(f"   ✓ Borrado: {doc_id}")

    print(f"\n✅ Limpieza completada. {len(to_delete)} duplicados eliminados.")

if __name__ == "__main__":
    main()
