package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameEs: String,
    val description: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val municipality: String,
    val photoUrlsJson: String,
    val thumbnailUrl: String,
    val openingHoursJson: String?,
    val priceLevel: String?,
    val rating: Float?,
    val reviewCount: Int,
    val tipsJson: String,
    val website: String?,
    val phoneNumber: String?,
    val lastUpdatedEpoch: Long,
)
