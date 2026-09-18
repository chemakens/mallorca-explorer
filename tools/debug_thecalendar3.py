#!/usr/bin/env python3
from playwright.sync_api import sync_playwright

# Categorías del menú a ignorar
MENU_CATS = {
    "cultura-de-las-artes", "mejores-condiciones-de-vida",
    "clinicas-de-campamentos", "negocio-comunitario", "comprar-mas",
    "talleres-clases",
}

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(user_agent="Mozilla/5.0 AppleWebKit/537.36")
    page.goto("https://thecalendarmallorca.com/es/eventos/En-las-rocas--cerrando-el-club-de-playa-Cape-Nao/", wait_until="domcontentloaded", timeout=20000)
    page.wait_for_timeout(2000)

    # Todas las categorías de la página
    all_cats = []
    for a in page.query_selector_all("a[href*='categor']"):
        href = a.get_attribute("href") or ""
        txt = a.inner_text().strip()
        # Extraer slug de la URL
        m = __import__("re").search(r"categor[^/]*/([^/]+)/?$", href)
        slug = m.group(1) if m else ""
        all_cats.append((slug, txt, href))

    print("Todas las categorías con texto visible:")
    for slug, txt, href in all_cats:
        print(f"  slug={slug:40} txt='{txt}'")

    # Categorías con texto (las del evento específico lo tienen)
    event_cats = [(slug, txt) for slug, txt, href in all_cats if txt]
    print("\nCategorías CON texto (del evento):")
    for slug, txt in event_cats:
        print(f"  {slug} → {txt}")

    # Navegación meses
    print("\n=== Botones de navegación de mes ===")
    for btn in page.query_selector_all("button, a"):
        txt = (btn.inner_text() or "").strip()
        cls = btn.get_attribute("class") or ""
        if any(x in txt.lower() for x in ["siguiente", "next", "anterior", "prev", "octubre", "noviembre"]):
            print(f"  '{txt}' class='{cls[:60]}'")

    browser.close()
