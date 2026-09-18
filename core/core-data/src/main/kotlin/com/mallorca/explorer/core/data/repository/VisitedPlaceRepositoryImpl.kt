package com.mallorca.explorer.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.mallorca.explorer.core.data.auth.UserCloudDataSource
import com.mallorca.explorer.core.data.database.dao.VisitedPlaceDao
import com.mallorca.explorer.core.data.database.entity.VisitedPlaceEntity
import com.mallorca.explorer.core.domain.repository.VisitedPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitedPlaceRepositoryImpl @Inject constructor(
    private val visitedPlaceDao: VisitedPlaceDao,
    private val cloud: UserCloudDataSource,
    private val firebaseAuth: FirebaseAuth,
) : VisitedPlaceRepository {

    private val uid get() = firebaseAuth.currentUser?.uid

    override fun getAllVisitedIds(): Flow<List<String>> = visitedPlaceDao.getAllVisitedIds()

    override fun isVisited(placeId: String): Flow<Boolean> = visitedPlaceDao.isVisited(placeId)

    override suspend fun markVisited(placeId: String) {
        visitedPlaceDao.markVisited(VisitedPlaceEntity(placeId, System.currentTimeMillis()))
        uid?.let { cloud.addVisited(it, placeId) }
    }

    override suspend fun unmarkVisited(placeId: String) {
        visitedPlaceDao.unmarkVisited(placeId)
        uid?.let { cloud.removeVisited(it, placeId) }
    }

    override suspend fun toggleVisited(placeId: String) {
        val isVisited = visitedPlaceDao.isVisited(placeId).first()
        if (isVisited) unmarkVisited(placeId) else markVisited(placeId)
    }
}
