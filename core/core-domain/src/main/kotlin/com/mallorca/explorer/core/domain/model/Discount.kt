package com.mallorca.explorer.core.domain.model

data class Discount(
    val id: String,
    val placeId: String,
    val partnerName: String,
    val headline: String,
    val headlineEs: String = "",
    val headlineDe: String = "",
    val headlineRu: String = "",
    val headlineZh: String = "",
    val terms: String,
    val termsEs: String = "",
    val termsDe: String = "",
    val termsRu: String = "",
    val termsZh: String = "",
    val code: String,
    val validUntilEpoch: Long,
)
