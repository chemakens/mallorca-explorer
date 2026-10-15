#!/usr/bin/env python3
"""
Crear nuevos sitios en Firestore a partir de fotos AETIB en Descargas.
Uso:
  python3 create_aetib_places.py [--dry-run]
"""

import os, re, sys, json, time, argparse, unicodedata
from pathlib import Path
from collections import defaultdict

try:
    import firebase_admin
    from firebase_admin import credentials, firestore, storage
except ImportError:
    sys.exit("❌ pip install firebase-admin")

try:
    import requests
except ImportError:
    sys.exit("❌ pip install requests")

# ── Config ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).parent.parent
SERVICE_KEY  = PROJECT_ROOT / "serviceAccountKey.json"

PHOTO_DIRS   = [
    Path.home() / "Downloads",
    Path.home() / "Desktop" / "mallorca explorer sin copyright",
]
BUCKET_NAME    = "mallorca-explorer-49eb2.firebasestorage.app"
STORAGE_PREFIX = "places"

# ── Coordenadas del KML (fuente principal, antes de Nominatim) ────────────────
KML_COORDS = {
    "ajuntament-santa-maria-del-cami":       (39.6472447, 2.7785609),
    "albufera-de-mallorca":                  (39.8003147, 3.1187504),
    "bec-de-ferrutx":                        (39.7178457, 3.2860995),
    "betlem":                                (39.7529396, 3.3174602),
    "biniaraix":                             (39.7710625, 2.7350090),
    "boquer-pollenca":                       (39.9145900, 3.0823700),
    "cala-domingos":                         (39.4585429, 3.2780694),
    "cala-major":                            (39.5525768, 2.6127159),
    "cala-matzoc":                           (39.7593763, 3.4040072),
    "cala-mondrago":                         (39.3521755, 3.1883751),
    "cala-rajtada-puerto":                   (39.7111736, 3.4629321),
    "cala-sa-font-celada-parc-de-llevant":   (39.7635839, 3.3847252),
    "cala-de-sa-font-celada":                (39.7635839, 3.3847252),
    "cala-na-clara":                         (39.7578505, 3.3254150),
    "sa-font-celada":                        (39.7635839, 3.3847252),
    "can-fussimany":                         (39.7600309, 2.6414413),
    "can-querol":                            (39.7683300, 2.7143700),
    "cartoixa-de-valldemossa":               (39.7093067, 2.6226363),
    "castell-dalaro":                        (39.7338171, 2.7915961),
    "castell-de-santueri":                   (39.4315823, 3.1870641),
    "convent-dels-minims":                   (39.6440974, 3.0108893),
    "convent-dels-minims-sineu":             (39.6440974, 3.0108893),
    "coves-del-drach":                       (39.5357992, 3.3307428),
    "deia":                                  (39.7480381, 2.6483738),
    "embassament-des-gorg-blau":             (39.8006909, 2.8113607),
    "ermita-de-betlem-arta":                 (39.7372596, 3.3113075),
    "ermita-de-valldemossa":                 (39.7296788, 2.6093607),
    "ermita-de-la-trinitat-valldemossa":     (39.7296788, 2.6093607),
    "es-baluard-interior":                   (39.5702820, 2.6411370),
    "es-firo-soller":                        (39.7666296, 2.7149847),
    "esglesia-de-la-sang-muro":              (39.7339935, 3.0578159),
    "finca-bellveure":                       (39.7043605, 2.8283820),
    "finca-bellveure-binissalem":            (39.7043605, 2.8283820),
    "far-bufador-soller":                    (39.7970892, 2.6816450),
    "far-cap-de-ses-salnes":                 (39.2658196, 3.0531373),
    "far-des-cap-gros-soller":               (39.7970622, 2.6816388),
    "formentor":                             (39.9534947, 3.1963367),
    "jardi-botanic-soller":                  (39.7645118, 2.7094112),
    "lhorta-soller":                         (39.7744411, 2.7154076),
    "mercat-olivar":                         (39.5738110, 2.6534772),
    "mercat-pere-garau":                     (39.5747781, 2.6642858),
    "museu-mallorca-cap-de-bou":             (39.5672812, 2.6507297),
    "museu-mallorca":                        (39.5672812, 2.6507297),
    "naranjos-soller":                       (39.7706509, 2.7265639),
    "palau-solleric":                        (39.5709604, 2.6465444),
    "palau-vivot":                           (39.5696091, 2.6518200),
    "parroquia-binissalem":                  (39.6889890, 2.8420684),
    "parroquia-santanyi":                    (39.3548205, 3.1295305),
    "parroquia-soller":                      (39.7665800, 2.7156300),
    "pollenca-des-del-puig-de-maria":        (39.8709367, 3.0220737),
    "puig-de-maria-pollenca":                (39.8689700, 3.0224600),
    "port-de-palma-castillo-de-bellver":     (39.5638706, 2.6194042),
    "port-de-soller":                        (39.7977600, 2.6964200),
    "porto-colom":                           (39.4161270, 3.2564555),
    "porto-cristo":                          (39.5442926, 3.3368134),
    "puerto-portals":                        (39.5298318, 2.5655747),
    "puig-de-lofre":                         (39.7634056, 2.7670254),
    "sestaca":                               (39.7336703, 2.6038772),
    "sestaca-valldemossa":                   (39.7336703, 2.6038772),
    "ses-paisses-arta":                      (39.6872331, 3.3551960),
    "ses-paisses-arta-prque-natural-llevant":(39.6872331, 3.3551960),
    "talaiot-son-serra-de-marina-talaiot":   (39.7300474, 3.2127025),
    "talaya-moreia":                         (39.7803657, 3.3502170),
    "terrasses-de-la-seu":                   (39.5674799, 2.6482771),
    "tramvia-de-soller":                     (39.5769161, 2.6538410),
    "tramvia-soller":                        (39.5769161, 2.6538410),
    "sa-caragolera-binissalem":              (39.6757634, 2.8438424),
    "estudi-miro":                           (39.5575100, 2.6295400),
    "llaut-cala-sant-vicenc":                (39.9038400, 3.0787600),
}

