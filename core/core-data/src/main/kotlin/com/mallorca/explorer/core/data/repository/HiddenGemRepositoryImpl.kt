package com.mallorca.explorer.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.mallorca.explorer.core.data.auth.UserCloudDataSource
import com.mallorca.explorer.core.data.database.dao.HiddenGemDao
import com.mallorca.explorer.core.data.database.entity.UnlockedGemEntity
import com.mallorca.explorer.core.domain.repository.HiddenGemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HiddenGemRepositoryImpl @Inject constructor(
    private val hiddenGemDao: HiddenGemDao,
    private val cloud: UserCloudDataSource,
    private val firebaseAuth: FirebaseAuth,
) : HiddenGemRepository {

    private val uid get() = firebaseAuth.currentUser?.uid

    override fun getUnlockedGemIds(): Flow<Set<String>> =
        hiddenGemDao.getUnlockedIds().map { it.toSet() }

    override suspend fun unlockGem(placeId: String) {
        hiddenGemDao.unlock(UnlockedGemEntity(placeId, System.currentTimeMillis()))
        uid?.let { cloud.addGem(it, placeId) }
    }
}
