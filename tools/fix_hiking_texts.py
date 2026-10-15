#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Corrige TEXTOS del seed de las 19 rutas de senderismo (y sus places enlazados):
  PASO 1 - cifras de km/tiempo en descripciones y consejos que contradecian el mapa.
  PASO 2 - titulo EN del Cami Vell de Lluc ("Old Lluc Road" -> "Cami Vell de Lluc") en el ITINERARIO (no en el place).
  PASO 3 - ortografia (tildes/n~ ES, umlauts/ss DE, erratas EN).
  PASO 4 - CURRENT_SEED_VERSION / version / seed_version 417 -> 418 (+ SeedDataWorker.kt).

NO toca geometria, lat/lng, tipos de waypoint, total_distance_km, ascent_m, descent_m,
duration_hours, route_type ni ids. Solo campos de texto.

Idempotente: usa reemplazos de subcadena con contexto suficiente; al reejecutar,
las subcadenas "antiguas" ya no existen y no hace nada. Solo ES/EN/DE (no zh/ru).
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "app/src/main/assets/seed_data.json")
WORKER = os.path.join(ROOT, "core/core-data/src/main/kotlin/com/mallorca/explorer/core/data/sync/SeedDataWorker.kt")
OLD_V, NEW_V = 417, 418

# -- pares (old, new) por itinerario. Se aplican a: desc base(EN)/es/de del itinerario,
#    description/_es/_de del place, y tips/_es/_de del place. Las subcadenas llevan
#    contexto para ser inequivocas (p.ej. no tocar "d'en Mas" al cambiar "mas"->"mas").
FIXES = {
 "itin-hike-ruta-cala-tuent": {"place": "ruta-cala-tuent", "pairs": [
   # EN
   ("above Soller,", "above Sóller,"),
   ("to Port de Soller.", "to Port de Sóller."),
   ("The one-way distance is 14.6 kilometres and the walk takes 4 to 5 hours.",
    "The one-way distance is about 10 kilometres and the walk takes around 3 hours."),
   ("retracing all 14.6 km.", "retracing all 10 km."),
   # ES
   ("costeras mas espectaculares", "costeras más espectaculares"),
   ("piedra que antano conectaba masias remotas", "piedra que antaño conectaba masías remotas"),
   ("ses Barques sobre Soller,", "ses Barques sobre Sóller,"),
   ("La distancia de ida es de 14,6 kilometros y la caminata dura entre 4 y 5 horas.",
    "La distancia de ida es de unos 10 kilómetros y la caminata dura unas 3 horas."),
   ("hasta Port de Soller.", "hasta Port de Sóller."),
   ("volver los 14,6 km a pie.", "volver los 10 km a pie."),
   ("La unica fuente de agua en la ruta es la Font des Verger: tratala como",
    "La única fuente de agua en la ruta es la Font des Verger: trátala como"),
   ("Las botas de montana con sujecion en el tobillo",
    "Las botas de montaña con sujeción en el tobillo"),
   ("no mas tarde de las 08:00-09:00", "no más tarde de las 08:00-09:00"),
   ("a tiempo de banarte y comer", "a tiempo de bañarte y comer"),
   ("Reserva el restaurante con antelacion.", "Reserva el restaurante con antelación."),
   ("personas con vertigo o poca experiencia de montana.",
    "personas con vértigo o poca experiencia de montaña."),
   # DE
   ("eine der spektakularsten und anspruchsvollsten Kustenwanderungen",
    "eine der spektakulärsten und anspruchsvollsten Küstenwanderungen"),
   ("einst entlegene Geh und Fischergemeinschaften entlang der nordlichen",
    "einst entlegene Gehöfte und Fischergemeinschaften entlang der nördlichen"),
   ("Startend vom Mirador de ses Barques oberhalb von Soller fuhrt der Weg durch die verlassenen Baltix-Guter mit ihren jahrhundertealten Olivenhainen, uberquert den Coll de Biniamar",
    "Ausgehend vom Mirador de ses Barques oberhalb von Sóller führt der Weg durch die verlassenen Baltix-Güter mit ihren jahrhundertealten Olivenhainen, überquert den Coll de Biniamar"),
   ("Die einseitige Distanz betragt 14,6 Kilometer und die Wanderung dauert 4 bis 5 Stunden.",
    "Die einseitige Distanz beträgt rund 10 Kilometer und die Wanderung dauert etwa 3 Stunden."),
   ("Barcos Azules Ruckfahrtfahre von Cala Tuent", "Barcos Azules Rückfahrtfähre von Cala Tuent"),
   ("Im Sommer fahrt sie um 16:50", "Im Sommer fährt sie um 16:50"),
   ("oder der Ruckweg auf den gesamten 14,6 km.", "oder der Rückweg auf den gesamten 10 km."),
   ("Bergschuhe mit Knoochelstutze sind unerlasslich. Das Gelande wechselt",
    "Bergschuhe mit Knöchelstütze sind unerlässlich. Das Gelände wechselt"),
   ("keine Route fur Trailrunner", "keine Route für Trailrunner"),
   ("ses Barques spatestens um 08:00-09:00 Uhr, um rechtzeitig vor der Fahre zur Cala Tuent",
    "ses Barques spätestens um 08:00-09:00 Uhr, um rechtzeitig vor der Fähre zur Cala Tuent"),
   ("der Gesamthohemunterschied ist erheblich und die exponierten Kliffabschnitte konnen schwindelerregend",
    "der Gesamthöhenunterschied ist erheblich und die exponierten Kliffabschnitte können schwindelerregend"),
   ("wird nicht fur Personen mit Hohenschwindel oder geringer Bergerfahrung",
    "wird nicht für Personen mit Höhenschwindel oder geringer Bergerfahrung"),
 ]},
 "itin-hike-ruta-cala-varques": {"place": "ruta-cala-varques", "pairs": [
   # EN
   ("from the Cala Romantica car park", "from the Cala Romàntica car park"),
   ("the route covers 7.5 kilometres round trip across a rocky limestone landscape",
    "the route covers about 13 kilometres on a circular loop across a rocky limestone landscape"),
   ("The walk takes 1 to 1.5 hours each way.", "The full loop takes around 4 hours."),
   # ES
   ("costeras mas gratificantes", "costeras más gratificantes"),
   ("del parking de Cala Romantica en S'Estany", "del parking de Cala Romàntica en S'Estany"),
   ("el recorrido cubre 7,5 kilometros de ida y vuelta por un paisaje rocoso de caliza tipico de la costa de Llevant",
    "el recorrido cubre unos 13 kilómetros en un trazado circular por un paisaje rocoso de caliza típico de la costa de Llevant"),
   ("arena blanca pristina con aguas turquesas de aspecto caribeno.",
    "arena blanca prístina con aguas turquesas de aspecto caribeño."),
   ("el calor del mediodia y llegar a la playa antes de que se llene. La caminata dura entre 1 y 1,5 horas en cada sentido.",
    "el calor del mediodía y llegar a la playa antes de que se llene. El circuito completo dura unas 4 horas."),
   ("la senal GPS puede ser irregular y la senalizacion en tierra es minima.",
    "la señal GPS puede ser irregular y la señalización en tierra es mínima."),
   ("No hay ningun servicio ni agua potable", "No hay ningún servicio ni agua potable"),
   ("linterna frontal y precaucion.", "linterna frontal y precaución."),
   # DE
   ("eine der lohnendsten Kustenwanderungen im Osten", "eine der lohnendsten Küstenwanderungen im Osten"),
   ("Startend vom Parkplatz Cala Romantica an der S'Estany d'en Mas (Manacor) umfasst die Route 7,5 Kilometer hin und zuruck durch eine felsige Kalksteinlandschaft.",
    "Ausgehend vom Parkplatz Cala Romàntica an der S'Estany d'en Mas (Manacor) umfasst die Route rund 13 Kilometer als Rundweg durch eine felsige Kalksteinlandschaft."),
   ("eine spektakulare naturliche Felsbrucke uber dem Meer, sowie die Eingange zu den beruhmten Hohlensystemen",
    "eine spektakuläre natürliche Felsbrücke über dem Meer, sowie die Eingänge zu den berühmten Höhlensystemen"),
   ("Das Ziel ist ein unberuhrt weisser Sandstrand mit turkisfarbenem, karibisch anmutendem Wasser.",
    "Das Ziel ist ein unberührter weißer Sandstrand mit türkisfarbenem, karibisch anmutendem Wasser."),
   ("Das Geland besteht aus unebenem Kalkstein und ist in einigen Abschnitten weglos. Flip-Flops oder Sandalen sind gefahrlich.",
    "Das Gelände besteht aus unebenem Kalkstein und ist in einigen Abschnitten weglos. Flip-Flops oder Sandalen sind gefährlich."),
   ("Die Wanderung dauert jeweils 1 bis 1,5 Stunden.", "Die Rundtour dauert etwa 4 Stunden."),
   ("Das GPS-Signal kann unzuverlassig sein", "Das GPS-Signal kann unzuverlässig sein"),
   ("Die Hohlen (Cova des Pirata und Cova des Pont)", "Die Höhlen (Cova des Pirata und Cova des Pont)"),
 ]},
 "itin-hike-cami-arxiduc": {"place": "cami-arxiduc", "pairs": [
   ("The circular trail (12–14 km, 5–6 hours, moderate-high difficulty)",
    "The circular trail (~19 km, ~6 hours, moderate-high difficulty)"),
   ("El sendero circular (12–14 km, 5–6 horas, dificultad moderada-alta)",
    "El sendero circular (unos 19 km, unas 6 horas, dificultad moderada-alta)"),
   ("Der Rundweg (12–14 km, 5–6 Stunden, mittlere bis schwere Schwierigkeit)",
    "Der Rundweg (rund 19 km, etwa 6 Stunden, mittlere bis schwere Schwierigkeit)"),
   ("botas de montaña con sujección de tobillo", "botas de montaña con sujeción de tobillo"),
 ]},
 "itin-hike-cami-des-correu": {"place": "cami-des-correu", "pairs": [
   ("This 8 km trail (part of the GR-221 Ruta de Pedra en Sec)",
    "This trail of around 16 km there and back (part of the GR-221 Ruta de Pedra en Sec)"),
   ("Este sendero de 8 km (parte del GR-221 Ruta de Pedra en Sec)",
    "Este sendero de unos 16 km ida y vuelta (parte del GR-221 Ruta de Pedra en Sec)"),
   ("Der 8 km lange Pfad (Teil des GR-221 Ruta de Pedra en Sec)",
    "Der rund 16 km lange Hin- und Rückweg (Teil des GR-221 Ruta de Pedra en Sec)"),
   ("Route: 8 km one way, ~2h15min, low-medium difficulty",
    "Route: ~16 km there and back, ~4 h, low-medium difficulty"),
   ("Ruta: 8 km solo ida, ~2h15min, dificultad media-baja",
    "Ruta: unos 16 km ida y vuelta, ~4 h, dificultad media-baja"),
   ("Route: 8 km einfach, ~2h15min, mittlere/leichte Schwierigkeit",
    "Route: rund 16 km hin und zurück, ~4 h, mittlere/leichte Schwierigkeit"),
 ]},
 "itin-hike-salt-des-freu": {"place": "salt-des-freu", "pairs": [
   ("The full round trip is about 3 km and takes 45–60 minutes — one of the most rewarding family walks on the island.",
    "The full circular route is about 6 km and takes around 2 hours — one of the most rewarding family walks on the island."),
   ("La excursión completa (ida y vuelta) son unos 3 km y se completa en 45-60 minutos — uno de los paseos familiares más gratificantes de la isla.",
    "La excursión circular completa son unos 6 km y se completa en unas 2 horas — uno de los paseos familiares más gratificantes de la isla."),
   ("Die Rundtour beträgt etwa 3 km und dauert 45–60 Minuten — eine der lohnendsten Familientouren der Insel.",
    "Die Rundtour beträgt etwa 6 km und dauert rund 2 Stunden — eine der lohnendsten Familientouren der Insel."),
 ]},
 "itin-hike-puig-tomir": {"place": "puig-tomir", "pairs": [
   ("Route: ~6 km round trip, 3–5 hours, high difficulty; final ridge is very technical",
    "Route: ~9.5 km round trip, around 6 hours, high difficulty; final ridge is very technical"),
   ("Ruta: ~6 km ida y vuelta, 3–5 horas, dificultad alta; la cresta final es muy técnica",
    "Ruta: ~9,5 km ida y vuelta, unas 6 horas, dificultad alta; la cresta final es muy técnica"),
   ("Route: ~6 km Hin- und Rückweg, 3–5 Std., hoher Schwierigkeitsgrad; Gipfelgrat sehr technisch",
    "Route: ~9,5 km Hin- und Rückweg, etwa 6 Std., hoher Schwierigkeitsgrad; Gipfelgrat sehr technisch"),
 ]},
 "itin-hike-cala-en-basset": {"place": "cala-en-basset", "pairs": [
   ("Total from the car park: 2.8 km round trip, 20–25 min each way",
    "Full circular route from the car park: about 5.4 km, around 2 hours"),
   ("En total desde el aparcamiento son 2,8 km (ida y vuelta), 20-25 minutos por trayecto",
    "La ruta circular completa desde el aparcamiento son unos 5,4 km, unas 2 horas"),
   ("Gesamtdistanz ab dem Parkplatz: 2,8 km (Hin- und Rückweg), 20–25 Minuten pro Richtung",
    "Komplette Rundtour ab dem Parkplatz: rund 5,4 km, etwa 2 Stunden"),
 ]},
 "itin-hike-la-comuna-bunyola": {"place": "la-comuna-bunyola", "pairs": [
   ("Coma d'en Buscante descent (~11 km, 450 m gain, 3–4 h)",
    "Coma d'en Buscante descent (~13 km, 4–4.5 h)"),
   ("bajada por la Coma d'en Buscante (~11 km, 450 m de desnivel, 3–4 h)",
    "bajada por la Coma d'en Buscante (~13 km, 4–4,5 h)"),
   ("Abstieg durch Coma d'en Buscante (~11 km, 450 Hm, 3–4 Std.)",
    "Abstieg durch Coma d'en Buscante (~13 km, 4–4,5 Std.)"),
 ]},
 "itin-hike-avenc-de-son-pou": {"place": "avenc-de-son-pou", "pairs": [
   ("hinter der Oberlichtzzone;", "hinter der Oberlichtzone;"),
 ]},
}

