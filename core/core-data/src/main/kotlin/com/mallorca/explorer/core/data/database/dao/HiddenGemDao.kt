package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.UnlockedGemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenGemDao {
    @Query("SELECT placeId FROM unlocked_gems")
    fun getUnlockedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun unlock(entity: UnlockedGemEntity)
}
