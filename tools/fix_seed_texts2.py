#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Correccion de textos puntual (tanda 2). SOLO texto.
  1) Cami des Correu: linea de bus L200 -> L202 (place cami-des-correu + itin-hike-cami-des-correu).
  2) "Cala Romantica"/"Cala Romàntica" -> "Cala Romántica" (grafia canonica del place cala-romantica,
     acute, igual en es/en/de), en TODO el seed (textos; no ids/slugs/URLs). Incluye el termino de
     busqueda de Wikiloc "Cala Varques desde Cala Romantica".
  3) DE: "ss" -> "ß" en palabras claramente mal escritas (Fuß, Straße, groß, heiß, weiß y flexiones),
     solo en campos DE. No toca toponimos ni palabras que llevan "ss" correcto (muss, Fluss, dass...).
  4) CURRENT_SEED_VERSION / version / seed_version 418 -> 419 (+ SeedDataWorker.kt).

NO toca geometria, lat/lng, tipos de waypoint, distancias, desniveles, duraciones, ids ni fotos.
Idempotente (reemplazos de subcadena; al reejecutar ya no encuentra las formas antiguas).
"""
import json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "app/src/main/assets/seed_data.json")
WORKER = os.path.join(ROOT, "core/core-data/src/main/kotlin/com/mallorca/explorer/core/data/sync/SeedDataWorker.kt")
OLD_V, NEW_V = 418, 419

CANON = "Cala Romántica"  # grafia del place cala-romantica (acute, es=en=de)

# -- Romantica: formas a normalizar (capital R; los ids/slugs son minusculas y no se tocan)
ROMANTICA_PAIRS = [
    ("Cala Romàntica", CANON),
    ("Cala Romantica", CANON),
]

# -- DE ss->ß (palabras largas/diptongo). Orden: formas largas primero. Solo campos DE.
DE_SS_PAIRS = [
    ("zu Fuss", "zu Fuß"),
    ("Strassen", "Straßen"),
    ("Strasse", "Straße"),
    ("weissen", "weißen"), ("weisser", "weißer"), ("weisses", "weißes"),
    ("weisse", "weiße"), ("weiss", "weiß"),
    ("grossen", "großen"), ("grosser", "großer"), ("grosses", "großes"),
    ("grossem", "großem"), ("grosse", "große"), ("gross", "groß"),
    ("heisse", "heiße"), ("heiss", "heiß"),
    ("draussen", "draußen"), ("Spass", "Spaß"),
]

BUS_PAIRS = [  # solo dentro del place/itin de Correu
    ("TIB bus L200", "TIB bus L202"),
    ("TIB L200", "TIB L202"),
    ("TIB-Bus L200", "TIB-Bus L202"),
    ("L-200", "L202"), ("L 200", "L202"), ("L200", "L202"),
    ("Línea 200", "Línea 202"), ("Linea 200", "Linea 202"),
    ("Line 200", "Line 202"), ("Linie 200", "Linie 202"),
]

SKIP = re.compile(r'(/id$|url|photo|thumbnail|slug)', re.I)

log = {"romantica": 0, "bus": 0, "de_ss": 0, "de_ss_detail": [], "rom_detail": []}

def is_de(path):
    return ("_de" in path) or ("/de/" in path)

def normalize_value(path, s):
    if not isinstance(s, str) or SKIP.search(path):
        return s
    # Romantica (todo campo de texto)
    for old, new in ROMANTICA_PAIRS:
        if old in s:
            n = s.count(old); s = s.replace(old, new)
            log["romantica"] += n
            if path not in log["rom_detail"]:
                log["rom_detail"].append(path)
    # DE ss->ß (solo campos DE), con limite de palabra para no partir compuestos como 'muss'
    if is_de(path):
        for old, new in DE_SS_PAIRS:
            pat = re.compile(r'(?<![A-Za-zÀ-ÿ])' + re.escape(old) + r'(?![A-Za-zÀ-ÿ])')
            def repl(m):
                log["de_ss"] += 1
                log["de_ss_detail"].append((path, old, new))
                return new
            s = pat.sub(repl, s)
    return s

def walk_apply(o, p=''):
    if isinstance(o, dict):
        return {k: walk_apply(v, p + '/' + str(k)) for k, v in o.items()}
    if isinstance(o, list):
        return [walk_apply(v, p + '[' + str(i) + ']') for i, v in enumerate(o)]
    if isinstance(o, str):
        return normalize_value(p, o)
    return o

def fix_bus(obj):
    def recurse(o):
        if isinstance(o, dict):
            for k in o:
                o[k] = recurse(o[k])
            return o
        if isinstance(o, list):
            return [recurse(x) for x in o]
        if isinstance(o, str):
            s = o
            for old, new in BUS_PAIRS:
                if old in s:
                    log["bus"] += s.count(old); s = s.replace(old, new)
            return s
        return o
    return recurse(obj)

def main():
    data = json.load(open(SEED, encoding="utf-8"))

    # 1) bus line: SOLO en el place y el itinerario de Correu
    for pl in data["places"]:
        if pl["id"] == "cami-des-correu":
            fix_bus(pl)
    for it in data["itineraries"]:
        if it["id"] == "itin-hike-cami-des-correu":
            fix_bus(it)

    # 2) + 3) Romantica (seed-wide) y DE ss->ß (campos DE)
    data = walk_apply(data)

    # 4) version bump
    for k in ("CURRENT_SEED_VERSION", "version", "seed_version"):
        if k in data:
            if data[k] == OLD_V:
                data[k] = NEW_V
            elif data[k] != NEW_V:
                print(f"  !! AVISO: {k}={data[k]} (esperaba {OLD_V}/{NEW_V})")

    json.dump(data, open(SEED, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    src = open(WORKER, encoding="utf-8").read()
    new = re.sub(r"(CURRENT_SEED_VERSION\s*=\s*)%d\b" % OLD_V, r"\g<1>%d" % NEW_V, src, count=1)
    if new != src:
        open(WORKER, "w", encoding="utf-8").write(new); worker = f"{OLD_V} -> {NEW_V}"
    elif re.search(r"CURRENT_SEED_VERSION\s*=\s*%d\b" % NEW_V, src):
        worker = f"ya en {NEW_V}"
    else:
        worker = "NO ENCONTRADO"

    print("Romantica reemplazos:", log["romantica"], "en", len(log["rom_detail"]), "campos")
    print("Bus L200->L202 reemplazos:", log["bus"])
    print("DE ss->ß reemplazos:", log["de_ss"])
    for d in log["de_ss_detail"]:
        print("   ", d[1], "->", d[2], "@", d[0])
    print("versiones JSON:", data["CURRENT_SEED_VERSION"], data.get("version"), data.get("seed_version"))
    print("SeedDataWorker.kt:", worker)

if __name__ == "__main__":
    main()
