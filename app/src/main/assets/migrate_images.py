#!/usr/bin/env python3
"""
Migración de imágenes de assets/images/ a Firebase Storage
y actualización de URLs en Firestore.

Uso:
  # Subir imágenes:
  python3 migrate_images.py --key ~/Downloads/mallorca-explorer-49eb2-firebase-adminsdk-fbsvc-74014bdc2a.json

  # Actualizar Firestore con las nuevas URLs:
  python3 migrate_images.py --key ~/Downloads/... --update-firestore

  # Solo un lugar:
  python3 migrate_images.py --key ~/Downloads/... --place cala-romantica --update-firestore
"""

import os
import sys
import argparse
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, storage, firestore

BUCKET_NAME = "mallorca-explorer-49eb2.firebasestorage.app"
IMAGES_DIR = Path(__file__).parent / "images"
STORAGE_PREFIX = "places"
ASSET_PREFIX = "file:///android_asset/images/"
STORAGE_BASE_URL = f"https://storage.googleapis.com/{BUCKET_NAME}/{STORAGE_PREFIX}/"

def init_firebase(key_path):
    cred = credentials.Certificate(key_path)
    firebase_admin.initialize_app(cred, {"storageBucket": BUCKET_NAME})
    print(f"✅ Firebase conectado al bucket: {BUCKET_NAME}")

def upload_image(bucket, local_path: Path) -> str:
    """Sube una imagen y devuelve su URL pública."""
    blob_name = f"{STORAGE_PREFIX}/{local_path.name}"
    blob = bucket.blob(blob_name)
    content_type = "image/webp" if local_path.suffix == ".webp" else "image/jpeg"
    blob.upload_from_filename(str(local_path), content_type=content_type)
    blob.make_public()
    return blob.public_url

def asset_to_storage_url(asset_url: str) -> str:
    """Convierte file:///android_asset/images/foo.webp → URL de Firebase Storage."""
    filename = asset_url.replace(ASSET_PREFIX, "")
    # Cambiar .jpg/.jpeg a .webp
    if filename.endswith('.jpg') or filename.endswith('.jpeg'):
        filename = filename.rsplit('.', 1)[0] + '.webp'
    return STORAGE_BASE_URL + filename

def update_firestore(db, place_filter=None, dry_run=False):
    """Recorre todos los documentos de 'places' y actualiza las URLs de imágenes."""
    print("\n🔄 Actualizando Firestore...")
    places_ref = db.collection("places")
    docs = places_ref.stream()

    updated = 0
    skipped = 0
    errors = 0

    for doc in docs:
        data = doc.to_dict()
        doc_id = doc.id

        if place_filter and place_filter not in doc_id:
            continue

        changed = False
        new_data = {}

        # Actualizar thumbnail_url
        thumb = data.get("thumbnail_url", "")
        if thumb and thumb.startswith(ASSET_PREFIX):
            new_thumb = asset_to_storage_url(thumb)
            new_data["thumbnail_url"] = new_thumb
            changed = True
            if dry_run:
                print(f"  [DRY] {doc_id} thumbnail: {thumb.split('/')[-1]} → Storage")

        # Actualizar photo_urls (array de maps con campo 'url')
        photo_urls = data.get("photo_urls", [])
        new_photos = []
        photos_changed = False
        for photo in photo_urls:
            url = photo.get("url", "")
            if url and url.startswith(ASSET_PREFIX):
                new_url = asset_to_storage_url(url)
                new_photo = dict(photo)
                new_photo["url"] = new_url
                new_photos.append(new_photo)
                photos_changed = True
                if dry_run:
                    print(f"  [DRY] {doc_id} photo: {url.split('/')[-1]} → Storage")
            else:
                new_photos.append(photo)

        if photos_changed:
            new_data["photo_urls"] = new_photos
            changed = True

        if not changed:
            skipped += 1
            continue

        if dry_run:
            print(f"  [DRY-RUN] {doc_id}: {len(new_photos)} fotos + thumbnail actualizados")
            updated += 1
            continue

        try:
            places_ref.document(doc_id).update(new_data)
            print(f"  ✅ {doc_id}: URLs actualizadas")
            updated += 1
        except Exception as e:
            print(f"  ❌ {doc_id}: {e}")
            errors += 1

    print(f"\n{'[DRY-RUN] ' if dry_run else ''}✅ Actualizados: {updated} | ⏭️ Sin cambios: {skipped} | ❌ Errores: {errors}")

def main():
    parser = argparse.ArgumentParser(description="Migrar imágenes a Firebase Storage")
    parser.add_argument("--key", required=True, help="Ruta al archivo de Service Account JSON")
    parser.add_argument("--dry-run", action="store_true", help="Solo muestra lo que haría, sin hacer cambios")
    parser.add_argument("--place", help="Filtrar por lugar específico (ej: cala-romantica)")
    parser.add_argument("--update-firestore", action="store_true", help="Actualizar URLs en Firestore (sin subir imágenes)")
    args = parser.parse_args()

    if not Path(args.key).exists():
        print(f"❌ No se encuentra el archivo de clave: {args.key}")
        sys.exit(1)

    init_firebase(args.key)

    if args.update_firestore:
        db = firestore.client()
        update_firestore(db, place_filter=args.place, dry_run=args.dry_run)
        return

    # Modo subida de imágenes
    if not IMAGES_DIR.exists():
        print(f"❌ No se encuentra la carpeta de imágenes: {IMAGES_DIR}")
        sys.exit(1)

    bucket = storage.bucket()
    db = firestore.client()

    images = sorted([f for f in IMAGES_DIR.iterdir() if f.suffix in (".webp", ".jpg", ".jpeg")])

    if args.place:
        images = [f for f in images if args.place in f.name]
        print(f"🔍 Filtrando por lugar: {args.place} → {len(images)} imágenes")

    print(f"\n📁 Total imágenes a procesar: {len(images)}")
    print("=" * 60)

    uploaded = 0
    errors = 0

    for img_path in images:
        filename = img_path.name

        if args.dry_run:
            print(f"[DRY-RUN] {filename} → gs://{BUCKET_NAME}/{STORAGE_PREFIX}/{filename}")
            continue

        try:
            url = upload_image(bucket, img_path)
            print(f"✅ {filename}")
            print(f"   → {url}")
            uploaded += 1
        except Exception as e:
            print(f"❌ {filename}: {e}")
            errors += 1

    print("\n" + "=" * 60)
    if args.dry_run:
        print(f"[DRY-RUN] Se procesarían {len(images)} imágenes")
    else:
        print(f"✅ Subidas: {uploaded} | ❌ Errores: {errors}")
        if errors == 0:
            print(f"\n🎉 ¡Listo! Ahora actualiza Firestore:")
            place_arg = f" --place {args.place}" if args.place else ""
            print(f"   python3 migrate_images.py --key TU_CLAVE{place_arg} --update-firestore --dry-run")

if __name__ == "__main__":
    main()
