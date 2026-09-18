#!/usr/bin/env python3
from playwright.sync_api import sync_playwright

BASE_URL = "https://firesifestes.es"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    url = f"{BASE_URL}/es/calendario/2026/09/"
    print(f"Cargando {url}...")
    page.goto(url, wait_until="networkidle", timeout=30000)
    page.wait_for_timeout(2000)
    body = page.inner_text("body")
    print("\n--- PRIMEROS 3000 CHARS ---")
    print(body[:3000])
    links = page.locator("a[href*='evento']").all()
    print(f"\n--- ENLACES CON 'evento': {len(links)} ---")
    for l in links[:10]:
        print(f"  {l.get_attribute('href')} | {l.inner_text().strip()[:60]}")
    browser.close()
