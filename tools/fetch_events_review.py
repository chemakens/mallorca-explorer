#!/usr/bin/env python3
"""
Fetch Ticketmaster Events for Review
-------------------------------------
Fetches events from Ticketmaster API and generates an interactive
HTML review page where you can approve/reject events before uploading
to Firestore.
"""

import sys
import json
from pathlib import Path
from datetime import datetime

try:
    import requests
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install requests")
    sys.exit(1)

# Paths
PROJECT_ROOT = Path(__file__).parent.parent
LOCAL_PROPERTIES = PROJECT_ROOT / "local.properties"
TOOLS_DIR = Path(__file__).parent
REVIEW_HTML = TOOLS_DIR / "review.html"

# Ticketmaster API
TICKETMASTER_API_URL = "https://app.ticketmaster.com/discovery/v2/events.json"

# Category mapping
CATEGORY_MAP = {
    "Music": "CONCERT",
    "Sports": "SPORT",
    "Arts & Theatre": "CULTURE",
    "Family": "FESTIVAL",
    "Festival": "FESTIVAL",
    "Miscellaneous": "CULTURE",
}


def read_api_key():
    """Read TICKETMASTER_API_KEY from local.properties."""
    if not LOCAL_PROPERTIES.exists():
        print(f"❌ local.properties not found at {LOCAL_PROPERTIES}")
        sys.exit(1)

    with open(LOCAL_PROPERTIES) as f:
        for line in f:
            line = line.strip()
            if line.startswith("TICKETMASTER_API_KEY="):
                key = line.split("=", 1)[1].strip()
                if key:
                    return key

    print("❌ TICKETMASTER_API_KEY not found in local.properties")
    sys.exit(1)


def fetch_ticketmaster_events(api_key, limit=200):
    """Fetch events from Ticketmaster for Mallorca/Baleares."""
    print("🔍 Fetching events from Ticketmaster API...")

    params = {
        "apikey": api_key,
        "countryCode": "ES",
        "stateCode": "IB",
        "size": limit,
        "sort": "date,asc",
    }

    try:
        response = requests.get(TICKETMASTER_API_URL, params=params, timeout=30)
        response.raise_for_status()
        data = response.json()

        embedded = data.get("_embedded", {})
        events = embedded.get("events", [])

        print(f"✅ Fetched {len(events)} events from Ticketmaster")
        return events

    except requests.exceptions.RequestException as e:
        print(f"❌ Failed to fetch from Ticketmaster: {e}")
        return []


def map_category(tm_classification):
    """Map Ticketmaster classification to Firestore category."""
    for item in tm_classification:
        segment = item.get("segment", {}).get("name", "")
        mapped = CATEGORY_MAP.get(segment)
        if mapped:
            return mapped
    return "CULTURE"


def parse_date(date_str):
    """Parse Ticketmaster date to ISO format."""
    if not date_str:
        return None
    try:
        dt = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        return dt.strftime("%Y-%m-%d")
    except ValueError:
        return None


