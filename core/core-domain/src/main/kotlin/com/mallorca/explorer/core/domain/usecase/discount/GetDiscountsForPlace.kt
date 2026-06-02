package com.mallorca.explorer.core.domain.usecase.discount

import com.mallorca.explorer.core.domain.model.Discount
import com.mallorca.explorer.core.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDiscountsForPlace @Inject constructor(private val repo: DiscountRepository) {
    operator fun invoke(placeId: String): Flow<List<Discount>> = repo.getDiscountsForPlace(placeId)
}
