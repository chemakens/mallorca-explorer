package com.mallorca.explorer.core.domain.model

import java.time.Instant

data class UserTrip(
    val id: String,
    val name: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val stops: List<UserTripStop> = emptyList(),
    val notes: String? = null,
    val isSynced: Boolean = false,
) {
    val totalDurationMinutes: Int get() = stops.sumOf { it.suggestedDurationMinutes }
    val stopCount: Int get() = stops.size
}

data class UserTripStop(
    val place: Place,
    val order: Int,
    val userNote: String? = null,
    val suggestedDurationMinutes: Int = 60,
    val isVisited: Boolean = false,
    val visitedAt: Instant? = null,
)
