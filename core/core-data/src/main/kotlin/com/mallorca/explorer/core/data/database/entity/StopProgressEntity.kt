package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity

@Entity(tableName = "stop_progress", primaryKeys = ["itineraryId", "placeId"])
data class StopProgressEntity(
    val itineraryId: String,
    val placeId: String,
    val visitedEpoch: Long,
)
