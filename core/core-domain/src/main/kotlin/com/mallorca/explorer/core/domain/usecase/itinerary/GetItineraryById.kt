package com.mallorca.explorer.core.domain.usecase.itinerary

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetItineraryById @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(id: String): Flow<Itinerary> =
        itineraryRepository.getItineraryById(id).filterNotNull().flowOn(dispatcher)
}
