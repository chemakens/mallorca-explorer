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
    val galleryPhotosJson: String = "[]",
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
