package com.mallorca.explorer.core.data.firebase

import com.mallorca.explorer.core.data.database.entity.EventEntity

interface EventRemoteDataSource {
    /**
     * Throws if the whole fetch fails (network, permissions, timeout).
     * Individual malformed documents are skipped internally and never surface as an exception.
     */
    suspend fun fetchAll(): List<EventEntity>
}
