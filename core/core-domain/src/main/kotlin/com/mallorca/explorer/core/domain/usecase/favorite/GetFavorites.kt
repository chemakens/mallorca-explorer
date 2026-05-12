package com.mallorca.explorer.core.domain.usecase.favorite

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.FavoriteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetFavorites @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<List<Place>> =
        favoriteRepository.getFavoritePlaces().flowOn(dispatcher)
}
