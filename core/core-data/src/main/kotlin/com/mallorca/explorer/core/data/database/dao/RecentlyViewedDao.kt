package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyViewedDao {
    @Query("SELECT placeId FROM recently_viewed ORDER BY viewedAtEpoch DESC LIMIT 10")
    fun getRecentIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordView(entity: RecentlyViewedEntity)

    @Query("DELETE FROM recently_viewed WHERE placeId NOT IN (SELECT placeId FROM recently_viewed ORDER BY viewedAtEpoch DESC LIMIT 10)")
    suspend fun pruneOld()
}
