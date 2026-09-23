#!/usr/bin/env python3
"""
Debug script para scrape_bierkoenig() — eventos de nightlife en Playa de Palma
"""

import sys
import os

# Añadir el directorio tools al path para importar fetch_web_events
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fetch_web_events import scrape_bierkoenig

if __name__ == "__main__":
    print("🚀 Ejecutando scrape_bierkoenig()...\n")

    events = scrape_bierkoenig()

    print("\n" + "="*90)
    print(f"TOTAL EVENTOS EXTRAÍDOS: {len(events)}")
    print("="*90)

    if events:
        print("\nEventos encontrados:")
        print("-" * 90)
        print(f"{'Fecha':<12} | {'Hora':<6} | {'Artista':<50} | {'Location':<15}")
        print("-" * 90)

        for event in events:
            fecha = event.get("start_date", "N/A")[:10]
            hora = event.get("start_time", "N/A")
            titulo = event.get("title", "N/A")[:50]
            location = event.get("location", "N/A")[:15]
            print(f"{fecha:<12} | {hora:<6} | {titulo:<50} | {location:<15}")
    else:
        print("\n⚠️  No se encontraron eventos")

    print("\n" + "="*90)
