package com.mallorca.explorer.core.domain.model

import java.time.Instant

data class Place(
    val id: String,
    val name: String,
    val nameEs: String,
    val description: String,
    val category: Category,
    val subCategories: List<String> = emptyList(),
    val location: LatLng,
    val address: String? = null,
    val municipality: String,
    val photoUrls: List<String> = emptyList(),
    val thumbnailUrl: String = "",
    val openingHours: OpeningHours? = null,
    val priceLevel: PriceLevel? = null,
    val rating: Float? = null,
    val reviewCount: Int = 0,
    val tips: List<String> = emptyList(),
    val website: String? = null,
    val phoneNumber: String? = null,
    val isOfflineCached: Boolean = false,
    val isFavorite: Boolean = false,
    val lastUpdated: Instant = Instant.now(),
)

data class LatLng(val latitude: Double, val longitude: Double)

data class OpeningHours(
    val periods: List<OpeningPeriod> = emptyList(),
    val displayText: String,
)

data class OpeningPeriod(
    val dayOfWeek: Int, // 1=Mon … 7=Sun
    val openHour: Int,
    val openMinute: Int,
    val closeHour: Int,
    val closeMinute: Int,
)

enum class PriceLevel { FREE, BUDGET, MID, EXPENSIVE }
