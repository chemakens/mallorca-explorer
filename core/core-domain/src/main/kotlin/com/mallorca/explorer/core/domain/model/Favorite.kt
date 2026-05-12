package com.mallorca.explorer.core.domain.model

import java.time.Instant

data class Favorite(
    val placeId: String,
    val savedAt: Instant = Instant.now(),
)
