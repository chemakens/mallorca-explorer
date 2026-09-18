#!/bin/bash
# Scraper Fires i Festes — doble clic para ejecutar

cd "$(dirname "$0")"
TOOLS_DIR="$(pwd)"

echo "🔍 Iniciando scraping de Fires i Festes..."
echo ""

# Matar proceso anterior en el puerto si lo hubiera
lsof -ti:8765 | xargs kill -9 2>/dev/null

# Ejecutar el scraper
python3 "$TOOLS_DIR/scrape_firesifestes.py"

echo ""
echo "✅ Listo. Revisa firesifestes_output.json en la carpeta tools/"
echo ""
read -p "Pulsa Enter para cerrar..."
