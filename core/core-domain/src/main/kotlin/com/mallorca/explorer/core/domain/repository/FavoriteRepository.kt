package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun getFavoritePlaces(): Flow<List<Place>>
    fun isFavorite(placeId: String): Flow<Boolean>
    suspend fun addFavorite(placeId: String)
    suspend fun removeFavorite(placeId: String)
    suspend fun toggleFavorite(placeId: String)
}
