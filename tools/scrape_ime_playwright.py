#!/usr/bin/env python3
"""
Scraper IME Palma con Playwright - obtiene fechas reales usando JS rendering.
Guarda resultados en ime_events_fixed.json
"""
import json
import re
import sys
import time
from datetime import datetime

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("ERROR: playwright no instalado. Ejecuta: pip3 install playwright")
    sys.exit(1)

MESES = {
    "enero": "01", "febrero": "02", "marzo": "03", "abril": "04",
    "mayo": "05", "junio": "06", "julio": "07", "agosto": "08",
    "septiembre": "09", "octubre": "10", "noviembre": "11", "diciembre": "12",
    "gener": "01", "febrer": "02", "març": "03", "marc": "03",
    "maig": "05", "juny": "06", "juliol": "07", "agost": "08",
    "setembre": "09", "novembre": "11", "desembre": "12",
}

def parse_date(text):
    if not text:
        return None, None
    matches = list(re.finditer(r"(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})", text))
    if len(matches) >= 2:
        d1, m1, y1 = matches[0].groups()
        d2, m2, y2 = matches[1].groups()
        return f"{y1}-{m1.zfill(2)}-{d1.zfill(2)}", f"{y2}-{m2.zfill(2)}-{d2.zfill(2)}"
    elif len(matches) == 1:
        d, m, y = matches[0].groups()
        s = f"{y}-{m.zfill(2)}-{d.zfill(2)}"
        return s, s
    m = re.search(r"(\d{1,2})\s*(?:al|a|-)\s*(\d{1,2})\s+de\s+([a-záéíóúç]+)(?:\s+de)?\s+(\d{4})", text.lower())
    if m:
        d1, d2, mes, y = m.groups()
        mo = MESES.get(mes, "01")
        return f"{y}-{mo}-{d1.zfill(2)}", f"{y}-{mo}-{d2.zfill(2)}"
    m = re.search(r"(\d{1,2})\s+(?:de\s+)?([a-záéíóúç]+)(?:\s+de)?\s+(\d{4})", text.lower())
    if m:
        d, mes, y = m.groups()
        mo = MESES.get(mes)
        if mo:
            s = f"{y}-{mo}-{d.zfill(2)}"
            return s, s
    m = re.search(r"(\d{4}-\d{2}-\d{2})", text)
    if m:
        return m.group(1), m.group(1)
    return None, None


def scrape_ime_palma():
    print("   🔍 Scraping IME Palma...", flush=True)
    today = datetime.now().date()
    events = []
    seen_urls = set()
    consecutive_past_only_pages = 0
    base_url = "https://ime.palma.es/es/eventos"

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
        )
        page = context.new_page()

        for page_num in range(1, 3):
            if page_num == 1:
                url = base_url
            else:
                url = (
                    f"{base_url}?p_p_id=com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_zgzD14PYJk5w"
                    f"&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view"
                    f"&_com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_zgzD14PYJk5w_delta=12"
                    f"&p_r_p_resetCur=false"
                    f"&_com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_zgzD14PYJk5w_cur={page_num}"
                )
            try:
                page.goto(url, wait_until="domcontentloaded", timeout=20000)
                page.wait_for_timeout(1500)
            except Exception:
                break

            links = page.query_selector_all("a[href]")
            event_links = []
            for link in links:
                href = link.get_attribute("href") or ""
                title = (link.inner_text() or "").strip()
                if (("/-/" in href and "redirect=" in href) or "/evento/" in href):
                    if title and len(title) >= 5 and "ver más" not in title.lower():
                        full_url = href if href.startswith("http") else f"https://ime.palma.es{href}"
                        if full_url not in seen_urls:
                            event_links.append((full_url, title))
                            seen_urls.add(full_url)

            if not event_links:
                break

            count_before = len(events)

            for event_url, title in event_links:
                date_start = None
                date_end = None
                try:
                    detail_page = context.new_page()
                    detail_page.goto(event_url, wait_until="domcontentloaded", timeout=15000)
                    detail_page.wait_for_timeout(800)
                    time_tags = detail_page.query_selector_all("time[datetime]")
                    for tt in time_tags:
                        dt_val = tt.get_attribute("datetime") or ""
                        if re.match(r"\d{4}-\d{2}-\d{2}", dt_val):
                            date_start = dt_val[:10]
                            date_end = dt_val[:10]
                            break
                    if not date_start:
                        scripts = detail_page.query_selector_all("script[type='application/ld+json']")
                        for s in scripts:
                            try:
                                jd = json.loads(s.inner_text() or "{}")
                                sd = jd.get("startDate", "")[:10]
                                ed = jd.get("endDate", "")[:10]
                                if sd and re.match(r"\d{4}-\d{2}-\d{2}", sd):
                                    date_start = sd
                                    date_end = ed if (ed and re.match(r"\d{4}-\d{2}-\d{2}", ed)) else sd
                                    break
                            except:
                                pass
                    if not date_start:
                        full_text = detail_page.inner_text("body") or ""
                        date_start, date_end = parse_date(full_text)
                    detail_page.close()
                except Exception:
                    try:
                        detail_page.close()
                    except:
                        pass

                if date_start:
                    try:
                        if datetime.strptime(date_end or date_start, "%Y-%m-%d").date() < today:
                            continue
                    except:
                        pass
                else:
                    continue

                import hashlib
                event_id = "web-ime_palma-" + hashlib.md5(event_url.encode()).hexdigest()[:12]
                events.append({
                    "source": "ime_palma",
                    "title": title,
                    "description": "",
                    "start_date": date_start,
                    "end_date": date_end,
                    "municipality": "Palma",
                    "location": "Palma",
                    "category": "SPORT",
                    "is_free": True,
                    "price": None,
                    "website_url": event_url,
                    "id": event_id,
                })
                time.sleep(0.1)

            if len(events) == count_before:
                consecutive_past_only_pages += 1
                if consecutive_past_only_pages >= 2:
                    break
            else:
                consecutive_past_only_pages = 0

        browser.close()

    print(f"      ✅ Found {len(events)} events", flush=True)
    return events


if __name__ == "__main__":
    events = scrape_ime_palma()
    out = "/Users/usuario/Code/mallorca-explorer/tools/ime_events_fixed.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(events, f, ensure_ascii=False, indent=2)
    print(f"💾 Guardado en {out}", flush=True)