# ── Inicializar Firebase ───────────────────────────────────────────────────────
def init_firebase():
    cred = credentials.Certificate(str(SERVICE_KEY))
    firebase_admin.initialize_app(cred, {"storageBucket": BUCKET_NAME})
    return firestore.client(), storage.bucket()

# ── Parsear nombre de archivo ─────────────────────────────────────────────────
def parse_photo(filename):
    base = re.sub(r'\s*\.jpg\s*$', '', filename, flags=re.IGNORECASE).strip()
    m = re.search(r'[,\-]\s*([^,\-]+?)\s*-\s*AETIB\s*\d*\s*$', base, re.IGNORECASE)
    if m:
        author = m.group(1).strip()
        author = re.sub(r',?\s*culture\s*$', '', author, flags=re.IGNORECASE).strip()
        place_name = base[:m.start()].strip().rstrip(',-').strip()
        # Eliminar nombre de autor que puede haberse colado antes del separador
        # ej: "Can Fussimany,Eduardo Miralles, culture-AETIB" → "Can Fussimany"
        place_name = re.sub(
            r',\s*[A-ZÁÉÍÓÚÀÈÌÒÙÜÑ][a-záéíóúàèìòùüñ]+(?:\s+[A-ZÁÉÍÓÚÀÈÌÒÙÜÑ][a-záéíóúàèìòùüñ]+)+\s*$',
            '', place_name
        ).strip()
        place_display = re.sub(r'\d+\s*$', '', place_name).strip()
        if author.lower() in ('arxiu', ''):
            author = None
        return place_display, author
    if re.search(r'arxiu\s+aetib', base, re.IGNORECASE):
        place_name = re.sub(r'[\s,\-]*arxiu\s+aetib.*$', '', base, flags=re.IGNORECASE).strip()
        return place_name, None
    return None, None

def slugify(text):
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^\w\s-]", "", text)
    return re.sub(r"[\s_]+", "-", text).strip("-")

# ── Slugs a excluir (duplicados confirmados) ─────────────────────────────────
EXCLUDE_SLUGS = {
    "sestaca-valldemossa",                      # duplicado de sestaca
    "tramvia-soller",                           # duplicado de tramvia-de-soller
    "ses-paisses-arta-prque-natural-llevant",   # duplicado de ses-paisses-arta
    "cala-de-sa-font-celada",                   # duplicado de cala-sa-font-celada-parc-de-llevant
    "sa-font-celada",                           # duplicado de cala-sa-font-celada-parc-de-llevant
    "ermita-de-valldemossa",                    # duplicado de ermita-de-la-trinitat-valldemossa
    "far-bufador-soller",                       # duplicado de far-des-cap-gros-soller
}

