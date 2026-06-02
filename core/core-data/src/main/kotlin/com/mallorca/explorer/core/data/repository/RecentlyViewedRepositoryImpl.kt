package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.RecentlyViewedDao
import com.mallorca.explorer.core.data.database.entity.RecentlyViewedEntity
import com.mallorca.explorer.core.domain.repository.RecentlyViewedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyViewedRepositoryImpl @Inject constructor(
    private val recentlyViewedDao: RecentlyViewedDao,
) : RecentlyViewedRepository {

    override fun getRecentIds(): Flow<List<String>> = recentlyViewedDao.getRecentIds()

    override suspend fun recordView(placeId: String) {
        recentlyViewedDao.recordView(RecentlyViewedEntity(placeId, System.currentTimeMillis()))
        recentlyViewedDao.pruneOld()
    }
}