def apply_pairs_to_str(s, pairs, counter):
    if not isinstance(s, str):
        return s
    for i, (old, new) in enumerate(pairs):
        if old in s:
            n = s.count(old)
            s = s.replace(old, new)
            counter[i] += n
    return s

def main():
    data = json.load(open(SEED, encoding="utf-8"))
    places = {p["id"]: p for p in data["places"]}
    itins = {i["id"]: i for i in data["itineraries"]}

    first_run = data.get("CURRENT_SEED_VERSION") == OLD_V
    report = {}

    for iid, cfg in FIXES.items():
        pairs = cfg["pairs"]
        counter = [0] * len(pairs)
        it = itins[iid]
        pl = places[cfg["place"]]

        # itinerario: descripcion base (EN) + es/de
        it["description"] = apply_pairs_to_str(it.get("description"), pairs, counter)
        tr = it.get("translations", {})
        for lg in ("es", "de"):
            if lg in tr and isinstance(tr[lg], dict) and "description" in tr[lg]:
                tr[lg]["description"] = apply_pairs_to_str(tr[lg]["description"], pairs, counter)

        # place: description/_es/_de + tips/_es/_de
        for f in ("description", "description_es", "description_de"):
            if f in pl:
                pl[f] = apply_pairs_to_str(pl[f], pairs, counter)
        for f in ("tips", "tips_es", "tips_de"):
            if isinstance(pl.get(f), list):
                pl[f] = [apply_pairs_to_str(x, pairs, counter) for x in pl[f]]

        report[iid] = sum(counter)
        if first_run:
            for i, (old, new) in enumerate(pairs):
                if counter[i] == 0:
                    print(f"  !! AVISO [{iid}] par #{i} no encontro coincidencias: {old[:60]!r}")

    # PASO 2: titulo EN del itinerario Cami Vell de Lluc (NO el place)
    lluc = itins["itin-hike-cami-vell-de-lluc"]
    if lluc.get("title") == "Old Lluc Road":
        lluc["title"] = "Camí Vell de Lluc"
        report["itin-hike-cami-vell-de-lluc (title)"] = 1
    elif lluc.get("title") == "Camí Vell de Lluc":
        report["itin-hike-cami-vell-de-lluc (title)"] = "ya correcto"

    # PASO 4: version bump
    for k in ("CURRENT_SEED_VERSION", "version", "seed_version"):
        if k in data:
            if data[k] == OLD_V:
                data[k] = NEW_V
            elif data[k] != NEW_V:
                print(f"  !! AVISO: {k} = {data[k]} (esperaba {OLD_V} o {NEW_V})")

    json.dump(data, open(SEED, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    # SeedDataWorker.kt
    src = open(WORKER, encoding="utf-8").read()
    new = re.sub(r"(CURRENT_SEED_VERSION\s*=\s*)%d\b" % OLD_V, r"\g<1>%d" % NEW_V, src, count=1)
    if new != src:
        open(WORKER, "w", encoding="utf-8").write(new)
        worker_state = f"{OLD_V} -> {NEW_V}"
    elif re.search(r"CURRENT_SEED_VERSION\s*=\s*%d\b" % NEW_V, src):
        worker_state = f"ya en {NEW_V}"
    else:
        worker_state = "NO ENCONTRADO (revisar!)"

    print("=== Reemplazos por ruta (campos/ocurrencias cambiados) ===")
    for k, v in report.items():
        print(f"  {k}: {v}")
    print("versiones JSON:", data.get("CURRENT_SEED_VERSION"), data.get("version"), data.get("seed_version"))
    print("SeedDataWorker.kt:", worker_state)

if __name__ == "__main__":
    main()
