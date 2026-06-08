package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleEs: String,
    val titleDe: String = "",
    val titleRu: String = "",
    val titleZh: String = "",
    val description: String,
    val descriptionEs: String,
    val descriptionDe: String = "",
    val descriptionRu: String = "",
    val descriptionZh: String = "",
    val category: String,
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
