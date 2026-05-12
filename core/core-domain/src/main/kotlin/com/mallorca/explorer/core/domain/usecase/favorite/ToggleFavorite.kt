package com.mallorca.explorer.core.domain.usecase.favorite

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.repository.FavoriteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ToggleFavorite @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(placeId: String) =
        withContext(dispatcher) { favoriteRepository.toggleFavorite(placeId) }
}
