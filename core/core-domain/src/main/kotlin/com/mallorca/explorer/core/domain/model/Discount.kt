package com.mallorca.explorer.core.domain.model

data class Discount(
    val id: String,
    val placeId: String,
    val partnerName: String,
    val headline: String,
    val terms: String,
    val code: String,
    val validUntilEpoch: Long,
)
