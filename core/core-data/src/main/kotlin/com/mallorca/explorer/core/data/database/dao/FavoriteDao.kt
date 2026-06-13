package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAtEpoch DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE placeId = :placeId")
    fun isFavorite(placeId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE placeId = :placeId")
    suspend fun isFavoriteNow(placeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE placeId = :placeId")
    suspend fun delete(placeId: String)

    @Query("SELECT placeId FROM favorites")
    fun getAllFavoritePlaceIds(): Flow<List<String>>
}
