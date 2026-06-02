package com.mallorca.explorer.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface StopProgressRepository {
    fun getVisitedStops(itineraryId: String): Flow<Set<String>>
    suspend fun toggleStop(itineraryId: String, placeId: String)
}
