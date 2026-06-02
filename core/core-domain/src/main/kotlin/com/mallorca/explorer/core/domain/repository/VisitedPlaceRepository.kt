package com.mallorca.explorer.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface VisitedPlaceRepository {
    fun getAllVisitedIds(): Flow<List<String>>
    fun isVisited(placeId: String): Flow<Boolean>
    suspend fun markVisited(placeId: String)
    suspend fun unmarkVisited(placeId: String)
    suspend fun toggleVisited(placeId: String)
}
