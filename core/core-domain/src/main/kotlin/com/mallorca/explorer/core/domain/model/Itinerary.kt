package com.mallorca.explorer.core.domain.model

data class Itinerary(
    val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val durationDays: Int,
    // Duración en HORAS para rutas de menos de un día, como texto ya formateado
    // («3 h», «3-3,5 h»). Si está presente, manda sobre durationDays al mostrarla.
    // Opcional: las rutas de varios días lo dejan null y siguen mostrando «Nd».
    val durationHoursText: String? = null,
    val difficulty: Difficulty? = null,
    val places: List<ItineraryStop> = emptyList(),
    val coverPhoto: PlaceImage = PlaceImage(""),
    val galleryPhotos: List<PlaceImage> = emptyList(),
    val totalDistanceKm: Float? = null,
    val highlights: List<String> = emptyList(),
    val bestSeasons: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val weatherConfig: SupWeatherConfig? = null,
    val qrEntryPoint: QrEntryPoint? = null,
    val commercialBlock: CommercialBlock? = null,
    val routeWaypoints: List<RouteWaypoint> = emptyList(),
    val routePolylinePoints: List<LatLng> = emptyList(),
    // true = el trazado procede de un KML revisado a mano (Google My Maps) y se dibuja como
    // línea en el mapa aunque tenga pocos puntos. Sustituye a la antigua heurística de nº de
    // puntos (MIN_TRACK_POINTS_FOR_SUP_LINE). Rutas SUP sin KML -> false (solo marcadores).
    val routeTrackReviewed: Boolean = false,
    // Variantes de trazado (misma salida, mismo nombre en el KML). Vacío = ruta de una sola
    // línea, que se comporta EXACTAMENTE igual que antes (usa routePolylinePoints). Cuando hay
    // >=2 variantes, el mapa dibuja todas con colores distintos y una leyenda. La ficha, las
    // fotos y el semáforo son COMUNES a toda la ruta (las variantes solo cambian el trazado,
    // la distancia y, opcionalmente, dificultad/duración).
    val routeVariants: List<RouteVariant> = emptyList(),
    // Senderismo (GPX de gpx.studio): tipo de ruta, desnivel acumulado y aviso de acceso
    // (p. ej. zonas con permiso o zonas cerradas). Todos opcionales: las rutas que no son de
    // senderismo los dejan en null y se comportan igual que antes.
    val routeType: RouteType? = null,
    val ascentM: Int? = null,
    val descentM: Int? = null,
    val accessWarning: String? = null,
    val accessWarningEs: String? = null,
    val accessWarningDe: String? = null,
) {
    val isQrLanding: Boolean get() = qrEntryPoint != null
    val isSUPRoute: Boolean get() = weatherConfig != null
    // Hay varias variantes de trazado que mostrar (una sola línea NO cuenta como "con variantes").
    val hasVariants: Boolean get() = routeVariants.size >= 2
}

/** Forma del recorrido de una ruta de senderismo. */
enum class RouteType { CIRCULAR, LINEAR, OUT_AND_BACK }

/**
 * Itinerarios HIKING que se OCULTAN de las listas/tarjetas de senderismo (mapa, Explorar,
 * lista de itinerarios) por solaparse con las rutas nuevas de GPX, pero cuyos datos se
 * CONSERVAN en el seed y siguen accesibles por id (deep link o navegación directa).
 * Para reactivar uno, basta quitarlo de este conjunto (no hay que tocar el seed).
 *   itin-cami-port-canonge  ≈ itin-hike-port-des-canonge
 *   itin-cala-marmols-faro  ≈ itin-hike-faro-ses-salines
 */
val HIDDEN_HIKING_ITINERARY_IDS: Set<String> = setOf(
    "itin-cami-port-canonge",
    "itin-cala-marmols-faro",
)

