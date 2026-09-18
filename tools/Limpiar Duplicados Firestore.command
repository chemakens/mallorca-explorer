#!/bin/bash
# Limpia eventos duplicados en Firestore

cd /Users/usuario/Code/mallorca-explorer/tools

echo "=================================================="
echo "  🧹  LIMPIAR DUPLICADOS - Mallorca Explorer"
echo "=================================================="
echo ""
echo "🔍 Primero modo dry-run para ver qué se borraría..."
echo ""
python3 /Users/usuario/Code/mallorca-explorer/tools/dedup_firestore.py --dry-run

echo ""
echo "--------------------------------------------------"
echo ""
python3 /Users/usuario/Code/mallorca-explorer/tools/dedup_firestore.py

echo ""
read -p "Pulsa Enter para cerrar..."
