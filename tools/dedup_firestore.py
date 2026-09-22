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

# Prefijos a suprimir (antes de normalizar)
PREFIXES_TO_STRIP = [
    r"^concierto:\s*",
    r"^concert:\s*",
    r"^espectacle:\s*",
    r"^tributo a:\s*",
    r"^tribut a:\s*",
]

# Sufijos de recinto comunes (regex patterns)
VENUE_SUFFIXES = [
    r"\s+en es gremi\s*\(palma\)\s*$",
    r"\s+en es gremi\s*$",
    r"\s+en el trui teatre\s*$",
    r"\s+a santanyí\s*$",
    r"\s+a alaró\s*$",
    r"\s+a alcúdia\s*$",
    r"\s+a palma\s*$",
    r"\s+en palma\s*$",
]

# Coletillas descriptivas largas
DESCRIPTIVE_PATTERNS = [
    r"\s+por parte de\s+.*$",
    r"\s+de la mano de\s+.*$",
    r"\s+organiza\s+.*$",
]

# Normalizaciones bilingües catalán/castellano
BILINGUAL_NORMALIZATIONS = {
    r"\bmúsiques\b": "musicas",
    r"\bmúsic\b": "musica",
    r"\bmúsics\b": "musica",
    r"\bd'": "de ",
    r"\bde l'": "de ",
}

def normalize_title(title: str) -> str:
    """
    Normalización agresiva mejorada:
    - Separa palabras pegadas (CamelCase/PascalCase)
    - Elimina prefijos y sufijos de recintos
    - Normaliza palabras bilingües catalán/castellano
    - Elimina coletillas descriptivas
    - Lowercase, sin acentos, sin puntuación
    """
    t = title.strip()

    # 1. Separar palabras pegadas (CamelCase/PascalCase) ANTES de lowercase
    # Ejemplo: "ArmstrongThe" -> "Armstrong The"
    t = re.sub(r"([a-z])([A-Z])", r"\1 \2", t)
    t = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", t)

    # 2. Eliminar prefijos específicos
    for prefix in PREFIXES_TO_STRIP:
        t = re.sub(prefix, "", t, flags=re.IGNORECASE)

    # 3. Eliminar sufijos de recintos
    for suffix in VENUE_SUFFIXES:
        t = re.sub(suffix, "", t, flags=re.IGNORECASE)

    # 4. Eliminar coletillas descriptivas largas
    for pattern in DESCRIPTIVE_PATTERNS:
        t = re.sub(pattern, "", t, flags=re.IGNORECASE)

    # 5. Normalizar a lowercase
    t = t.lower()

    # 6. Normalizaciones bilingües catalán/castellano
    for pattern, replacement in BILINGUAL_NORMALIZATIONS.items():
        t = re.sub(pattern, replacement, t)

    # 7. Remover acentos
    t = "".join(
        c for c in unicodedata.normalize("NFD", t)
        if unicodedata.category(c) != "Mn"
    )

    # 8. Remover puntuación (conservar solo letras, números y espacios)
    t = re.sub(r"[^a-z0-9\s]", " ", t)

    # 9. Remover palabras de relleno
    for filler in FILLER_WORDS:
        t = re.sub(r"\b" + re.escape(filler) + r"\b", "", t)

    # 10. Normalizar espacios múltiples
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

def get_significant_words(title: str) -> set:
    """Obtiene conjunto de palabras significativas (longitud >= 3)."""
    words = title.split()
    return set(w for w in words if len(w) >= 3)

def count_significant_words(title: str) -> int:
    """Cuenta palabras significativas (longitud >= 3)."""
    return len(get_significant_words(title))

def are_duplicates(title1: str, title2: str) -> bool:
    """
    Detecta si dos títulos normalizados son duplicados.
    Criterios:
    1. Fuzzy matching >= 0.82
    2. Contención de subcadena con >= 3 palabras significativas comunes
    3. Mismo conjunto de palabras significativas (reordenación)
    """
    if not title1 or not title2:
        return False

    # Criterio 1: Fuzzy matching
    similarity = SequenceMatcher(None, title1, title2).ratio()
    if similarity >= 0.82:
        return True

    # Obtener palabras significativas
    words1 = get_significant_words(title1)
    words2 = get_significant_words(title2)

    # Criterio 2: Contención de subcadena
    # Si uno contiene al otro como substring
    if title1 in title2:
        # title1 está contenido en title2
        # Verificar que title2 (el más largo) tenga >= 3 palabras significativas
        # O que ambos compartan >= 2 palabras significativas (más flexible)
        common_words = words1 & words2
        if len(words2) >= 3 or len(common_words) >= 2:
            return True
    elif title2 in title1:
        # title2 está contenido en title1
        common_words = words1 & words2
        if len(words1) >= 3 or len(common_words) >= 2:
            return True

    # Criterio 3: Mismo conjunto de palabras significativas (orden diferente)
    # Ejemplo: "sinatra armstrong jazz room" == "jazz room sinatra armstrong"
    if len(words1) >= 3 and len(words2) >= 3:
        # Si comparten al menos 3 palabras significativas y una tiene ≤ 2 palabras extra
        common_words = words1 & words2
        extra_words1 = words1 - words2
        extra_words2 = words2 - words1

        if len(common_words) >= 3 and len(extra_words1) <= 2 and len(extra_words2) <= 2:
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

def run_tests():
    """Tests unitarios para verificar la detección de duplicados."""
    print("🧪 Ejecutando tests unitarios...\n")

    test_cases = [
        # (title1, title2, expected_duplicate, description)
        (
            "Les músiques de Joan Alcover a Santanyí",
            "Concierto: Las músicas de Joan Alcover",
            True,
            "Bilingüe catalán/castellano + sufijo de lugar"
        ),
        (
            "Encuentro de Gigantes de Alcúdia",
            "Encuentro de Gigantes por parte de la Colla Gegantera d'Alcúdia",
            True,
            "Contención con coletilla descriptiva"
        ),
        (
            "The Jazz Room: Tributo a Frank Sinatra y Louis Armstrong en Es Gremi (Palma)",
            "Tributo a Frank Sinatra y Louis ArmstrongThe Jazz Room",
            True,
            "CamelCase pegado + prefijos/sufijos"
        ),
        (
            "Concierto de rock en Es Gremi",
            "Concierto de jazz en Es Gremi",
            False,
            "Diferentes eventos en misma sala (NO duplicado)"
        ),
        (
            "Rata Market en Palma",
            "Festa Popular en Palma",
            False,
            "Eventos distintos en mismo lugar (NO duplicado)"
        ),
    ]

    passed = 0
    failed = 0

    for title1, title2, expected, description in test_cases:
        norm1 = normalize_title(title1)
        norm2 = normalize_title(title2)
        result = are_duplicates(norm1, norm2)

        status = "✅" if result == expected else "❌"
        if result == expected:
            passed += 1
        else:
            failed += 1

        print(f"{status} Test: {description}")
        print(f"   Título 1: {title1}")
        print(f"   Título 2: {title2}")
        print(f"   Norm 1:   '{norm1}'")
        print(f"   Norm 2:   '{norm2}'")
        print(f"   Esperado: {expected} | Resultado: {result}")
        print()

    print(f"📊 Resultados: {passed} pasados, {failed} fallidos\n")

    if failed > 0:
        print("❌ Algunos tests fallaron. Revisa la lógica de normalización.")
        return False
    else:
        print("✅ Todos los tests pasaron correctamente!")
        return True

if __name__ == "__main__":
    # Si se pasa --test, ejecutar solo los tests
    if "--test" in sys.argv:
        run_tests()
    else:
        main()
