#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Revisión ORTOGRÁFICA del alemán (DE) del seed. SOLO faltas de escritura
(umlauts ä/ö/ü y ß perdidos/transcritos, erratas evidentes). NO reescribe, NO
mejora estilo, NO traduce, NO toca ES/EN ni datos no textuales.

Ámbito: solo campos DE (clave con '_de' o ruta '/de/').
Método: lista EXPLÍCITA de reemplazos.
  - UNAMBIG: token mal escrito -> correcto, aplicado con límite de palabra
    (cada token NO tiene homógrafo alemán correcto; p.ej. 'fur' nunca es palabra).
  - CONTEXT: frases para los casos ambiguos (schon/schön, Nahe/Nähe, Lange/Länge,
    Fahre/Fähre) donde el token aislado SÍ puede ser correcto.
Idempotente (al reejecutar, las formas malas ya no existen). Registra cada cambio
(path, antes -> después) para revisión.

NO se tocan: topónimos/nombres propios (Santanyi, Soller, Marmols, Alcudia, Cala…),
'ss' correcta (muss, Fluss, dass, Masse, Wasser…), ni 'hoher'/'langer' positivos.
"""
import json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "app/src/main/assets/seed_data.json")
WORKER = os.path.join(ROOT, "core/core-data/src/main/kotlin/com/mallorca/explorer/core/data/sync/SeedDataWorker.kt")
OLD_V, NEW_V = 420, 421

# token mal escrito -> correcto (sin homógrafo correcto en alemán)
UNAMBIG = {
 # ü
 "fur":"für","Fur":"Für","uber":"über","Uber":"Über","zuruck":"zurück",
 "fuhrt":"führt","Fuhrer":"Führer","fuhlt":"fühlt","fruh":"früh","fruih":"früh","fruhere":"frühere",
 "Kuste":"Küste","Kusten":"Küsten","Kustenpfad":"Küstenpfad","Kustenpfades":"Küstenpfades",
 "Kustenwanderung":"Küstenwanderung","Kustenwanderungen":"Küstenwanderungen",
 "Kustenstadtchen":"Küstenstädtchen","Ostkuste":"Ostküste","Sudostkuste":"Südostküste",
 "Kustenkalksein":"Küstenkalkstein","Kuche":"Küche",
 "konnen":"können","fullen":"füllen","fullt":"füllt","gewunschten":"gewünschten",
 "naturlich":"natürlich","naturliche":"natürliche","naturlichen":"natürlichen","naturlicher":"natürlicher",
 "geschutzt":"geschützt","geschutzte":"geschützte","geschutzten":"geschützten","geschutzter":"geschützter",
 "gehuteten":"gehüteten","graugruner":"graugrüner","grunen":"grünen","grun":"grün",
 "smaragdgruner":"smaragdgrüner","Smaragdgrun":"Smaragdgrün",
 "Dunen":"Dünen","Dunensystemen":"Dünensystemen","Dunensysteme":"Dünensysteme",
 "turkis":"türkis","turkisfarben":"türkisfarben","turkisfarbenem":"türkisfarbenem","Turkis":"Türkis","Turkisfarbe":"Türkisfarbe",
 "Gestrupp":"Gestrüpp","uberhangenden":"überhängenden","uberragt":"überragt","unubersichtlichen":"unübersichtlichen",
 "verfugbar":"verfügbar","gegenuber":"gegenüber","Schnorchelausrustung":"Schnorchelausrüstung","ausgerusteten":"ausgerüsteten",
 "familiengeruhrte":"familiengeführte","beruhmte":"berühmte","beruhmten":"berühmten",
 "unberuhrt":"unberührt","unberuhrten":"unberührten","Prufen":"Prüfen","Ruckfahrt":"Rückfahrt",
 "Hugel":"Hügel","vollstandig":"vollständig","vollstandigsten":"vollständigsten","mochten":"möchten",
 # ö
 "Fischerdufer":"Fischerdörfer","schonsten":"schönsten","Schonheit":"Schönheit","offnen":"öffnen",
 "offentlichen":"öffentlichen","kront":"krönt","ortlichen":"örtlichen","vollig":"völlig",
 "Hohenunterschied":"Höhenunterschied","Strandhohe":"Strandhöhe","hochstem":"höchstem","hochste":"höchste",
 "nordlich":"nördlich","nordlichen":"nördlichen","Sudosten":"Südosten","Sudostwind":"Südostwind",
 "sudlich":"südlich","Y-formiger":"Y-förmiger","moglich":"möglich","Hohlenforscher":"Höhlenforscher",
 # ä
 "gesaumt":"gesäumt","kieferngesaumten":"kieferngesäumten","Fischerhausern":"Fischerhäusern",
 "Fensterlaeden":"Fensterläden","sorgfaltig":"sorgfältig","spektakularsten":"spektakulärsten",
 "spektakularen":"spektakulären","Strande":"Strände","Taschenstrande":"Taschenstrände",
 "gefahrlich":"gefährlich","Abhangen":"Abhängen","Felswanden":"Felswänden","Felswande":"Felswände",
 "Getranke":"Getränke","Platze":"Plätze","Parkplatze":"Parkplätze","farbt":"färbt","langeres":"längeres",
 "langsten":"längsten","Erwaegen":"Erwägen","trub":"trüb","getunchten":"getünchten","taglich":"täglich",
 "vervollstandigt":"vervollständigt","Gelande":"Gelände","Hohle":"Höhle","Hohlen":"Höhlen","Gebaude":"Gebäude",
 # ß y combinaciones (ss/ö/ä)
 "aussergewohnlich":"außergewöhnlich","ausserhalb":"außerhalb","ausserordentlich":"außerordentlich","ausser":"außer",
 "Bergstrasse":"Bergstraße","Bergstrassen":"Bergstraßen","Zufahrtsstrasse":"Zufahrtsstraße",
 "regelmasig":"regelmäßig","geniessen":"genießen","Grosse":"Größe","Fusse":"Füße","Fussen":"Füßen",
 "weissem":"weißem","unerlasslich":"unerlässlich","schutzen":"schützen","betragt":"beträgt",
 # erratas evidentes (letras comidas/dobles)
 "meditertranem":"mediterranem","Mereshorizont":"Meereshorizont",
}

# ambiguos: solo en estas frases concretas
CONTEXT = [
 ("ebenso schon ist", "ebenso schön ist"),
 ("in der Nahe des", "in der Nähe des"),
 ("500 Metern Lange", "500 Metern Länge"),
 ("die Fahre vom Hafen", "die Fähre vom Hafen"),
 ("Barcos Azules Fahre", "Barcos Azules Fähre"),
 ("Ihren Mull mit", "Ihren Müll mit"),
]

WB_L = r"(?<![0-9A-Za-zÄÖÜäöüß])"
WB_R = r"(?![0-9A-Za-zÄÖÜäöüß])"

def is_de(p): return ("_de" in p) or ("/de/" in p)

def main():
    data = json.load(open(SEED, encoding="utf-8"))
    log = []  # (path, before, after)

    # precompilar patrones de token (más largos primero no hace falta: límite de palabra exacto)
    pats = [(re.compile(WB_L + re.escape(w) + WB_R), w, r) for w, r in UNAMBIG.items()]

    def fix_str(path, s):
        orig = s
        for pat, w, r in pats:
            if pat.search(s):
                s = pat.sub(r, s)
        for old, new in CONTEXT:
            if old in s:
                s = s.replace(old, new)
        if s != orig:
            # registrar los tokens concretos que cambiaron en este campo
            for pat, w, r in pats:
                n = len(pat.findall(orig))
                if n:
                    log.append((path, w, r, n))
            for old, new in CONTEXT:
                if old in orig:
                    log.append((path, old, new, orig.count(old)))
        return s

    def walk(o, p=""):
        if isinstance(o, dict):
            return {k: walk(v, p + "/" + str(k)) for k, v in o.items()}
        if isinstance(o, list):
            return [walk(v, p + "[" + str(i) + "]") for i, v in enumerate(o)]
        if isinstance(o, str):
            return fix_str(p, o) if is_de(p) else o
        return o

    data = walk(data)

    for k in ("CURRENT_SEED_VERSION", "version", "seed_version"):
        if k in data:
            if data[k] == OLD_V: data[k] = NEW_V
            elif data[k] != NEW_V: print(f"  !! AVISO {k}={data[k]}")

    json.dump(data, open(SEED, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    src = open(WORKER, encoding="utf-8").read()
    new = re.sub(r"(CURRENT_SEED_VERSION\s*=\s*)%d\b" % OLD_V, r"\g<1>%d" % NEW_V, src, count=1)
    worker = "sin cambio"
    if new != src:
        open(WORKER, "w", encoding="utf-8").write(new); worker = f"{OLD_V}->{NEW_V}"
    elif re.search(r"CURRENT_SEED_VERSION\s*=\s*%d\b" % NEW_V, src):
        worker = f"ya {NEW_V}"

    # informe
    from collections import defaultdict
    total = sum(n for _, _, _, n in log)
    print(f"correcciones aplicadas: {total} (en {len({l[0] for l in log})} campos)")
    bytok = defaultdict(int)
    for _, w, r, n in log: bytok[(w, r)] += n
    print("--- por token (antes -> después : nº) ---")
    for (w, r), n in sorted(bytok.items(), key=lambda x: (-x[1], x[0][0])):
        print(f"  {w!r} -> {r!r} : {n}")
    print("versiones:", data["CURRENT_SEED_VERSION"], data.get("version"), data.get("seed_version"), "| worker", worker)
    # guardar lista completa por campo
    import json as J
    outp = os.path.join(ROOT, "tools", "de_spelling_changes.json")
    J.dump([{"path": p, "before": w, "after": r, "count": n} for p, w, r, n in log],
           open(outp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("lista completa por campo:", outp)

if __name__ == "__main__":
    main()
