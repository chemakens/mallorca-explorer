package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.model.UserTripStop
import kotlinx.coroutines.flow.Flow

interface UserTripRepository {
    fun getUserTrips(): Flow<List<UserTrip>>
    fun getTripById(id: String): Flow<UserTrip?>
    suspend fun createTrip(name: String): String
    suspend fun updateTrip(trip: UserTrip)
    suspend fun deleteTrip(id: String)
    suspend fun addStop(tripId: String, stop: UserTripStop)
    suspend fun removeStop(tripId: String, placeId: String)
    suspend fun reorderStops(tripId: String, orderedPlaceIds: List<String>)
    suspend fun markStopVisited(tripId: String, placeId: String)
}
