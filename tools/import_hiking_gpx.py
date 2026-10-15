#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Importa las rutas de senderismo (Hiking) desde los GPX dibujados por el usuario en gpx.studio
(tools/gpx-senderismo/) a itinerarios del seed (app/src/main/assets/seed_data.json), siguiendo
la misma lógica que las rutas SUP (un itinerario por ruta, enlazado a su ficha de lugar).

Fuente de trazados: SOLO los GPX de gpx.studio. Nunca Wikiloc u otras fuentes.
Idiomas: SOLO ES/EN/DE.

Idempotente: reemplaza el itinerario por id si ya existe (vuelve a ejecutarse con un GPX
actualizado sin duplicar). Las traducciones estables viven en tools/hiking_translations.json.

Uso:
  python3 tools/import_hiking_gpx.py --analyze     # solo imprime el análisis geométrico (no escribe)
  python3 tools/import_hiking_gpx.py --write       # copia de seguridad + inyecta en el seed + sube versión
"""
import os, sys, json, math, glob, datetime, unicodedata, re
import xml.etree.ElementTree as ET

ROOT = os.path.expanduser("~/Code/mallorca-explorer")
GPXDIR = os.path.join(ROOT, "tools", "gpx-senderismo")
SEED = os.path.join(ROOT, "app/src/main/assets/seed_data.json")
WORKER = os.path.join(ROOT, "core/core-data/src/main/kotlin/com/mallorca/explorer/core/data/sync/SeedDataWorker.kt")
TRANS = os.path.join(ROOT, "tools", "hiking_translations.json")
OVERRIDES = os.path.join(ROOT, "tools", "hiking_overrides.json")
NS = {"g": "http://www.topografix.com/GPX/1/1"}

# -------- Notas cortas (es/en/de) de los waypoints de la Dragonera (keyed por nombre limpio) --------
DRAGONERA_NOTES = {
 "Salida y llegada": {
   "es": "Muelle de desembarco de las barcas desde Sant Elm, junto a la antigua casa forestal; salida y llegada de las rutas y centro de visitantes.",
   "en": "Landing jetty for the boats from Sant Elm, by the old forestry house; start and finish of the trails and visitor centre.",
   "de": "Anlegestelle der Boote von Sant Elm, beim alten Forsthaus; Start und Ziel der Wege und Besucherzentrum."},
 "Centro de Información de sa Dragonera": {
   "es": "Punto de recepción en la casa forestal de Cala Lladó, con mapas e información del parque natural.",
   "en": "Reception point in the Cala Lladó forestry house, with maps and information about the natural park.",
   "de": "Empfangspunkt im Forsthaus von Cala Lladó, mit Karten und Informationen zum Naturpark."},
 "Mirador de los Chiqueros (Es Tancat)": {
   "es": "Mirador al inicio de la subida a Na Pòpia, entre muros de piedra seca y antiguos corrales.",
   "en": "Viewpoint at the start of the climb to Na Pòpia, among dry-stone walls and old animal pens.",
   "de": "Aussichtspunkt am Beginn des Aufstiegs nach Na Pòpia, zwischen Trockenmauern und alten Pferchen."},
 "Mirador de na Miranda": {
   "es": "Mirador con terraza de piedra y zona de pícnic en el itinerario circular más corto y fácil.",
   "en": "Viewpoint with a stone terrace and picnic area on the shortest, easiest loop.",
   "de": "Aussichtspunkt mit Steinterrasse und Picknickplatz auf der kürzesten, leichtesten Runde."},
 "Far de Tramuntana": {
   "es": "Faro activo en el extremo nororiental de la isla, final de una de las rutas señalizadas.",
   "en": "Working lighthouse at the island's north-eastern tip, the end of one of the waymarked trails.",
   "de": "Aktiver Leuchtturm an der Nordostspitze der Insel, Ziel einer der markierten Routen."},
 "Coll de Tramuntana (Sa Dragonera)": {
   "es": "Collado en la pista hacia el Far de Tramuntana que separa las dos vertientes del islote.",
   "en": "Saddle on the track to the Far de Tramuntana, dividing the two sides of the islet.",
   "de": "Sattel am Weg zum Far de Tramuntana, der die beiden Seiten der Insel trennt."},
 "Mirador de aves del Far de Tramuntana": {
   "es": "Pequeño mirador con paneles, buen punto para observar aves cerca del Far de Tramuntana.",
   "en": "Small viewpoint with panels, a good spot for birdwatching near the Far de Tramuntana.",
   "de": "Kleiner Aussichtspunkt mit Tafeln, guter Ort zur Vogelbeobachtung nahe dem Far de Tramuntana."},
 "Marcador del Meridiano de París (Sa Dragonera)": {
   "es": "Placa que señala el paso del antiguo meridiano de París, en el camino hacia el Far de Tramuntana.",
   "en": "Plaque marking the old Paris meridian, on the path towards the Far de Tramuntana.",
   "de": "Tafel, die den alten Pariser Meridian markiert, am Weg zum Far de Tramuntana."},
 "Portillo del Camí del Collet (Sa Dragonera)": {
   "es": "Portillo de madera en el sendero: déjalo cerrado tras pasar para proteger el parque.",
   "en": "Wooden gate on the path: leave it closed behind you to protect the park.",
   "de": "Holztor am Weg: nach dem Durchgang geschlossen lassen, um den Park zu schützen."},
 "Torre de es Colomer (El Palomar)": {
   "es": "Torre cilíndrica de piedra del siglo XX, usada como palomar, junto al camino.",
   "en": "20th-century cylindrical stone tower, used as a dovecote, beside the path.",
   "de": "Zylindrischer Steinturm aus dem 20. Jahrhundert, als Taubenhaus genutzt, am Weg."},
 "Portillo de es Tancat (Sa Dragonera)": {
   "es": "Portillo de madera que da paso a los antiguos campos de Es Tancat: déjalo cerrado.",
   "en": "Wooden gate into the old fields of Es Tancat: leave it closed.",
   "de": "Holztor zu den alten Feldern von Es Tancat: geschlossen lassen."},
 "Caseta Agrícola de es Tancat (Sa Dragonera)": {
   "es": "Pequeña caseta de piedra seca en la llanura agrícola recuperada de Es Tancat.",
   "en": "Small dry-stone hut on the restored farmland of Es Tancat.",
   "de": "Kleine Trockensteinhütte auf dem wiederhergestellten Ackerland von Es Tancat."},
 "Coll Roig (Sa Dragonera)": {
   "es": "Collado y mirador en el camino de herradura que sube a Na Pòpia, con barandilla.",
   "en": "Saddle and viewpoint on the bridle path up to Na Pòpia, with a railing.",
   "de": "Sattel und Aussichtspunkt am Saumpfad hinauf nach Na Pòpia, mit Geländer."},
 "Portillo des Camí de Na Pòpia": {
   "es": "Portillo de madera tras el Coll Roig, en la subida a la cima: déjalo cerrado.",
   "en": "Wooden gate after Coll Roig, on the climb to the summit: leave it closed.",
   "de": "Holztor nach dem Coll Roig, am Aufstieg zum Gipfel: geschlossen lassen."},
 "Mirador des Coll Roig (Sa Dragonera)": {
   "es": "Mirador con barandilla en el camino a Na Pòpia, con vistas verticales al mar.",
   "en": "Railed viewpoint on the way to Na Pòpia, with sheer views down to the sea.",
   "de": "Aussichtspunkt mit Geländer auf dem Weg nach Na Pòpia, mit senkrechtem Blick aufs Meer."},
 "Caseta des Camí des Far de Llebeig": {
   "es": "Caseta rústica de piedra junto al camino hacia el Far de Llebeig.",
   "en": "Rustic stone hut beside the path to the Far de Llebeig.",
   "de": "Rustikale Steinhütte am Weg zum Far de Llebeig."},
 "Ruinas del Far de Na Pòpia": {
   "es": "Restos del Faro Viejo (inaugurado en 1850), en el punto más alto de la isla, Na Pòpia (352 m).",
   "en": "Remains of the Old Lighthouse (opened 1850), at the island's highest point, Na Pòpia (352 m).",
   "de": "Reste des alten Leuchtturms (eröffnet 1850), am höchsten Punkt der Insel, Na Pòpia (352 m)."},
 "Cima de Na Pòpia y Vistas Panorámicas": {
   "es": "Cima de Na Pòpia (352 m), el punto más alto de la isla, con la panorámica más amplia.",
   "en": "Summit of Na Pòpia (352 m), the island's highest point, with the widest panorama.",
   "de": "Gipfel Na Pòpia (352 m), höchster Punkt der Insel, mit dem weitesten Panorama."},
 "Panoràmica al Puig dels Aucells": {
   "es": "Mirador en la ruta hacia el Far de Llebeig, con vista despejada del Puig dels Aucells.",
   "en": "Viewpoint on the route to the Far de Llebeig, with a clear view of Puig dels Aucells.",
   "de": "Aussichtspunkt auf dem Weg zum Far de Llebeig, mit freiem Blick auf den Puig dels Aucells."},
 "S'Abeurador (Sa Dragonera)": {
   "es": "Antiguo aljibe de piedra para recoger agua de lluvia, junto al sendero (no es agua potable).",
   "en": "Old stone cistern for collecting rainwater, beside the path (not drinking water).",
   "de": "Alte Steinzisterne zum Sammeln von Regenwasser, am Weg (kein Trinkwasser)."},
 "Forn de Calç (Horno de Cal de sa Dragonera)": {
   "es": "Horno de cal de piedra restaurado, un elemento etnográfico junto al camino.",
   "en": "Restored stone lime kiln, an ethnographic feature beside the path.",
   "de": "Restaurierter steinerner Kalkofen, ein ethnografisches Element am Weg."},
 "Far de Llebeig": {
   "es": "Faro histórico del extremo suroccidental (inaugurado en 1910), final de una de las rutas.",
   "en": "Historic lighthouse at the south-western tip (opened 1910), the end of one of the trails.",
   "de": "Historischer Leuchtturm an der Südwestspitze (eröffnet 1910), Ziel einer der Routen."},
 "Torre de Llebeig": {
   "es": "Torre de defensa del siglo XVI (1585) sobre la costa suroccidental, restaurada.",
   "en": "16th-century defence tower (1585) above the south-western coast, restored.",
   "de": "Verteidigungsturm aus dem 16. Jahrhundert (1585) über der Südwestküste, restauriert."},
}

DRAGONERA_TYPES = {
 "Portillo del Camí del Collet (Sa Dragonera)": "WARNING",
 "Portillo de es Tancat (Sa Dragonera)": "WARNING",
 "Portillo des Camí de Na Pòpia": "WARNING",
 "Cima de Na Pòpia y Vistas Panorámicas": "SUMMIT",
 "Panoràmica al Puig dels Aucells": "VIEWPOINT",
 "S'Abeurador (Sa Dragonera)": "OTHER",
 "Centro de Información de sa Dragonera": "OTHER",
 "Marcador del Meridiano de París (Sa Dragonera)": "OTHER",
}

# -------- Config por ruta (archivo GPX -> destino) --------
# type: CIRCULAR / LINEAR / OUT_AND_BACK ; dist_override: km exactos que manda el usuario
ROUTES = [
    dict(file="Avenc de Son Pou.gpx",        place="avenc-de-son-pou",      type="OUT_AND_BACK", diff="MODERATE", dur="5 h"),
    dict(file="Barranc de Biniaraix.gpx",    place="barranc-biniaraix",     type="OUT_AND_BACK", diff="MODERATE", dur="2,5 h", chain=True),
    dict(file="Cala en Basset y torre.gpx",  place="cala-en-basset",        type="CIRCULAR",     diff="EASY",     dur="2 h"),
    dict(file="Camí de s´arxiduc .gpx",      place="cami-arxiduc",          type="CIRCULAR",     diff="MODERATE", dur="6 h", access_warning=True),
    dict(file="Camí des Correu.gpx",         place="cami-des-correu",       type="OUT_AND_BACK", diff="EASY",     dur="4 h"),
    dict(file="Castell d´Alaró.gpx",         place="castell-alaro",         type="CIRCULAR",     diff="MODERATE", dur="3 h"),
    dict(file="Far cap de ses Salines.gpx",  place="faro-ses-salines",      type="OUT_AND_BACK", diff="EASY",     dur="4,5 h", mirror=True, dist_override=10.00,
         extra_from=("Camí des Correu.gpx", ["Far de Ses Salines", "Parking (inicio)", "Cala Marmols"])),
    dict(file="Port des Canonge (Camí de sa Volta des General).gpx", place="hike-port-des-canonge", type="OUT_AND_BACK", diff="EASY", dur="2,5 h"),
    dict(file="Puig Tomir.gpx",              place="puig-tomir",            type="OUT_AND_BACK", diff="HARD",     dur="6 h", mirror=True, dist_override=9.54),
    dict(file="Puig de l´Ofre.gpx",          place="puig-ofre",             type="OUT_AND_BACK", diff="HARD",     dur="5,5 h", use_segment=1, dist_override=13.14, no_turnaround=True),
    dict(file="Ruta a Platja des Coll Baix.gpx", place="ruta-coll-baix",    type="OUT_AND_BACK", diff="EASY",     dur="1,5 h"),
    dict(file="Ruta desde cala Estreta.gpx", place="ruta-cala-estreta",     type="LINEAR",       diff="EASY",     dur="1,5 h"),
    dict(file="Salt des Freu.gpx",           place="salt-des-freu",         type="CIRCULAR",     diff="EASY",     dur="2 h"),
    # --- Tanda 2 (2026-10-15): 6 rutas nuevas ---
    dict(file="Cala Bóquer .gpx",            place="ruta-cala-boquer",      type="OUT_AND_BACK", diff="EASY",     dur="2,5 h"),
    dict(file="Cami Vell a Lluc.gpx",        place="cami-vell-de-lluc",     type="OUT_AND_BACK", diff="MODERATE", dur="5,5 h",
         no_turnaround=True, fuse_start_end=True,
         type_override={"Sa coveta Negra": "OTHER"}),
    dict(file="Fonts Ufanes .gpx",           place="fonts-ufanes",          type="CIRCULAR",     diff="EASY",     dur="1,5 h",
         fuse_start_end=True,
         rename_wpts={"Punto pular para hacer fotos": "Punto para fotos"},
         type_override={"Punto para fotos": "PHOTO", "Coves de Campanet": "CAVE",
                        "Torrent de Teló": "OTHER", "torrent de Sant Miquel": "OTHER", "Siquia": "OTHER",
                        "Baños Públicos (Fonts Ufanes)": "OTHER",
                        "Centro d´interpretación de le Fonts Ufanes": "OTHER"},
         note_override={"Punto para fotos": {"es": "Punto con buenas vistas para hacer fotos del paisaje de Campanet.",
                                             "en": "A good spot for photos of the Campanet landscape.",
                                             "de": "Ein guter Ort für Fotos der Landschaft von Campanet."}}),
    dict(file="La Comuna de Bunyola.gpx",    place="la-comuna-bunyola",     type="CIRCULAR",     diff="MODERATE", dur="4,5 h",
         fuse_start_end=True,
         fuse_wpts=[["Comellar d'en Cupí", "Ruinas del Forn d'es Comellar d'en Cupí"]],
         type_override={"Depósito de agua contra incendios": "OTHER",
                        "Intersección Penyal d'Honor - Cas Garriguer (Cruce superior)": "OTHER"}),
    dict(file="Ruta a Cala Tuent.gpx",       place="ruta-cala-tuent",       type="LINEAR",       diff="MODERATE", dur="3 h",
         fuse_wpts=[["Camino empedrado tradicional (GR-221)", "Camino empedrado tradicional"]],
         type_override={"Torrent de na Mora": "OTHER"}),
    dict(file="Ruta a Cala Varques .gpx",    place="ruta-cala-varques",     type="CIRCULAR",     diff="MODERATE", dur="4 h",
         fuse_start_end=True,
         extra_from=("Ruta a Cala Tuent.gpx", ["Aparcamiento de Cala Romàntica- Inicio", "Cova de ses Pedreres",
                     "Cala Romàntica", "Coco Beach Bar (Cala Romàntica)", "Punto Fotográfico Destacado", "Cova dels Llimacs"]),
         type_override={"Coco Beach Bar (Cala Romàntica)": "OTHER", "Punto Fotográfico Destacado": "PHOTO"},
         note_override={"Punto Fotográfico Destacado": {"es": "Punto con una vista destacada del litoral para hacer fotos.",
                                                        "en": "A spot with a standout coastal view for photos.",
                                                        "de": "Ein Ort mit herausragendem Küstenblick für Fotos."}}),
    # --- Dragonera (2026-10-15): el Info dice "Ida y Vuelta" pero el trazado es un circuito
    #     (espejo p95 ~2190 m, no llega al 95%) -> guarda el track COMPLETO; salida y llegada
    #     coinciden (73 m) -> marcador único "Salida y llegada" y SIN punto de giro (no_turnaround). ---
    dict(file="Isla de la Dragonera .gpx", place="illa-sa-dragonera", type="OUT_AND_BACK", diff="MODERATE", dur="4,5 h",
         no_turnaround=True, fuse_start_end=True, access_warning=True, project_full=True,
         rename_wpts={"c Torre de es Colomer (El Palomar)": "Torre de es Colomer (El Palomar)"},
         type_override=DRAGONERA_TYPES,
         note_override=DRAGONERA_NOTES),
]

WPT_FAR_FROM_TRACK_M = 1000.0   # waypoints a más de 1000 m del trazado -> descartar

# -------- Geometría --------
def hav(a, b):
    R = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    dla, dlo = la2 - la1, lo2 - lo1
    h = math.sin(dla/2)**2 + math.cos(la1)*math.cos(la2)*math.sin(dlo/2)**2
    return 2*R*math.asin(math.sqrt(h))

def track_len_m(pts):
    return sum(hav(pts[i], pts[i+1]) for i in range(len(pts)-1))

def cum_dist(pts):
    c = [0.0]
    for i in range(len(pts)-1):
        c.append(c[-1] + hav(pts[i], pts[i+1]))
    return c

# Proyección equirectangular local (metros) referida a lat0
def to_xy(p, lat0):
    R = 6371000.0
    x = math.radians(p[1]) * math.cos(math.radians(lat0)) * R
    y = math.radians(p[0]) * R
    return (x, y)

def pt_seg_dist_xy(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx-ax, by-ay
    if dx == 0 and dy == 0:
        return math.hypot(px-ax, py-ay), 0.0
    t = ((px-ax)*dx + (py-ay)*dy) / (dx*dx + dy*dy)
    t = max(0.0, min(1.0, t))
    cx, cy = ax + t*dx, ay + t*dy
    return math.hypot(px-cx, py-cy), t

def project_on_track(p, pts, lat0, cum):
    """Distancia mínima (m) del punto al track y arc-length (m) de la proyección."""
    pxy = to_xy(p, lat0)
    best_d, best_s = float("inf"), 0.0
    for i in range(len(pts)-1):
        a = to_xy(pts[i], lat0); b = to_xy(pts[i+1], lat0)
        d, t = pt_seg_dist_xy(pxy, a, b)
        if d < best_d:
            seg = hav(pts[i], pts[i+1])
            best_d, best_s = d, cum[i] + t*seg
    return best_d, best_s

def rdp(pts, tol_m, lat0):
    """Douglas-Peucker en metros (proyección equirectangular local)."""
    if len(pts) < 3:
        return pts[:]
    xy = [to_xy(p, lat0) for p in pts]
    keep = [False]*len(pts); keep[0] = keep[-1] = True
    stack = [(0, len(pts)-1)]
    while stack:
        i0, i1 = stack.pop()
        a, b = xy[i0], xy[i1]
        dmax, idx = -1.0, -1
        for i in range(i0+1, i1):
            d, _ = pt_seg_dist_xy(xy[i], a, b)
            if d > dmax:
                dmax, idx = d, i
        if dmax > tol_m and idx != -1:
            keep[idx] = True
            stack.append((i0, idx)); stack.append((idx, i1))
    return [pts[i] for i in range(len(pts)) if keep[i]]

def elevation_gain_loss(pts, thr=3.0):
    """Subida/bajada acumuladas (m enteros) sobre puntos originales con filtro de histéresis."""
    gain = loss = 0.0
    ref = pts[0][2]
    for p in pts[1:]:
        dz = p[2] - ref
        if abs(dz) >= thr:
            if dz > 0: gain += dz
            else: loss += -dz
            ref = p[2]
    return int(round(gain)), int(round(loss))

def mirror_metric(pts, cum):
    """Devuelve (mediana, p95) de la distancia entre la ida y la vuelta-espejo (m)."""
    total = cum[-1]
    if total <= 0: return (9e9, 9e9)
    half = total/2.0
    # interpola un punto a una arc-length s dada
    def at(s):
        for i in range(len(pts)-1):
            if cum[i] <= s <= cum[i+1]:
                seg = cum[i+1]-cum[i]
                t = 0 if seg == 0 else (s-cum[i])/seg
                return (pts[i][0]+t*(pts[i+1][0]-pts[i][0]), pts[i][1]+t*(pts[i+1][1]-pts[i][1]))
        return (pts[-1][0], pts[-1][1])
    K = 60
    ds = []
    for k in range(K+1):
        s = half*k/K
        a = at(s); b = at(total - s)
        ds.append(hav(a, b))
    ds.sort()
    return (ds[len(ds)//2], ds[int(0.95*len(ds))])

def split_ida(pts):
    """Para rutas de ida y vuelta: conserva solo la IDA, cortando en el punto más lejano
    (geográficamente) de la salida. Si el track ya es ida-sola, el punto lejano es el final
    y se conserva entero."""
    start = pts[0]
    ti = max(range(len(pts)), key=lambda i: hav(pts[i], start))
    return pts[:ti+1] if ti >= 2 else pts[:]

# -------- Parseo GPX --------
def txt(e):
    return (e.text or "").strip() if e is not None else ""

def parse_gpx(path):
    root = ET.parse(path).getroot()
    meta = root.find("g:metadata", NS)
    name = txt(meta.find("g:name", NS)) if meta is not None else ""
    desc = txt(meta.find("g:desc", NS)) if meta is not None else ""
    wpts = []
    for w in root.findall("g:wpt", NS):
        wpts.append(dict(lat=float(w.get("lat")), lon=float(w.get("lon")),
                         name=txt(w.find("g:name", NS)), cmt=txt(w.find("g:cmt", NS)),
                         desc=txt(w.find("g:desc", NS)), sym=txt(w.find("g:sym", NS))))
    segs = []
    for trk in root.findall("g:trk", NS):
        for seg in trk.findall("g:trkseg", NS):
            pts = [(float(p.get("lat")), float(p.get("lon")),
                    float(txt(p.find("g:ele", NS)) or 0)) for p in seg.findall("g:trkpt", NS)]
            if pts: segs.append(pts)
    return dict(name=name, desc=desc, wpts=wpts, segs=segs)

def chain_segments(segs):
    """Une los segmentos en una sola línea continua. gpx.studio los exporta contiguos en orden
    de archivo (el fin de uno = el inicio del siguiente); se concatenan en ese orden quitando el
    punto repetido en la unión. Si un segmento no encaja por ningún extremo (hueco), se añade
    igualmente a continuación (se avisa en el análisis)."""
    segs = [s[:] for s in segs if s]
    if not segs: return []
    chain = segs[0][:]
    for s in segs[1:]:
        # ¿encaja por el final de la cadena? (tolerancia 5 m)
        if hav(chain[-1], s[0]) <= 5: chain += s[1:]
        elif hav(chain[-1], s[-1]) <= 5: chain += s[::-1][1:]
        elif hav(chain[0], s[-1]) <= 5: chain = s[:-1] + chain
        elif hav(chain[0], s[0]) <= 5: chain = s[::-1][:-1] + chain
        else: chain += s   # hueco: concatena de todas formas
    return chain

# -------- Clasificación de waypoints --------
def strip_accents(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn").lower()

def norm(s):
    """Normaliza espacios/saltos de línea para usar como clave de traducción estable."""
    return " ".join((s or "").split())

_EMOJI_EXTRA = {"ℹ", "‍", "️"}  # ℹ (categoría Ll), ZWJ, VS16
def clean_wpt_name(s):
    """Quita emojis/símbolos (🏁 ℹ️ 🧱 👁️ 🗼 🏔️ 🌐 🚪 🗿 🛖 ❌ 💧…) y el prefijo 'Cruce:' del nombre."""
    s = "".join(ch for ch in s if unicodedata.category(ch) not in ("So", "Sk", "Cf") and ch not in _EMOJI_EXTRA)
    s = re.sub(r"^\s*Cruce\s*:\s*", "", s)
    return " ".join(s.split()).strip()

def strip_citations(s):
    """Elimina marcas de cita copiadas: [1], [1.1], [1.2, 1.2]… ([n] o [n.m, n.m])."""
    return " ".join(re.sub(r"\s*\[\d+(?:\.\d+)?(?:\s*,\s*\d+(?:\.\d+)?)*\]", "", s or "").split())

SYM_TYPE = {
    "parking area": "PARKING", "water source": "WATER", "drinking water": "WATER",
    "beach": "BEACH", "binoculars": "VIEWPOINT", "summit": "VIEWPOINT",
    "building": "HERITAGE", "lodge": "HERITAGE", "lodging": "HERITAGE", "forest": "HERITAGE",
    "tunnel": "CAVE", "restaurant": "OTHER", "bridge": "OTHER", "crossing": "OTHER",
    "road": "OTHER", "information": "OTHER", "alert": "WARNING",
}

def classify_type(name, sym, role):
    """Tipo por rol > nombre (palabras clave) > símbolo del GPX. No usa la nota larga
    (evita falsos positivos por menciones en prosa, p. ej. 'Avenc' dentro de un texto)."""
    if role == "LAUNCH": return "START"
    if role == "FINISH": return "END"
    n = strip_accents(name)
    def has(*ks): return any(k in n for k in ks)
    # 1) override por nombre (prioridad descendente)
    if has("aviso de acceso", "paso privado", "autorizacion", "permiso"): return "WARNING"
    if has("parada de bus", "parada de autobus", "parada de autobús") or n.split()[:1] == ["bus"]: return "BUS"
    if has("para hacer fotos"): return "PHOTO"
    if has("pas de", "pas d'", "paso tecnico", "paso técnico", "paso equipado", "barrera", "botador", "cadena"): return "TECHNICAL"
    if has("cova", "cueva", "avenc", "gruta"): return "CAVE"
    if has("mirador", "panoram", "observacion"): return "VIEWPOINT"
    if has("font", "fuente", "aljibe", "pou salat", "rentador"): return "WATER"
    if has("parking", "aparcamiento"): return "PARKING"
    if has("platja", "playa", "cala", "arenalet"): return "BEACH"
    if has("torre", "castell", "castillo", "ermita", "esglesia", "forn de", "sitges", "refugi",
            "hostatgeria", "casa", "cases ", "pi de", "talaia", "vertice", "funicular", "plataforma",
            "barraca", "centre forestal", "alzina"): return "HERITAGE"
    if has("coll", "puig", "cim", "cumbre"): return "VIEWPOINT"
    # 2) por símbolo del GPX
    return SYM_TYPE.get(sym.lower(), "OTHER")

def name_is_inicio(name):
    n = strip_accents(name)
    return "inicio" in n or "inici " in n or n == "inici" or "salida" in n

def name_is_finish(name):
    n = strip_accents(name)
    return any(k in n for k in ("llegada", "destino", "final", "regreso"))

# correcciones de erratas evidentes en nombres
NAME_FIX = {
    "SÁvenc de Son Pou (Destino final)": "Avenc de Son Pou (Destino final)",
    "Torre de Cala en Basse": "Torre de Cala en Basset",
    "Punto pupular para hacer fotos": "Punto popular para hacer fotos",
    "Desvio a Orient y Salt des Freu": "Desvío a Orient y Salt des Freu",
    "Son Roig de Coanegre": "Son Roig de Coanegra",
    "Font de ses tresPiques y sus dos Estanques": "Font de ses Tres Piques y sus dos estanques",
    "Inicio- restauran Orient Ca´n Tomeu": "Inicio - Restaurante Orient Ca'n Tomeu",
    "Coma de n´Arbona (inicio y Fin )": "Coma de n'Arbona (inicio y fin)",
    "Parking Plataforma del Fonicular (Inicio)": "Parking Plataforma del Funicular (inicio)",
}

def main():
    mode = "--write" if "--write" in sys.argv else "--analyze"
    translations = json.load(open(TRANS, encoding="utf-8")) if os.path.exists(TRANS) else {}
    # HIKE_ONLY="arch1.gpx||arch2.gpx" restringe el procesado a esas rutas (el resto del seed
    # queda idéntico: assemble solo reemplaza/añade los ids generados). Útil para añadir una
    # tanda sin regenerar/reordenar las rutas ya importadas.
    only = os.environ.get("HIKE_ONLY")
    routes = [r for r in ROUTES if (not only or r["file"] in only.split("||"))]
    gpx = {r["file"]: parse_gpx(os.path.join(GPXDIR, r["file"])) for r in routes}

    analysis = []
    for r in routes:
        g = gpx[r["file"]]
        # --- track ---
        if r.get("use_segment") is not None:
            raw = g["segs"][r["use_segment"]][:]
        elif r.get("chain"):
            raw = chain_segments(g["segs"])
        else:
            raw = max(g["segs"], key=len)[:]

        # --- recolectar waypoints (propios + extra_from) para orientar y proyectar ---
        raw_wpts = [dict(w, _src=r["file"]) for w in g["wpts"]]
        if r.get("extra_from"):
            src_file, names = r["extra_from"]
            for w in gpx[src_file]["wpts"]:
                if w["name"] in names:
                    raw_wpts.append(dict(w, _src=src_file))

        # orientar el track para que empiece junto al punto de inicio (si existe)
        inicio_pt = next(((w["lat"], w["lon"]) for w in raw_wpts
                          if name_is_inicio(w["name"]) and not (w["lat"] == 0 and w["lon"] == 0)), None)
        if inicio_pt and hav(raw[-1], inicio_pt) < hav(raw[0], inicio_pt):
            raw = raw[::-1]

        lat0 = raw[0][0]
        cum = cum_dist(raw)
        full_len = track_len_m(raw)
        med, p95 = mirror_metric(raw, cum)
        auto_mirror = (med < 25 and p95 < 70)
        is_oab = (r["type"] == "OUT_AND_BACK")
        # Trazado de ORDENACIÓN: en ida y vuelta, solo la ida (corte en el punto más lejano);
        # así los waypoints se ordenan por el recorrido de subida y no se mezclan con la bajada.
        # project_full: proyectar/ordenar sobre el track COMPLETO (loops etiquetados OUT_AND_BACK
        # cuya "ida" solo cubre media vuelta -> distancias infladas y waypoints mal descartados).
        proj_pts = raw if r.get("project_full") else (split_ida(raw) if is_oab else raw)
        # orientar la ida para que empiece junto al punto de inicio (los tracks de gpx.studio
        # a veces empiezan en el destino y vuelven al mismo punto)
        if inicio_pt and hav(inicio_pt, proj_pts[-1]) < hav(inicio_pt, proj_pts[0]):
            proj_pts = proj_pts[::-1]
        proj_cum = cum_dist(proj_pts)
        # Trazado que se GUARDA: si la vuelta es espejo limpio (o está marcada), solo la ida
        # (evita duplicar puntos); si no, el trazado completo tal cual se dibujó.
        clean_mirror = r.get("mirror", False) or auto_mirror
        poly_raw = proj_pts if (is_oab and clean_mirror) else raw
        gain, loss = elevation_gain_loss(raw)  # desnivel del archivo completo (o seg2 para Ofre)
        simp = rdp(poly_raw, 9.0, lat0)
        if len(simp) > 250:
            simp = rdp(poly_raw, 15.0, lat0)

        # --- waypoints: filtrar y conservar ---
        kept, discarded = [], []
        for w in raw_wpts:
            cname = clean_wpt_name(w["name"])
            name = NAME_FIX.get(cname, cname).strip()
            name = r.get("rename_wpts", {}).get(name, name)
            note = strip_citations(w["cmt"] or w["desc"])
            # sin nombre: derivar de desc, o descartar si tampoco hay desc
            if not name:
                if note:
                    name = "Punto de observación de Sa Dragonera"  # único caso (Cala en Basset)
                else:
                    discarded.append((w["name"] or "(sin nombre)", w["lat"], w["lon"], "sin nombre ni descripción"))
                    continue
            if w["lat"] == 0 and w["lon"] == 0:
                discarded.append((name, 0, 0, "punto en (0,0)"))
                continue
            d, s = project_on_track((w["lat"], w["lon"]), proj_pts, lat0, proj_cum)
            if d > WPT_FAR_FROM_TRACK_M:
                discarded.append((name, w["lat"], w["lon"], f"a {d/1000:.1f} km del trazado"))
                continue
            kept.append(dict(name=name, lat=w["lat"], lon=w["lon"], note=note, sym=w["sym"], s=s, _d=d))

        # --- fusión de duplicados: se conserva el primer nombre, se descarta el segundo ---
        for keep_nm, drop_nm in r.get("fuse_wpts", []):
            before = len(kept)
            kept = [k for k in kept if k["name"] != drop_nm]
            if len(kept) < before:
                discarded.append((drop_nm, 0, 0, f"fusionado con «{keep_nm}»"))

        # --- orden y roles ---
        kept.sort(key=lambda x: x["s"])
        max_s = max((k["s"] for k in kept), default=1.0) or 1.0
        # LAUNCH: el punto de inicio por nombre (el de menor s); si no hay, un parking/bus junto
        # a la salida (s < 12% del recorrido). FINISH: un punto de destino/fin por nombre que
        # esté en la mitad lejana (nunca en la salida); en lineales, el último punto.
        inicio_cands = [k for k in kept if name_is_inicio(k["name"])]
        launch = min(inicio_cands, key=lambda k: k["s"]) if inicio_cands else None
        if launch is None:
            near = [k for k in kept if k["s"] < 0.12*max_s and classify_type(k["name"], k["sym"], "WAYPOINT") in ("PARKING", "BUS")]
            launch = min(near, key=lambda k: k["s"]) if near else None
        fin_cands = [k for k in kept if name_is_finish(k["name"]) and k["s"] > 0.5*max_s]
        finish = max(fin_cands, key=lambda k: k["s"]) if fin_cands else None
        middles = [k for k in kept if k is not launch and k is not finish]
        middles.sort(key=lambda x: x["s"])
        ordered = ([launch] if launch else []) + middles + ([finish] if finish else [])
        for k in ordered:
            k["role"] = "WAYPOINT"
        if launch: launch["role"] = "LAUNCH"
        if finish: finish["role"] = "FINISH"
        if r["type"] == "LINEAR" and ordered:   # lineal: inicio y fin claros
            if not launch: ordered[0]["role"] = "LAUNCH"
            if not finish: ordered[-1]["role"] = "FINISH"
        kept = ordered
        for idx, k in enumerate(kept):
            k["type"] = classify_type(k["name"], k["sym"], k["role"])
            k["dist_prev"] = 0.0 if idx == 0 else round(abs(k["s"] - kept[idx-1]["s"])/1000.0, 3)

        # SUMMIT: punto más alto del track (por elevación); si un waypoint está a <=80 m, su tipo
        # pasa a SUMMIT (icono de montaña). Si no hay ninguno cerca, no se inventa.
        PEAK_KW = ("puig", "castell", "talaia", "cim ", "cumbre", "tomir", "pico", "penyal", "morro", "s'alcadena")
        if raw and len(raw[0]) >= 3:
            summit = max(raw, key=lambda p: p[2])
            best = None
            for k in kept:
                d = hav((k["lat"], k["lon"]), (summit[0], summit[1]))
                if d <= 80 and (best is None or d < best[1]):
                    best = (k, d)
            # solo si el punto más alto es una cima/castillo con nombre (evita falsos "summit"
            # en refugios, cuevas o trailheads en alto como el Coll Baix o Port des Canonge)
            if best and any(kw in strip_accents(best[0]["name"]) for kw in PEAK_KW):
                best[0]["type"] = "SUMMIT"

        # Punto de giro (OUT_AND_BACK, salvo Ofre): el punto más alejado de la salida (fin de la
        # ida). Si hay un waypoint a <=100 m, se reutiliza como FINISH; si no, se añade un marcador
        # sintético "Punto de giro" (sin nota; solo icono en el mapa).
        if r["type"] == "OUT_AND_BACK" and not r.get("no_turnaround"):
            tp = proj_pts[-1]
            near = [(k, hav((k["lat"], k["lon"]), (tp[0], tp[1]))) for k in kept]
            near = [x for x in near if x[1] <= 100]
            if near:
                kk = min(near, key=lambda x: x[1])[0]
                if kk["role"] not in ("LAUNCH", "FINISH"):
                    kk["role"] = "FINISH"
                    if kk["type"] != "SUMMIT":  # la cima manda sobre el punto de giro (p. ej. Tomir)
                        kk["type"] = "END"
            elif not any(k["role"] == "FINISH" for k in kept):
                last_s = kept[-1]["s"] if kept else 0.0
                kept.append(dict(
                    name="Punto de giro", lat=tp[0], lon=tp[1], note="", sym="", s=proj_cum[-1], _d=0.0,
                    role="FINISH", type="END",
                    dist_prev=round(abs(proj_cum[-1] - last_s)/1000.0, 3),
                ))

        # --- fusión salida/llegada: cuando el inicio y el final coinciden (circulares y rutas
        # ida-y-vuelta que vuelven al mismo punto), se elimina el waypoint de "llegada" próximo a
        # la salida y el marcador de inicio pasa a llamarse "Salida y llegada" (marcador único). ---
        if r.get("fuse_start_end"):
            launch = next((k for k in kept if k.get("role") == "LAUNCH"), None)
            if launch:
                kept = [k for k in kept if k is launch or not (
                    name_is_finish(k["name"]) and hav((k["lat"], k["lon"]), (launch["lat"], launch["lon"])) <= 200)]
                for k in kept:
                    if k.get("role") == "FINISH":  # por si el "llegada" se había marcado FINISH
                        k["role"] = "FINISH"
                launch["name"] = "Salida y llegada"

        # --- override de tipo por nombre (lo decide la tarea; p. ej. torrentes/acequias no son WATER) ---
        tov = r.get("type_override", {})
        for k in kept:
            if k["name"] in tov:
                k["type"] = tov[k["name"]]

        # --- nota sintética para waypoints sin descripción (traducida) ---
        nov = r.get("note_override", {})
        for k in kept:
            if k["name"] in nov:
                k["note_tr"] = nov[k["name"]]

        dist_km = r.get("dist_override", round(full_len/1000.0, 2))
        analysis.append(dict(route=r, g=g, full_len_km=round(full_len/1000,2), dist_km=dist_km,
                             mirror=clean_mirror, mirror_med=round(med,1), mirror_p95=round(p95,1),
                             auto_mirror=auto_mirror, gain=gain, loss=loss,
                             n_raw=len(raw), n_poly=len(simp), simp=simp, kept=kept, discarded=discarded,
                             lat0=lat0))

    # ---- salida análisis ----
    print(f"{'ruta':26} {'km':>6} {'full':>6} {'↑':>5} {'↓':>5} {'esp':>4} {'med':>5} {'p95':>5} {'poly':>5} {'wpt':>4} {'desc':>4}")
    for a in analysis:
        r = a["route"]
        print(f"{r['place']:26} {a['dist_km']:6.2f} {a['full_len_km']:6.2f} {a['gain']:5d} {a['loss']:5d} "
              f"{('S' if a['mirror'] else '-'):>4} {a['mirror_med']:5.0f} {a['mirror_p95']:5.0f} {a['n_poly']:5d} {len(a['kept']):4d} {len(a['discarded']):4d}  "
              f"[{r['type']}/{r['diff']}/{r['dur']}]")
    print("\n--- waypoints conservados y descartados ---")
    for a in analysis:
        r = a["route"]
        print(f"\n### {r['place']}  (auto_mirror={a['auto_mirror']})")
        for k in a["kept"]:
            print(f"   {k['role'][:6]:6} {k['type']:9} d={k['_d']:6.0f}m dprev={k['dist_prev']:.3f}  {k['name']}")
        for d in a["discarded"]:
            print(f"   DESCARTADO: {d[0]}  @({d[1]},{d[2]})  -> {d[3]}")

    if mode == "--analyze":
        print("\n(análisis; no se ha escrito el seed)")
        return analysis, translations

    assemble_and_write(analysis, translations)
    return analysis, translations

# -------- Ensamblaje e inyección en el seed --------
ROUTE_TYPE_LABELS = {
    "CIRCULAR":     dict(en="Loop",        es="Circular",   de="Rundweg"),
    "LINEAR":       dict(en="One-way",     es="Solo ida",   de="Einfache Strecke"),
    "OUT_AND_BACK": dict(en="Out and back", es="Ida y vuelta", de="Hin- und Rückweg"),
}

def _tr_note(translations, note_norm):
    """Devuelve (es, en, de) de una nota; si falta la traducción, usa el ES tal cual y avisa."""
    notes = translations.get("_notes_norm")
    if notes is None:
        notes = {norm(k): v for k, v in translations.get("notes", {}).items()}
        translations["_notes_norm"] = notes
    v = notes.get(note_norm)
    if not v:
        if note_norm:
            print(f"  [AVISO] nota sin traducción (se usa ES): {note_norm[:70]}…")
        return note_norm, note_norm, note_norm
    return v.get("es", note_norm), v.get("en", note_norm), v.get("de", note_norm)

def itin_id_for(place_id):
    slug = place_id[5:] if place_id.startswith("hike-") else place_id
    return "itin-hike-" + slug

def assemble_and_write(analysis, translations):
    data = json.load(open(SEED, encoding="utf-8"))
    places = {p["id"]: p for p in data["places"]}
    tips = translations.get("route_tips", {})
    warns = translations.get("access_warning", {})
    overrides = json.load(open(OVERRIDES, encoding="utf-8")) if os.path.exists(OVERRIDES) else {}

    new_itins = []
    for a in analysis:
        r = a["route"]; pid = r["place"]; p = places[pid]
        iid = itin_id_for(pid)
        photos = p.get("photo_urls", [])
        cover = photos[0] if photos else {"url": p.get("thumbnail_url", "")}
        gallery = photos[:]  # portada incluida como gallery[0], igual que SUP/otros itinerarios

        # waypoints
        wps = []
        for k in a["kept"]:
            if k.get("note_tr"):
                es, en, de = k["note_tr"]["es"], k["note_tr"]["en"], k["note_tr"]["de"]
            elif k["note"]:
                es, en, de = _tr_note(translations, k["note"])
            else:
                es, en, de = "", "", ""
            nm = translations.get("wpt_names", {}).get(k["name"], {})
            wps.append({
                "order": len(wps)+1,
                "name": nm.get("en", k["name"]), "name_es": nm.get("es", k["name"]), "name_de": nm.get("de", k["name"]),
                "role": k["role"], "type": k["type"],
                "lat": round(k["lat"], 6), "lng": round(k["lon"], 6),
                "note": en, "note_es": es, "note_de": de,
                "distance_from_prev_km": k["dist_prev"],
            })

        # viñetas de consejos prácticos
        bl = tips.get(iid, {})
        def with_bullets(base, lang):
            lines = bl.get(lang, [])
            if not lines: return base
            return (base.rstrip() + "\n\n" + "\n".join("• " + x for x in lines))

        lbl = ROUTE_TYPE_LABELS[r["type"]]
        itin = {
            "id": iid,
            "title": p["name"],
            "description": with_bullets(p.get("description", ""), "en"),
            "category": "HIKING",
            "duration_days": 1,
            "duration_hours": r["dur"],
            "difficulty": r["diff"],
            "route_type": r["type"],
            "total_distance_km": a["dist_km"],
            "ascent_m": a["gain"],
            "descent_m": a["loss"],
            "cover_photo": cover,
            "gallery_photos": gallery,
            "best_seasons": p.get("best_seasons", []) or ["spring", "autumn"],
            "tags": ["hiking", "gpx"],
            "route_track_reviewed": True,
            "route_polyline_points": [{"lat": round(la, 6), "lng": round(lo, 6)} for (la, lo, *_ ) in a["simp"]],
            "route_waypoints": wps,
            "translations": {
                "es": {"title": p.get("name_es", p["name"]), "description": with_bullets(p.get("description_es", p.get("description", "")), "es")},
                "de": {"title": p.get("name_de", p["name"]), "description": with_bullets(p.get("description_de", p.get("description", "")), "de")},
            },
            "stops": [{"place_id": pid, "order": 1, "suggested_duration_minutes": 120, "day_number": 1}],
        }
        if r.get("access_warning") and iid in warns:
            w = warns[iid]
            itin["access_warning"] = w.get("en", "")
            itin["access_warning_es"] = w.get("es", "")
            itin["access_warning_de"] = w.get("de", "")
        # overrides finales fijados por el usuario (tools/hiking_overrides.json): no se recalculan
        ov = overrides.get(pid, {})
        for k in ("total_distance_km", "route_type", "ascent_m", "descent_m", "duration_hours"):
            if k in ov:
                itin[k] = ov[k]
        if ov:
            print(f"  override {pid}: " + ", ".join(f"{k}={ov[k]}" for k in ov if not k.startswith('_')))
        new_itins.append(itin)

    # backup
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    bk = os.path.join(ROOT, "tools", f"seed_data_backup_hiking_{ts}.json")
    with open(bk, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2))
    print(f"backup del seed -> {bk}")

    # reemplazar/insertar por id (idempotente)
    new_ids = {i["id"] for i in new_itins}
    kept_existing = [i for i in data["itineraries"] if i["id"] not in new_ids]
    data["itineraries"] = kept_existing + new_itins

    # subir versión +1 (las 3 claves del JSON que deben ir alineadas)
    old_v = data["CURRENT_SEED_VERSION"]
    for vk in ("CURRENT_SEED_VERSION", "version", "seed_version"):
        if vk in data:
            data[vk] = old_v + 1
    with open(SEED, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2))
    print(f"seed escrito: {len(new_itins)} itinerarios de senderismo; "
          f"total itinerarios={len(data['itineraries'])}; versión {old_v} -> {data['CURRENT_SEED_VERSION']}")

    # actualizar CURRENT_SEED_VERSION en SeedDataWorker.kt
    src = open(WORKER, encoding="utf-8").read()
    import re
    new_src = re.sub(r"(CURRENT_SEED_VERSION\s*=\s*)\d+", rf"\g<1>{old_v+1}", src, count=1)
    if new_src != src:
        open(WORKER, "w", encoding="utf-8").write(new_src)
        print(f"SeedDataWorker.kt: CURRENT_SEED_VERSION -> {old_v+1}")
    else:
        print("  [AVISO] no se pudo actualizar CURRENT_SEED_VERSION en SeedDataWorker.kt")

if __name__ == "__main__":
    main()
