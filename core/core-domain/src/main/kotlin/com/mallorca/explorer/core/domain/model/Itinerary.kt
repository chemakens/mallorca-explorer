package com.mallorca.explorer.core.domain.model

data class Itinerary(
    val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val durationDays: Int,
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
) {
    val isQrLanding: Boolean get() = qrEntryPoint != null
    val isSUPRoute: Boolean get() = weatherConfig != null
}

data class ItineraryStop(
    val place: Place,
    val order: Int,
    val suggestedDurationMinutes: Int = 60,
    val note: String? = null,
    val dayNumber: Int = 1,
)

enum class Difficulty { EASY, MODERATE, HARD }

data class SupWeatherConfig(
    val fetchLat: Double,
    val fetchLng: Double,
    val beachFacingDeg: Int,
    val maxSafeWindKnots: Float,
    val maxSafeGustKnots: Float,
    val tramuntanaWarning: Boolean,
    val tramuntanaNoteEs: String,
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
    val role: WaypointRole,
    val lat: Double,
    val lng: Double,
    val noteEs: String,
    val distanceFromPrevKm: Float,
    val conditional: Boolean = false,
    val conditionNoteEs: String = "",
)

enum class WaypointRole { LAUNCH, WAYPOINT, WAYPOINT_CONDITIONAL, FINISH }
