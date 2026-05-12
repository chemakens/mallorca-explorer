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
)

@Entity(tableName = "itinerary_stops", primaryKeys = ["itineraryId", "placeId"])
data class ItineraryStopEntity(
    val itineraryId: String,
    val placeId: String,
    val order: Int,
    val suggestedDurationMinutes: Int,
    val note: String?,
    val dayNumber: Int,
)
