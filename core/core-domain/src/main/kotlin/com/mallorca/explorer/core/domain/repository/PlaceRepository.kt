package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun getAllPlaces(): Flow<List<Place>>
    fun getPlacesByCategory(category: Category): Flow<List<Place>>
    fun getPlaceById(id: String): Flow<Place?>
    fun searchPlaces(query: String): Flow<List<Place>>
    fun getNearbyPlaces(center: LatLng, radiusKm: Double): Flow<List<Place>>
    suspend fun refreshPlaces()
}