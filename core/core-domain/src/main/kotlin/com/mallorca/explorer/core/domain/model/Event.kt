package com.mallorca.explorer.core.domain.model

data class Event(
    val id: String,
    val title: String,
    val titleEs: String,
    val description: String,
    val descriptionEs: String,
    val category: EventCategory,
    val startDateEpoch: Long,
    val endDateEpoch: Long?,
    val municipality: String,
    val address: String?,
    val isFree: Boolean,
    val price: String?,
    val imageUrl: String?,
    val isRecurring: Boolean,
    val recurringDayOfWeek: Int?,
)

enum class EventCategory(val emoji: String, val label: String) {
    MARKET("🛒", "Mercado"),
    FESTIVAL("🎪", "Festival"),
    CONCERT("🎵", "Concierto"),
    CULTURE("🎭", "Cultura"),
    SPORT("⚽", "Deporte"),
}
