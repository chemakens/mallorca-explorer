#!/usr/bin/env python3
"""
Debug script para scrape_entradium() — verifica barrido general + organizadores
"""

import sys
import os

# Añadir el directorio tools al path para importar fetch_web_events
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fetch_web_events import scrape_entradium

if __name__ == "__main__":
    print("🚀 Ejecutando scrape_entradium()...\n")

    events = scrape_entradium()

    print("\n" + "="*80)
    print(f"TOTAL EVENTOS EXTRAÍDOS: {len(events)}")
    print("="*80)

    if events:
        print("\nPrimeros 30 eventos:")
        print("-" * 80)
        print(f"{'Fecha':<12} | {'Título':<40} | {'Municipio':<12} | {'Precio':<15}")
        print("-" * 80)

        for event in events[:30]:
            fecha = event.get("start_date", "N/A")[:10]
            titulo = event.get("title", "N/A")[:40]
            municipio = event.get("municipality", "N/A")[:12]
            precio = event.get("price", "N/A")[:15]
            print(f"{fecha:<12} | {titulo:<40} | {municipio:<12} | {precio:<15}")

        if len(events) > 30:
            print(f"\n... y {len(events) - 30} eventos más")
    else:
        print("\n⚠️  No se encontraron eventos")

    print("\n" + "="*80)