def generate_review_html(events):
    """Generate interactive HTML review page."""
    print(f"📝 Generating review page with {len(events)} events...")

    # Convert events to simplified format for JavaScript
    events_data = []
    for tm_event in events:
        event_id = tm_event.get("id")
        name = tm_event.get("name", "")

        dates = tm_event.get("dates", {})
        start_info = dates.get("start", {})
        start_date = parse_date(start_info.get("dateTime") or start_info.get("localDate"))

        classifications = tm_event.get("classifications", [])
        category = map_category(classifications)

        venues = tm_event.get("_embedded", {}).get("venues", [])
        venue = venues[0] if venues else {}
        municipality = venue.get("city", {}).get("name", "Mallorca")

        images = tm_event.get("images", [])
        image_url = images[0].get("url") if images else None

        url = tm_event.get("url")

        events_data.append({
            "tm_id": event_id,
            "name": name,
            "start_date": start_date or "Unknown",
            "category": category,
            "municipality": municipality,
            "image_url": image_url,
            "url": url,
            "raw": tm_event  # Keep full data for later processing
        })

    html_content = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Ticketmaster Events Review</title>
    <style>
        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }}

        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #f5f5f5;
            padding: 20px;
        }}

        .header {{
            background: white;
            padding: 30px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }}

        h1 {{
            color: #1a1a1a;
            margin-bottom: 10px;
        }}

        .stats {{
            display: flex;
            gap: 20px;
            margin-top: 20px;
        }}

        .stat {{
            background: #f0f0f0;
            padding: 15px 20px;
            border-radius: 8px;
        }}

        .stat-label {{
            font-size: 12px;
            color: #666;
            text-transform: uppercase;
        }}

        .stat-value {{
            font-size: 24px;
            font-weight: bold;
            color: #1a1a1a;
            margin-top: 5px;
        }}

        .stat.approved .stat-value {{ color: #10b981; }}
        .stat.rejected .stat-value {{ color: #ef4444; }}

        .events-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
            gap: 20px;
        }}

        .event-card {{
            background: white;
            border-radius: 12px;
            overflow: hidden;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            transition: transform 0.2s, box-shadow 0.2s;
        }}

        .event-card:hover {{
            transform: translateY(-2px);
            box-shadow: 0 4px 16px rgba(0,0,0,0.15);
        }}

        .event-card.approved {{
            border: 3px solid #10b981;
        }}

        .event-card.rejected {{
            opacity: 0.5;
            border: 3px solid #ef4444;
        }}

        .event-image {{
            width: 100%;
            height: 200px;
            object-fit: cover;
            background: #e5e5e5;
        }}

        .event-content {{
            padding: 20px;
        }}

        .event-title {{
            font-size: 18px;
            font-weight: 600;
            color: #1a1a1a;
            margin-bottom: 10px;
        }}

        .event-meta {{
            display: flex;
            flex-direction: column;
            gap: 8px;
            margin-bottom: 15px;
        }}

        .event-meta-item {{
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 14px;
            color: #666;
        }}

        .event-actions {{
            display: flex;
            gap: 10px;
        }}

        button {{
            flex: 1;
            padding: 12px;
            border: none;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }}

        .btn-approve {{
            background: #10b981;
            color: white;
        }}

        .btn-approve:hover {{
            background: #059669;
        }}

        .btn-reject {{
            background: #ef4444;
            color: white;
        }}

        .btn-reject:hover {{
            background: #dc2626;
        }}

        .footer {{
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: white;
            padding: 20px;
            box-shadow: 0 -2px 8px rgba(0,0,0,0.1);
            display: flex;
            justify-content: center;
        }}

        .btn-download {{
            background: #3b82f6;
            color: white;
            padding: 16px 40px;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }}

        .btn-download:hover {{
            background: #2563eb;
        }}

        .btn-download:disabled {{
            background: #9ca3af;
            cursor: not-allowed;
        }}

        .category-badge {{
            display: inline-block;
            padding: 4px 12px;
            background: #e5e7eb;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
        }}

        .category-CONCERT {{ background: #dbeafe; color: #1e40af; }}
        .category-FESTIVAL {{ background: #fce7f3; color: #9f1239; }}
        .category-CULTURE {{ background: #f3e8ff; color: #6b21a8; }}
        .category-SPORT {{ background: #d1fae5; color: #065f46; }}
    </style>
</head>
<body>
    <div class="header">
        <h1>🎟️ Ticketmaster Events Review</h1>
        <p>Revisa y aprueba los eventos antes de subirlos a Firestore</p>

        <div class="stats">
            <div class="stat">
                <div class="stat-label">Total</div>
                <div class="stat-value" id="total-count">{len(events_data)}</div>
            </div>
            <div class="stat approved">
                <div class="stat-label">Aprobados</div>
                <div class="stat-value" id="approved-count">0</div>
            </div>
            <div class="stat rejected">
                <div class="stat-label">Rechazados</div>
                <div class="stat-value" id="rejected-count">0</div>
            </div>
        </div>
    </div>

    <div class="events-grid" id="events-grid"></div>

    <div class="footer">
        <button class="btn-download" id="download-btn" disabled>
            Descargar aprobados (0)
        </button>
    </div>

    <script>
        const eventsData = {json.dumps(events_data, ensure_ascii=False, indent=2)};

        let approvedEvents = new Set();
        let rejectedEvents = new Set();

        function renderEvents() {{
            const grid = document.getElementById('events-grid');
            grid.innerHTML = '';

            eventsData.forEach(event => {{
                const card = document.createElement('div');
                card.className = 'event-card';
                card.id = `event-${{event.tm_id}}`;

                if (approvedEvents.has(event.tm_id)) {{
                    card.classList.add('approved');
                }} else if (rejectedEvents.has(event.tm_id)) {{
                    card.classList.add('rejected');
                }}

                card.innerHTML = `
                    ${{event.image_url ? `<img src="${{event.image_url}}" class="event-image" alt="${{event.name}}">` : '<div class="event-image"></div>'}}
                    <div class="event-content">
                        <div class="event-title">${{event.name}}</div>
                        <div class="event-meta">
                            <div class="event-meta-item">
                                📅 ${{event.start_date}}
                            </div>
                            <div class="event-meta-item">
                                <span class="category-badge category-${{event.category}}">${{event.category}}</span>
                            </div>
                            <div class="event-meta-item">
                                📍 ${{event.municipality}}
                            </div>
                        </div>
                        <div class="event-actions">
                            <button class="btn-approve" onclick="approveEvent('${{event.tm_id}}')">
                                ✓ Aprobar
                            </button>
                            <button class="btn-reject" onclick="rejectEvent('${{event.tm_id}}')">
                                ✗ Rechazar
                            </button>
                        </div>
                    </div>
                `;

                grid.appendChild(card);
            }});

            updateStats();
        }}

        function approveEvent(eventId) {{
            approvedEvents.add(eventId);
            rejectedEvents.delete(eventId);
            updateCard(eventId);
            updateStats();
        }}

        function rejectEvent(eventId) {{
            rejectedEvents.add(eventId);
            approvedEvents.delete(eventId);
            updateCard(eventId);
            updateStats();
        }}

        function updateCard(eventId) {{
            const card = document.getElementById(`event-${{eventId}}`);
            card.className = 'event-card';

            if (approvedEvents.has(eventId)) {{
                card.classList.add('approved');
            }} else if (rejectedEvents.has(eventId)) {{
                card.classList.add('rejected');
            }}
        }}

        function updateStats() {{
            document.getElementById('approved-count').textContent = approvedEvents.size;
            document.getElementById('rejected-count').textContent = rejectedEvents.size;

            const downloadBtn = document.getElementById('download-btn');
            downloadBtn.disabled = approvedEvents.size === 0;
            downloadBtn.textContent = `Descargar aprobados (${{approvedEvents.size}})`;
        }}

        function downloadApproved() {{
            const approved = eventsData.filter(e => approvedEvents.has(e.tm_id));
            const jsonData = JSON.stringify(approved, null, 2);
            const blob = new Blob([jsonData], {{ type: 'application/json' }});
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'approved_events.json';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            alert(`✅ ${{approved.length}} eventos aprobados descargados como approved_events.json`);
        }}

        document.getElementById('download-btn').addEventListener('click', downloadApproved);

        // Initial render
        renderEvents();
    </script>
</body>
</html>"""

    # Write HTML file
    with open(REVIEW_HTML, 'w', encoding='utf-8') as f:
        f.write(html_content)

    print(f"✅ Review page generated: {REVIEW_HTML}")
    print(f"\n📂 Open in browser: file://{REVIEW_HTML.absolute()}")


def main():
    print("🎟️  Ticketmaster Events Review Generator")
    print("=" * 60)

    # Read API key
    api_key = read_api_key()
    print("✅ Loaded Ticketmaster API key")

    # Fetch events
    events = fetch_ticketmaster_events(api_key)

    if not events:
        print("⚠️  No events found")
        return

    # Generate review HTML
    generate_review_html(events)

    print("=" * 60)
    print("🎉 Done! Open the review page in your browser to approve events.")


if __name__ == "__main__":
    main()
