package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.StopProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StopProgressDao {
    @Query("SELECT placeId FROM stop_progress WHERE itineraryId = :itineraryId")
    fun getVisitedPlaceIds(itineraryId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markVisited(entity: StopProgressEntity)

    @Query("DELETE FROM stop_progress WHERE itineraryId = :itineraryId AND placeId = :placeId")
    suspend fun markUnvisited(itineraryId: String, placeId: String)

    @Query("SELECT COUNT(*) FROM stop_progress WHERE itineraryId = :itineraryId AND placeId = :placeId")
    suspend fun isVisited(itineraryId: String, placeId: String): Int
}
