#!/bin/bash
# Script para borrar datos de la app Mallorca Explorer

# Buscar adb
ADB=$(find ~/Library/Android/sdk -name adb 2>/dev/null | head -1)
if [ -z "$ADB" ]; then
    echo "❌ No se encontró adb"
    echo "Por favor, borra los datos manualmente:"
    echo "1. Abre Settings en el emulador"
    echo "2. Apps → Mallorca Explorer → Storage → Clear Data"
    exit 1
fi

echo "🧹 Borrando datos de com.mallorca.explorer..."
$ADB shell pm clear com.mallorca.explorer

echo "✅ Datos borrados. Ahora abre la app para que ejecute el seed v177"
