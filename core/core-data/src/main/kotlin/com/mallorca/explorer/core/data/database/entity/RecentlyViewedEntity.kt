package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_viewed")
data class RecentlyViewedEntity(
    @PrimaryKey val placeId: String,
    val viewedAtEpoch: Long,
)
