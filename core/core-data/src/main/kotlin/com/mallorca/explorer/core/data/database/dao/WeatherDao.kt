package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.WeatherCacheEntity

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache WHERE fetchedEpoch < :cutoffEpoch")
    suspend fun deleteOlderThan(cutoffEpoch: Long)
}
