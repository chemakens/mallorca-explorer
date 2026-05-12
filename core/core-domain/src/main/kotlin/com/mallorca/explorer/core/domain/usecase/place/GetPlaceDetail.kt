package com.mallorca.explorer.core.domain.usecase.place

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.FavoriteRepository
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetPlaceDetail @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val favoriteRepository: FavoriteRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(id: String): Flow<Place> =
        combine(
            placeRepository.getPlaceById(id).filterNotNull(),
            favoriteRepository.isFavorite(id),
        ) { place, isFav -> place.copy(isFavorite = isFav) }
            .flowOn(dispatcher)
}
