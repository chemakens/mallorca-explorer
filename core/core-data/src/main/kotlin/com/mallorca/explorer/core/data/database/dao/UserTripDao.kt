package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserTripDao {
    @Query("SELECT * FROM user_trips ORDER BY updatedAtEpoch DESC")
    fun getAllTrips(): Flow<List<UserTripEntity>>

    @Query("SELECT * FROM user_trips WHERE id = :id")
    fun getTripById(id: String): Flow<UserTripEntity?>

    @Query("SELECT * FROM user_trip_stops WHERE tripId = :tripId ORDER BY `order` ASC")
    fun getStopsForTrip(tripId: String): Flow<List<UserTripStopEntity>>

    @Upsert
    suspend fun upsertTrip(trip: UserTripEntity)

    @Upsert
    suspend fun upsertStop(stop: UserTripStopEntity)

    @Upsert
    suspend fun upsertStops(stops: List<UserTripStopEntity>)

    @Query("DELETE FROM user_trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: String)

    @Query("DELETE FROM user_trip_stops WHERE tripId = :tripId AND placeId = :placeId")
    suspend fun deleteStop(tripId: String, placeId: String)

    @Query("DELETE FROM user_trip_stops WHERE tripId = :tripId")
    suspend fun deleteAllStopsForTrip(tripId: String)

    @Transaction
    suspend fun reorderStops(tripId: String, stops: List<UserTripStopEntity>) {
        deleteAllStopsForTrip(tripId)
        upsertStops(stops)
    }
}