# ── Agrupar fotos por lugar ───────────────────────────────────────────────────
def collect_photos():
    groups = defaultdict(list)
    for photo_dir in PHOTO_DIRS:
        if not photo_dir.exists():
            print(f"  ⚠️  Directorio no encontrado: {photo_dir}")
            continue
        for f in sorted(photo_dir.rglob("*-AETIB*.jpg")):
            name, author = parse_photo(f.name)
            if name and slugify(name) not in EXCLUDE_SLUGS:
                groups[name].append((f, author))
    return groups

# ── Deduplicar por slug ───────────────────────────────────────────────────────
def dedup_by_slug(places_dict):
    slug_map = {}
    for name, photos in sorted(places_dict.items()):
        slug = slugify(name)
        if slug not in slug_map:
            slug_map[slug] = (name, photos)
        else:
            existing_name, existing_photos = slug_map[slug]
            if len(photos) > len(existing_photos):
                slug_map[slug] = (name, photos)
                print(f"  🔁 Duplicado: '{existing_name}' → '{name}' (más fotos)")
            else:
                print(f"  🔁 Duplicado omitido: '{name}' (se usa '{existing_name}')")
    return {name: photos for name, photos in slug_map.values()}

# ── Comprobar existencia en Firestore ─────────────────────────────────────────
def existing_place_names(db):
    docs = db.collection("places").stream()
    names = set()
    for doc in docs:
        d = doc.to_dict()
        names.add(d.get("name", "").lower().strip())
        names.add(d.get("name_es", "").lower().strip())
    return names

# ── Geocodificación: KML primero, Nominatim como fallback ────────────────────
def geocode(place_name):
    slug_variants = list(dict.fromkeys([
        slugify(place_name),
        slugify(place_name.split(',')[0].strip()),
        slugify(place_name.rsplit(',', 1)[0].strip()),
    ]))
    for variant in slug_variants:
        if variant in KML_COORDS:
            return KML_COORDS[variant]

    clean = re.sub(r'\d+\s*$', '', place_name).strip()
    url = "https://nominatim.openstreetmap.org/search"
    headers = {"User-Agent": "mallorca-explorer-aetib-tool/1.0 (jose@mallorca-explorer.com)"}
    for query in [f"{clean}, Mallorca, Spain", clean]:
        params = {"q": query, "format": "json", "limit": 1, "countrycodes": "es"}
        try:
            time.sleep(1.2)
            r = requests.get(url, params=params, headers=headers, timeout=15)
            if r.status_code != 200 or not r.text.strip():
                continue
            data = r.json()
            if data:
                lat, lng = float(data[0]["lat"]), float(data[0]["lon"])
                if 39.2 <= lat <= 40.1 and 2.3 <= lng <= 3.6:
                    return lat, lng
                print(f"  ⚠️  Coords fuera de Mallorca para '{query}', ignorando")
        except Exception as e:
            print(f"  ⚠️  Geocodificación falló para '{query}': {e}")
    return None, None

# ── Subir foto a Firebase Storage ─────────────────────────────────────────────
def upload_photo(bucket, local_path: Path, place_slug: str, index: int) -> str:
    ext = local_path.suffix.lower()
    blob_name = f"{STORAGE_PREFIX}/{place_slug}/{index:02d}{ext}"
    blob = bucket.blob(blob_name)
    blob.upload_from_filename(str(local_path), content_type="image/jpeg")
    blob.make_public()
    return blob.public_url

# ── Cargar descripciones pregeneradas ────────────────────────────────────────
DESCRIPTIONS_FILE = Path(__file__).parent / "aetib_descriptions.json"
_descriptions_cache = None

def _normalize_key(text):
    return re.sub(r'\s+', ' ', text.lower().strip().replace(',', ' ')).strip()

def load_descriptions():
    global _descriptions_cache
    if _descriptions_cache is None:
        if DESCRIPTIONS_FILE.exists():
            with open(DESCRIPTIONS_FILE, encoding="utf-8") as f:
                data = json.load(f)
            _descriptions_cache = {_normalize_key(k): v for k, v in data.items()}
        else:
            _descriptions_cache = {}
    return _descriptions_cache

