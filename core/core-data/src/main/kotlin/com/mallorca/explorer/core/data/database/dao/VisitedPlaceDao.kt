package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.VisitedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPlaceDao {
    @Query("SELECT placeId FROM visited_places ORDER BY visitedAtEpoch DESC")
    fun getAllVisitedIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) > 0 FROM visited_places WHERE placeId = :placeId")
    fun isVisited(placeId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markVisited(entity: VisitedPlaceEntity)

    @Query("DELETE FROM visited_places WHERE placeId = :placeId")
    suspend fun unmarkVisited(placeId: String)
}
