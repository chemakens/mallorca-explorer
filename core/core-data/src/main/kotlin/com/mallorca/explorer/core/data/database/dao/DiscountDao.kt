package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mallorca.explorer.core.data.database.entity.DiscountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscountDao {
    @Query("SELECT * FROM discounts WHERE placeId = :placeId AND validUntilEpoch > :nowEpoch")
    fun getActiveForPlace(placeId: String, nowEpoch: Long): Flow<List<DiscountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(discounts: List<DiscountEntity>)
}
