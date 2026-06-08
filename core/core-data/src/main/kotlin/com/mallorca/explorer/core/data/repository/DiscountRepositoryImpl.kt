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
                    headlineEs = e.headlineEs,
                    headlineDe = e.headlineDe,
                    headlineRu = e.headlineRu,
                    headlineZh = e.headlineZh,
                    terms = e.terms,
                    termsEs = e.termsEs,
                    termsDe = e.termsDe,
                    termsRu = e.termsRu,
                    termsZh = e.termsZh,
                    code = e.code,
                    validUntilEpoch = e.validUntilEpoch,
                )
            }
        }
}