/** Aviso de acceso localizado (zona con permiso, cerrada o militar); null si no hay. */
fun Itinerary.localizedAccessWarning(locale: String): String? = when (locale) {
    "es" -> accessWarningEs ?: accessWarning
    "de" -> accessWarningDe ?: accessWarning
    else -> accessWarning
}?.takeIf { it.isNotBlank() }

/**
 * Una variante de trazado de una ruta: misma salida y mismo nombre que las demás, pero recorrido
 * distinto (p. ej. "Variante sur" vs "Variante norte"). La etiqueta es obligatoria (es/en/de) y
 * la dificultad/duración son opcionales (si faltan, se usan las de la ruta).
 */
data class RouteVariant(
    val id: String,
    val label: String,
    val labelEs: String = "",
    val labelDe: String = "",
    val polylinePoints: List<LatLng> = emptyList(),
    val distanceKm: Float? = null,
    val difficulty: Difficulty? = null,
    val durationHoursText: String? = null,
    // Color fijado opcional (#RRGGBB). Si es null, el mapa asigna uno de su paleta por orden.
    val color: String? = null,
)

/** Etiqueta de la variante en el idioma pedido, con respaldo al texto base (en). */
fun RouteVariant.localizedLabel(locale: String): String = when (locale) {
    "es" -> labelEs.ifBlank { label }
    "de" -> labelDe.ifBlank { label }
    else -> label
}.ifBlank { label }

data class ItineraryStop(
    val place: Place,
    val order: Int,
    val suggestedDurationMinutes: Int = 60,
    val note: String? = null,
    val dayNumber: Int = 1,
)

/**
 * Etiqueta de duración para cabecera y tarjetas. Si la ruta trae `durationHoursText`
 * (rutas de menos de un día: «3 h», «3-3,5 h»), se usa tal cual; si no, «Nd» por días.
 */
fun Itinerary.durationLabel(): String =
    durationHoursText?.takeIf { it.isNotBlank() } ?: "${durationDays}d"

enum class Difficulty { EASY, MODERATE, HARD }

data class SupWeatherConfig(
    val fetchLat: Double,
    val fetchLng: Double,
    val beachFacingDeg: Int,
    val maxSafeWindKnots: Float,
    val maxSafeGustKnots: Float,
    val tramuntanaWarning: Boolean,
    val tramuntanaNote: String,
    val tramuntanaNoteEs: String,
    val tramuntanaNoteDe: String,
    val caveEntryMaxWaveM: Float? = null,
    val caveEntryMaxWindKnots: Float? = null,
)

data class QrEntryPoint(
    val qrId: String,
    val sourceBeach: String,
    val deepLink: String,
    val showWeatherBlockOnEntry: Boolean,
)

data class CommercialBlock(
    val enabled: Boolean,
    val partnerName: String,
    val ctaLabelEs: String,
    val phone: String,
    val whatsapp: String,
    val partnerLat: Double,
    val partnerLng: Double,
    val discountCode: String,
    val discountPct: Int,
    val discountNoteEs: String,
)

data class RouteWaypoint(
    val order: Int,
    val name: String,
    val nameEs: String,
    val nameDe: String,
    val role: WaypointRole,
    val lat: Double,
    val lng: Double,
    val note: String,
    val noteEs: String,
    val noteDe: String,
    val distanceFromPrevKm: Float,
    val conditional: Boolean = false,
    val conditionNote: String = "",
    val conditionNoteEs: String = "",
    val conditionNoteDe: String = "",
    // Tipo de punto para elegir el icono del mapa/lista (senderismo). OTHER por defecto.
    val type: WaypointType = WaypointType.OTHER,
)

enum class WaypointRole { LAUNCH, WAYPOINT, WAYPOINT_CONDITIONAL, FINISH }

/** Clase de punto de interés para el icono. START/END derivan del rol de salida/llegada. */
enum class WaypointType {
    START, END, PARKING, WATER, VIEWPOINT, HERITAGE, CAVE, BEACH, TECHNICAL, WARNING, BUS, PHOTO, SUMMIT, OTHER
}
