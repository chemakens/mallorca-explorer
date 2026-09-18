#!/usr/bin/env python3
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    context = browser.new_context(
        user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
    )
    page = context.new_page()
    print("Navegando...", flush=True)
    page.goto("https://thecalendarmallorca.com/es/events/", wait_until="networkidle", timeout=30000)
    page.wait_for_timeout(3000)
    print("Título:", page.title(), flush=True)
    print("URL final:", page.url, flush=True)

    # Ver todos los links
    links = page.query_selector_all("a[href]")
    print(f"\nTotal links: {len(links)}", flush=True)
    event_links = []
    for link in links:
        href = link.get_attribute("href") or ""
        if "thecalendarmallorca" in href and len(href) > 40:
            event_links.append(href)
    print(f"Links de eventos candidatos: {len(event_links)}", flush=True)
    for l in event_links[:20]:
        print(" ", l)

    # Ver texto de la página
    print("\nTexto página (primeros 500 chars):")
    print(page.inner_text("body")[:500])

    input("\nPulsa Enter para cerrar el navegador...")
    browser.close()
