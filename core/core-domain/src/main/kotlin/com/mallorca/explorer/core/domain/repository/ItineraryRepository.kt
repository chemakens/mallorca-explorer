package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Itinerary
import kotlinx.coroutines.flow.Flow

interface ItineraryRepository {
    fun getAllItineraries(): Flow<List<Itinerary>>
    fun getItinerariesByCategory(category: Category): Flow<List<Itinerary>>
    fun getItineraryById(id: String): Flow<Itinerary?>
    suspend fun refreshItineraries()
}
