package com.mallorca.explorer.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val id: String,
    val name: String,
    @SerialName("name_es") val nameEs: String = "",
    @SerialName("name_de") val nameDe: String = "",
    @SerialName("name_ru") val nameRu: String = "",
    @SerialName("name_zh") val nameZh: String = "",
    val description: String = "",
    @SerialName("description_es") val descriptionEs: String = "",
    @SerialName("description_de") val descriptionDe: String = "",
    @SerialName("description_ru") val descriptionRu: String = "",
    @SerialName("description_zh") val descriptionZh: String = "",
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
    @SerialName("tips_es") val tipsEs: List<String> = emptyList(),
    @SerialName("tips_de") val tipsDe: List<String> = emptyList(),
    @SerialName("tips_ru") val tipsRu: List<String> = emptyList(),
    @SerialName("tips_zh") val tipsZh: List<String> = emptyList(),
    val website: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("ticket_url") val ticketUrl: String? = null,
    val tags: List<String> = emptyList(),
)
