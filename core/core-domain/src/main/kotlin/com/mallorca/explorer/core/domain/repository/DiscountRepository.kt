package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.Discount
import kotlinx.coroutines.flow.Flow

interface DiscountRepository {
    fun getDiscountsForPlace(placeId: String): Flow<List<Discount>>
}
