package com.mallorca.explorer.core.domain.usecase.place

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetPlacesByCategory @Inject constructor(
    private val placeRepository: PlaceRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(category: Category): Flow<List<Place>> =
        placeRepository.getPlacesByCategory(category).flowOn(dispatcher)
}
