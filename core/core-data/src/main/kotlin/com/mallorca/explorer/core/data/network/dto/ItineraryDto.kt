package com.mallorca.explorer.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItineraryDto(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String,
    @SerialName("duration_days") val durationDays: Int = 1,
    val difficulty: String? = null,
    val stops: List<ItineraryStopDto> = emptyList(),
    @SerialName("cover_photo_url") val coverPhotoUrl: String = "",
    @SerialName("total_distance_km") val totalDistanceKm: Float? = null,
    val highlights: List<String> = emptyList(),
    @SerialName("best_seasons") val bestSeasons: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class ItineraryStopDto(
    @SerialName("place_id") val placeId: String,
    val order: Int,
    @SerialName("suggested_duration_minutes") val suggestedDurationMinutes: Int = 60,
    val note: String? = null,
    @SerialName("day_number") val dayNumber: Int = 1,
)
