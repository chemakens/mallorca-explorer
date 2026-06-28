package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itineraries ORDER BY title ASC")
    fun getAllItineraries(): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM itineraries WHERE category = :category")
    fun getItinerariesByCategory(category: String): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM itineraries WHERE id = :id")
    fun getItineraryById(id: String): Flow<ItineraryEntity?>

    @Query("SELECT * FROM itinerary_stops WHERE itineraryId = :itineraryId ORDER BY `order` ASC")
    fun getStopsForItinerary(itineraryId: String): Flow<List<ItineraryStopEntity>>

    @Upsert
    suspend fun upsertAll(itineraries: List<ItineraryEntity>)

    @Upsert
    suspend fun upsertStops(stops: List<ItineraryStopEntity>)

    @Transaction
    suspend fun upsertItineraryWithStops(itinerary: ItineraryEntity, stops: List<ItineraryStopEntity>) {
        upsertAll(listOf(itinerary))
        upsertStops(stops)
    }

    @Query("DELETE FROM itinerary_stops WHERE placeId IN (:placeIds)")
    suspend fun deleteStopsByPlaceIds(placeIds: List<String>)

    @Query("DELETE FROM itinerary_stops WHERE itineraryId IN (:itineraryIds)")
    suspend fun deleteStopsByItineraryIds(itineraryIds: List<String>)

    @Query("DELETE FROM itineraries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM itineraries")
    suspend fun count(): Int
}
