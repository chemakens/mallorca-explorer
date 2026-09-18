# Mallorca Explorer Tools

Scripts de utilidad para gestión de datos del proyecto.

---

## 📅 `fetch_ticketmaster_events.py`

Sincroniza eventos de Ticketmaster Discovery API a Firestore.

### Requisitos previos

1. **Python 3.8+**

2. **Dependencias:**
   ```bash
   pip install -r requirements.txt
   ```

3. **API Key de Ticketmaster:**
   - Obtener en: https://developer.ticketmaster.com/
   - Añadir a `local.properties` (raíz del proyecto):
     ```
     TICKETMASTER_API_KEY=tu_api_key_aqui
     ```

4. **Credenciales de Firebase:**
   - Ir a [Firebase Console](https://console.firebase.google.com/)
   - Seleccionar proyecto Mallorca Explorer
   - **Project Settings** → **Service Accounts**
   - Click **"Generate new private key"**
   - Guardar como `serviceAccountKey.json` en la raíz del proyecto
   
   **O** usar variable de entorno:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
   ```

### Uso

```bash
cd tools
python fetch_ticketmaster_events.py
```

### Funcionamiento

1. **Fetch:** Busca eventos en Ticketmaster para Mallorca/Baleares (`countryCode=ES`, `stateCode=IB`)

2. **Mapeo de categorías:**
   - Music → `CONCERT`
   - Sports → `SPORT`
   - Arts & Theatre → `CULTURE`
   - Family/Festival → `FESTIVAL`
   - Miscellaneous → `CULTURE`

3. **Upsert a Firestore:**
   - Colección: `events`
   - ID: `tm-{ticketmaster_id}` (prefijo para distinguir de eventos manuales)
   - **NO borra eventos existentes** — solo crea/actualiza los de Ticketmaster

4. **Sincronización automática:**
   - La app sincroniza desde Firestore automáticamente
   - Los eventos nuevos aparecerán en la siguiente sincronización

### Campos mapeados

| Firestore | Ticketmaster | Notas |
|-----------|--------------|-------|
| `id` | `"tm-" + id` | Prefijo para IDs de Ticketmaster |
| `title` | `name` | |
| `description` | `info` (o generado) | Máx 500 chars |
| `category` | `classifications` → mapeado | Ver tabla arriba |
| `start_date` | `dates.start` | Formato: `YYYY-MM-DD` |
| `municipality` | `_embedded.venues[0].city.name` | |
| `address` | `_embedded.venues[0].address.line1` | |
| `is_free` | `!priceRanges` | |
| `price` | `priceRanges[0].min-max` | Formato: `€X-Y` |
| `image_url` | `images[0].url` | |
| `website_url` | `url` | Link oficial del evento |

### Logs

```
🚀 Ticketmaster → Firestore Event Sync
============================================================
✅ Loaded Ticketmaster API key from local.properties
✅ Connected to Firestore
🔍 Fetching events from Ticketmaster API...
✅ Fetched 47 events from Ticketmaster
🔄 Mapping events to Firestore schema...
✅ Mapped 45 events
📤 Syncing 45 events to Firestore...
✅ Successfully synced 45/45 events to Firestore
============================================================
🎉 Done! 45 events synced to Firestore
   Events will appear in the app after next sync (automatic)
```

### Errores comunes

**❌ TICKETMASTER_API_KEY not found**
- Añadir `TICKETMASTER_API_KEY=...` a `local.properties`

**❌ Firebase service account key not found**
- Descargar desde Firebase Console (ver pasos arriba)
- Guardar como `serviceAccountKey.json` en raíz

**❌ Missing dependencies**
- Ejecutar: `pip install -r requirements.txt`

---

## 📅 `fetch_eventbrite_events.py`

Sincroniza eventos de Eventbrite API a Firestore.

### Requisitos previos

1. **Python 3.8+**

2. **Dependencias:**
   ```bash
   pip install -r requirements.txt
   ```

3. **API Key de Eventbrite (Private Token):**
   - Obtener en: https://www.eventbrite.com/platform/api#/introduction/authentication
   - Ir a Account Settings → Developer Links → API Keys
   - Copiar tu **Private Token**
   - Añadir a `local.properties` (raíz del proyecto):
     ```
     EVENTBRITE_API_KEY=tu_private_token_aqui
     ```

4. **Credenciales de Firebase:**
   - Mismas que para Ticketmaster (ver arriba)

### Uso

```bash
cd tools
python fetch_eventbrite_events.py
```

### Funcionamiento

1. **Fetch:** Busca eventos en Eventbrite en Mallorca (lat/lng: 39.6953, 3.0176, radio 50km)

2. **Mapeo de categorías:**
   - 103 (Music) → `CONCERT`
   - 108 (Sports & Fitness) → `SPORT`
   - 105, 107, 102, 104 (Arts, Science, Film) → `CULTURE`
   - 110, 111 (Food & Drink, Travel) → `FESTIVAL`
   - 113 (Community) → `CULTURE`
   - Resto → `CULTURE`

3. **Upsert a Firestore:**
   - Colección: `events`
   - ID: `eb-{eventbrite_id}` (prefijo "eb-" para distinguir)
   - **NO borra eventos existentes** — convive con eventos manuales y de Ticketmaster

4. **Sincronización automática:**
   - La app sincroniza desde Firestore automáticamente
   - Los eventos nuevos aparecerán en la siguiente sincronización

### Campos mapeados

| Firestore | Eventbrite | Notas |
|-----------|------------|-------|
| `id` | `"eb-" + id` | Prefijo para IDs de Eventbrite |
| `title` | `name.text` | |
| `description` | `description.text` | Máx 500 chars |
| `category` | `category.id` → mapeado | Ver tabla arriba |
| `start_date` | `start.local` o `start.utc` | Formato: `YYYY-MM-DD` |
| `end_date` | `end.local` o `end.utc` | Puede ser mismo día |
| `municipality` | `venue.address.city` | |
| `address` | `venue.address.address_1` | |
| `is_free` | `is_free` | Boolean directo |
| `price` | `ticket_availability.{min,max}_ticket_price` | Formato: `€X.XX-€Y.YY` |
| `image_url` | `logo.url` | |
| `website_url` | `url` | Link oficial del evento |

### Logs

```
🚀 Eventbrite → Firestore Event Sync
============================================================
✅ Loaded Eventbrite API key from local.properties
✅ Connected to Firestore
🔍 Fetching events from Eventbrite API...
✅ Fetched 32 events from Eventbrite
🔄 Mapping events to Firestore schema...
✅ Mapped 30 events
📤 Syncing 30 events to Firestore...
✅ Successfully synced 30/30 events to Firestore
============================================================
🎉 Done! 30 events synced to Firestore
   Events will appear in the app after next sync (automatic)
```

### Errores comunes

**❌ EVENTBRITE_API_KEY not found**
- Añadir `EVENTBRITE_API_KEY=...` a `local.properties`

**❌ Firebase service account key not found**
- Descargar desde Firebase Console (ver pasos arriba)
- Guardar como `serviceAccountKey.json` en raíz

**❌ Missing dependencies**
- Ejecutar: `pip install -r requirements.txt`

---

## 🔄 Ejecutar ambos scripts

Para sincronizar eventos de todas las fuentes:

```bash
cd tools
python fetch_ticketmaster_events.py
python fetch_eventbrite_events.py
```

Los eventos se distinguen por su prefijo de ID:
- **Sin prefijo**: eventos manuales (añadidos directamente a Firestore)
- **`tm-`**: eventos de Ticketmaster
- **`eb-`**: eventos de Eventbrite

---

## Seguridad

⚠️ **NUNCA commitear:**
- `serviceAccountKey.json` (ya en `.gitignore`)
- `local.properties` (ya en `.gitignore`)

Las API keys y credenciales son **privadas** y no deben subirse a git.
