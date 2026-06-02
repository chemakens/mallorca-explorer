package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.StopProgressDao
import com.mallorca.explorer.core.data.database.entity.StopProgressEntity
import com.mallorca.explorer.core.domain.repository.StopProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StopProgressRepositoryImpl @Inject constructor(
    private val stopProgressDao: StopProgressDao,
) : StopProgressRepository {

    override fun getVisitedStops(itineraryId: String): Flow<Set<String>> =
        stopProgressDao.getVisitedPlaceIds(itineraryId).map { it.toSet() }

    override suspend fun toggleStop(itineraryId: String, placeId: String) {
        if (stopProgressDao.isVisited(itineraryId, placeId) > 0) {
            stopProgressDao.markUnvisited(itineraryId, placeId)
        } else {
            stopProgressDao.markVisited(
                StopProgressEntity(itineraryId, placeId, System.currentTimeMillis())
            )
        }
    }
}
