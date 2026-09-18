#!/usr/bin/env python3
from playwright.sync_api import sync_playwright

URL = "https://thecalendarmallorca.com/es/eventos/En-las-rocas--cerrando-el-club-de-playa-Cape-Nao/"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
    page.goto(URL, wait_until="domcontentloaded", timeout=20000)
    page.wait_for_timeout(2000)

    print("=== TEXTO ===")
    print(page.inner_text("body")[:2000])

    print("\n=== JSON-LD ===")
    for s in page.query_selector_all("script[type='application/ld+json']"):
        print(s.inner_text()[:800])

    print("\n=== CATEGORIAS (links) ===")
    for a in page.query_selector_all("a[href*='categor']"):
        print(" ", a.inner_text().strip(), "|", a.get_attribute("href"))

    browser.close()
