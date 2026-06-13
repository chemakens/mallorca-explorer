package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.FavoriteDao
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.toDomain
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val placeDao: PlaceDao,
) : FavoriteRepository {

    override fun getFavoritePlaces(): Flow<List<Place>> =
        combine(
            favoriteDao.getAllFavoritePlaceIds(),
            placeDao.getAllPlaces(),
        ) { favIds, places ->
            val favSet = favIds.toSet()
            places.filter { it.id in favSet }
                .map { it.toDomain().copy(isFavorite = true) }
        }

    override fun isFavorite(placeId: String): Flow<Boolean> =
        favoriteDao.isFavorite(placeId)

    override suspend fun addFavorite(placeId: String) {
        favoriteDao.insert(FavoriteEntity(placeId, Instant.now().toEpochMilli()))
    }

    override suspend fun removeFavorite(placeId: String) {
        favoriteDao.delete(placeId)
    }

    override suspend fun toggleFavorite(placeId: String) {
        if (favoriteDao.isFavoriteNow(placeId)) removeFavorite(placeId) else addFavorite(placeId)
    }
}
