package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.DiscountDao
import com.mallorca.explorer.core.domain.model.Discount
import com.mallorca.explorer.core.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DiscountRepositoryImpl @Inject constructor(
    private val discountDao: DiscountDao,
) : DiscountRepository {

    override fun getDiscountsForPlace(placeId: String): Flow<List<Discount>> =
        discountDao.getActiveForPlace(placeId, System.currentTimeMillis()).map { list ->
            list.map { e ->
                Discount(
                    id = e.id,
                    placeId = e.placeId,
                    partnerName = e.partnerName,
                    headline = e.headline,
                    terms = e.terms,
                    code = e.code,
                    validUntilEpoch = e.validUntilEpoch,
                )
            }
        }
}
