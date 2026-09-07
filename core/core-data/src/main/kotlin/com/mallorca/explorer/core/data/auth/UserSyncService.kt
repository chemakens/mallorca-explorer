package com.mallorca.explorer.core.data.auth

import com.mallorca.explorer.core.data.database.dao.FavoriteDao
import com.mallorca.explorer.core.data.database.dao.HiddenGemDao
import com.mallorca.explorer.core.data.database.dao.VisitedPlaceDao
import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.entity.UnlockedGemEntity
import com.mallorca.explorer.core.data.database.entity.VisitedPlaceEntity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincroniza datos de usuario entre Room y Firestore al hacer login.
 * Estrategia merge: une datos locales + nube (nunca borra datos locales).
 */
@Singleton
class UserSyncService @Inject constructor(
    private val cloud: UserCloudDataSource,
    private val favoriteDao: FavoriteDao,
    private val hiddenGemDao: HiddenGemDao,
    private val visitedPlaceDao: VisitedPlaceDao,
) {
    suspend fun syncFromCloud(uid: String) {
        Timber.d("UserSyncService: sincronizando desde nube uid=$uid")
        try {
            val now = System.currentTimeMillis()
            val localFavIds = favoriteDao.getAllFavoritePlaceIds().first().toSet()
            val cloudFavIds = cloud.getFavoriteIds(uid)
            cloudFavIds.filter { it !in localFavIds }.forEach {
                favoriteDao.insert(FavoriteEntity(it, now))
            }

            val localGemIds = hiddenGemDao.getUnlockedIds().first().toSet()
            val cloudGemIds = cloud.getGemIds(uid)
            cloudGemIds.filter { it !in localGemIds }.forEach {
                hiddenGemDao.unlock(UnlockedGemEntity(it, now))
            }

            val localVisitedIds = visitedPlaceDao.getAllVisitedIds().first().toSet()
            val cloudVisitedIds = cloud.getVisitedIds(uid)
            cloudVisitedIds.filter { it !in localVisitedIds }.forEach {
                visitedPlaceDao.markVisited(VisitedPlaceEntity(it, now))
            }

            Timber.d("UserSyncService: sync completada")
        } catch (e: Exception) {
            Timber.e(e, "UserSyncService: sync falló")
        }
    }
}
