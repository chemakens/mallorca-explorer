package com.mallorca.explorer.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface HiddenGemRepository {
    fun getUnlockedGemIds(): Flow<Set<String>>
    suspend fun unlockGem(placeId: String)
}
