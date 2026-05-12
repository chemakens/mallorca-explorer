package com.mallorca.explorer.core.domain.model

data class Itinerary(
    val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val durationDays: Int,
    val difficulty: Difficulty? = null,
    val places: List<ItineraryStop> = emptyList(),
    val coverPhotoUrl: String = "",
    val totalDistanceKm: Float? = null,
    val highlights: List<String> = emptyList(),
    val bestSeasons: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

data class ItineraryStop(
    val place: Place,
    val order: Int,
    val suggestedDurationMinutes: Int = 60,
    val note: String? = null,
    val dayNumber: Int = 1,
)

enum class Difficulty { EASY, MODERATE, HARD }
