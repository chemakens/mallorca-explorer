package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "itineraries")
data class ItineraryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val durationDays: Int,
    val difficulty: String?,
    val coverPhotoUrl: String,
    val totalDistanceKm: Float?,
    val highlightsJson: String,
    val bestSeasonsJson: String,
    val tagsJson: String,
    // JSON: {"es":{"title":"…","description":"…","highlights":[…]},"de":{…}}
    val translationsJson: String = "{}",
    // SUP/QR extended fields — DB v9
    val weatherConfigJson: String? = null,
    val qrEntryPointJson: String? = null,
    val commercialBlockJson: String? = null,
    val routeWaypointsJson: String = "[]",
    val routePolylinePointsJson: String = "[]",
    val galleryPhotosJson: String = "[]",
    // DB v18: trazado KML revisado a mano -> dibujar línea aunque tenga pocos puntos
    val routeTrackReviewed: Boolean = false,
    // DB v19: duración en horas ya formateada («3 h», «3-3,5 h») para rutas de <1 día.
    val durationHoursText: String? = null,
    // DB v20: variantes de trazado (JSON). "[]" = ruta de una sola línea (comportamiento previo).
    val routeVariantsJson: String = "[]",
    // DB v21: senderismo — tipo de ruta (CIRCULAR/LINEAR/OUT_AND_BACK), desnivel acumulado
    // (m) y aviso de acceso (es/en/de). El tipo de waypoint viaja dentro de routeWaypointsJson.
    val routeType: String? = null,
    val ascentM: Int? = null,
    val descentM: Int? = null,
    val accessWarning: String? = null,
    val accessWarningEs: String? = null,
    val accessWarningDe: String? = null,
)

@Entity(tableName = "itinerary_stops", primaryKeys = ["itineraryId", "placeId"])
data class ItineraryStopEntity(
    val itineraryId: String,
    val placeId: String,
    val order: Int,
    val suggestedDurationMinutes: Int,
    val note: String?,
    val noteEs: String? = null,
    val noteDe: String? = null,
    val dayNumber: Int,
)
