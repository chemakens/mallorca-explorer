package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discounts")
data class DiscountEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val partnerName: String,
    val headline: String,
    val terms: String,
    val code: String,
    val validUntilEpoch: Long,
)
