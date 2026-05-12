package com.mallorca.explorer.core.domain.usecase.place

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class SearchPlaces @Inject constructor(
    private val placeRepository: PlaceRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(query: String): Flow<List<Place>> =
        placeRepository.searchPlaces(query.trim()).flowOn(dispatcher)
}
