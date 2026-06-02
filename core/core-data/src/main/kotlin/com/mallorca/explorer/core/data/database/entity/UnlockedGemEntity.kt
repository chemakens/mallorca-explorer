package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlocked_gems")
data class UnlockedGemEntity(
    @PrimaryKey val placeId: String,
    val unlockedEpoch: Long,
)