def generate_descriptions(place_name: str, lat: float, lng: float) -> dict:
    descs = load_descriptions()
    for key in [_normalize_key(place_name), _normalize_key(place_name.split(',')[0].strip())]:
        if key in descs:
            return descs[key]
    return {
        "name_en": place_name, "name_es": place_name, "name_de": place_name,
        "description_en": f"{place_name} is a remarkable place in Mallorca worth visiting.",
        "description_es": f"{place_name} es un lugar destacado de Mallorca que vale la pena visitar.",
        "description_de": f"{place_name} ist ein bemerkenswerter Ort auf Mallorca.",
        "category": "CULTURE", "municipality": "",
    }

# ── Crear documento en Firestore ──────────────────────────────────────────────
def create_place(db, place_id, desc, lat, lng, photo_docs):
    photos_json = [{"url": url, "source": "AETIB", "author": author} for url, author in photo_docs]
    doc = {
        "name":           desc.get("name_en", ""),
        "name_es":        desc.get("name_es", ""),
        "name_de":        desc.get("name_de", ""),
        "description":    desc.get("description_en", ""),
        "description_es": desc.get("description_es", ""),
        "description_de": desc.get("description_de", ""),
        "category":       desc.get("category", "CULTURE"),
        "municipality":   desc.get("municipality", ""),
        "latitude":       lat, "longitude": lng,
        "photo_urls":     photos_json,
        "thumbnail_url":  photos_json[0]["url"] if photos_json else "",
        "tips": [], "tips_es": [], "tips_de": [],
        "rating": None, "review_count": 0,
        "price_level": None, "website": None, "phone_number": None,
        "tags": ["aetib"],
        "last_updated": firestore.SERVER_TIMESTAMP,
    }
    db.collection("places").document(place_id).set(doc)
    return doc

# ── Main ──────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="No escribe nada")
    args = parser.parse_args()

    print("🔌 Conectando a Firebase...")
    db, bucket = init_firebase()

    print("📂 Leyendo fotos AETIB...")
    groups = collect_photos()
    print(f"   {len(groups)} lugares encontrados")

    print("🔁 Eliminando duplicados por slug...")
    groups = dedup_by_slug(groups)
    print(f"   {len(groups)} lugares únicos")

    print("🔍 Cargando lugares existentes en Firestore...")
    existing = existing_place_names(db)
    print(f"   {len(existing)} lugares ya existen en la app")

    new_places = {name: photos for name, photos in groups.items()
                  if name.lower().strip() not in existing}
    print(f"   {len(new_places)} lugares nuevos a crear\n")

    if not new_places:
        print("✅ No hay lugares nuevos que crear.")
        return

    results, skipped = [], []
    for place_name, photos in sorted(new_places.items()):
        print(f"━━ {place_name}")
        slug = slugify(place_name)
        lat, lng = geocode(place_name)
        if lat is None:
            print(f"  ⚠️  Sin coordenadas, omitiendo")
            skipped.append(place_name)
            continue
        print(f"  📍 {lat:.5f}, {lng:.5f}")

        selected = photos[:4]
        uploaded = []
        for i, (path, author) in enumerate(selected):
            if args.dry_run:
                url = f"https://storage.googleapis.com/{BUCKET_NAME}/{STORAGE_PREFIX}/{slug}/{i:02d}.jpg"
                print(f"  🖼  [DRY] {path.name}")
            else:
                print(f"  ⬆️  Subiendo {path.name}...")
                url = upload_photo(bucket, path, slug, i)
            uploaded.append((url, author))

        print(f"  🤖 Generando descripciones...")
        try:
            desc = generate_descriptions(place_name, lat, lng)
        except Exception as e:
            print(f"  ❌ Error: {e}")
            continue

        print(f"  📝 {desc.get('name_en')} | {desc.get('category')} | {desc.get('municipality')}")

        if not args.dry_run:
            create_place(db, slug, desc, lat, lng, uploaded)
            print(f"  ✅ Creado: places/{slug}")
        else:
            print(f"  ✅ [DRY] Se crearía: places/{slug}")

        results.append({"id": slug, "name": place_name, **desc, "lat": lat, "lng": lng})
        print()

    print(f"\n🎉 Procesados {len(results)} lugares nuevos.")
    if skipped:
        print(f"⏭️  Sin coords ({len(skipped)}): {', '.join(skipped)}")
    if args.dry_run:
        print("   (modo dry-run, no se ha escrito nada)")

    out = Path(__file__).parent / "aetib_places_created.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"📄 Resumen guardado en {out.name}")

if __name__ == "__main__":
    main()
