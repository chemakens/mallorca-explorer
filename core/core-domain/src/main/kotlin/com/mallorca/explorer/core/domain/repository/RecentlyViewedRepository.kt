package com.mallorca.explorer.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface RecentlyViewedRepository {
    fun getRecentIds(): Flow<List<String>>
    suspend fun recordView(placeId: String)
}
