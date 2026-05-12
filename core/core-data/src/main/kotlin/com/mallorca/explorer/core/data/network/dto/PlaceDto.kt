package com.mallorca.explorer.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val id: String,
    val name: String,
    @SerialName("name_es") val nameEs: String = "",
    val description: String = "",
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val municipality: String = "",
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("price_level") val priceLevel: String? = null,
    val rating: Float? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
    val tips: List<String> = emptyList(),
    val website: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
)
