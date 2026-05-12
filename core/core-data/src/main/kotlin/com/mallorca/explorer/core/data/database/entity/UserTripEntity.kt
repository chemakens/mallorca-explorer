package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_trips")
data class UserTripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
    val notes: String?,
    val isSynced: Boolean,
)

@Entity(tableName = "user_trip_stops", primaryKeys = ["tripId", "placeId"])
data class UserTripStopEntity(
    val tripId: String,
    val placeId: String,
    val order: Int,
    val userNote: String?,
    val suggestedDurationMinutes: Int,
    val isVisited: Boolean,
    val visitedAtEpoch: Long?,
)
