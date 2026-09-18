#!/bin/bash
cd ~/Code/mallorca-explorer/tools

echo "========================================"
echo "  MALLORCA EXPLORER — Actualizar Eventos"
echo "========================================"
echo ""

# 1. Scraping completo (firesifestes mejorado + todos los demás)
echo "🌐 Paso 1/4: Scraping completo de eventos..."
python3 -u fetch_web_events.py
echo ""

# 2. Abrir review.html para revisión manual
echo "👀 Paso 2/4: Abriendo revisión de eventos en el navegador..."
echo "   → Revisa y aprueba/rechaza eventos en http://localhost:8765"
echo "   → Descarga 'approved_events.json' cuando termines"
echo ""
open http://localhost:8765 2>/dev/null || python3 -m http.server 8765 --directory . &
SERVER_PID=$!

echo ""
echo "  Cuando hayas terminado la revisión y descargado approved_events.json,"
read -p "  pulsa ENTER para continuar con la subida a Firestore... " _

# Matar servidor si lo arrancamos nosotros
kill $SERVER_PID 2>/dev/null
echo ""

# 3. Limpiar eventos web antiguos de Firestore
echo "🗑️  Paso 3/4: Limpiando eventos antiguos de Firestore..."
echo "yes" | python3 -u delete_web_events.py
echo ""

# 4. Subir eventos nuevos y limpiar
echo "⬆️  Paso 4/4: Subiendo eventos aprobados a Firestore..."
python3 -u upload_approved_events.py
echo ""

echo "🧹 Eliminando eventos pasados restantes..."
echo "yes" | python3 -u delete_past_events.py
echo ""

echo "🚫 Eliminando eventos en lista negra..."
python3 -u delete_blacklisted_events.py
echo ""

echo "========================================"
echo "  ✅ ¡Actualización completada!"
echo "========================================"
