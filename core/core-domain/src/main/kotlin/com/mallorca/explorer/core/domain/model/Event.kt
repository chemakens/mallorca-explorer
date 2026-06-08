package com.mallorca.explorer.core.domain.model

data class Event(
    val id: String,
    val title: String,
    val titleEs: String,
    val titleDe: String,
    val titleRu: String,
    val titleZh: String,
    val description: String,
    val descriptionEs: String,
    val descriptionDe: String,
    val descriptionRu: String,
    val descriptionZh: String,
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

enum class EventCategory(val emoji: String) {
    MARKET("🛒"),
    FESTIVAL("🎪"),
    CONCERT("🎵"),
    CULTURE("🎭"),
    SPORT("⚽"),
}
