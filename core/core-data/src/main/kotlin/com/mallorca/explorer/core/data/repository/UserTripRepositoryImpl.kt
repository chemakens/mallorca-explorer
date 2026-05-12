package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.dao.UserTripDao
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity
import com.mallorca.explorer.core.data.database.toDomain
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.model.UserTripStop
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserTripRepositoryImpl @Inject constructor(
    private val userTripDao: UserTripDao,
    private val placeDao: PlaceDao,
) : UserTripRepository {

    override fun getUserTrips(): Flow<List<UserTrip>> =
        userTripDao.getAllTrips().flatMapLatest { tripEntities ->
            if (tripEntities.isEmpty()) return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList())
            combine(tripEntities.map { trip ->
                combine(
                    userTripDao.getStopsForTrip(trip.id),
                    placeDao.getAllPlaces(),
                ) { stops, places ->
                    val placeMap = places.associateBy { it.id }
                    trip.toDomain(
                        stops.mapNotNull { stop ->
                            placeMap[stop.placeId]?.let { stop to it.toDomain() }
                        }
                    )
                }
            }) { it.toList() }
        }

    override fun getTripById(id: String): Flow<UserTrip?> =
        combine(
            userTripDao.getTripById(id),
            userTripDao.getStopsForTrip(id),
            placeDao.getAllPlaces(),
        ) { trip, stops, places ->
            if (trip == null) return@combine null
            val placeMap = places.associateBy { it.id }
            trip.toDomain(stops.mapNotNull { stop ->
                placeMap[stop.placeId]?.let { stop to it.toDomain() }
            })
        }

    override suspend fun createTrip(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()
        userTripDao.upsertTrip(UserTripEntity(id, name, now, now, null, false))
        return id
    }

    override suspend fun updateTrip(trip: UserTrip) {
        userTripDao.upsertTrip(
            UserTripEntity(
                id = trip.id,
                name = trip.name,
                createdAtEpoch = trip.createdAt.toEpochMilli(),
                updatedAtEpoch = Instant.now().toEpochMilli(),
                notes = trip.notes,
                isSynced = false,
            )
        )
    }

    override suspend fun deleteTrip(id: String) = userTripDao.deleteTrip(id)

    override suspend fun addStop(tripId: String, stop: UserTripStop) {
        userTripDao.upsertStop(
            UserTripStopEntity(
                tripId = tripId,
                placeId = stop.place.id,
                order = stop.order,
                userNote = stop.userNote,
                suggestedDurationMinutes = stop.suggestedDurationMinutes,
                isVisited = false,
                visitedAtEpoch = null,
            )
        )
    }

    override suspend fun removeStop(tripId: String, placeId: String) =
        userTripDao.deleteStop(tripId, placeId)

    override suspend fun reorderStops(tripId: String, orderedPlaceIds: List<String>) {
        val existing = mutableListOf<UserTripStopEntity>()
        userTripDao.getStopsForTrip(tripId).collect { existing.addAll(it); return@collect }
        val reordered = orderedPlaceIds.mapIndexed { idx, placeId ->
            existing.first { it.placeId == placeId }.copy(order = idx)
        }
        userTripDao.reorderStops(tripId, reordered)
    }

    override suspend fun markStopVisited(tripId: String, placeId: String) {
        var stop: UserTripStopEntity? = null
        userTripDao.getStopsForTrip(tripId).collect { stops ->
            stop = stops.find { it.placeId == placeId }
            return@collect
        }
        stop?.let {
            userTripDao.upsertStop(
                it.copy(isVisited = true, visitedAtEpoch = Instant.now().toEpochMilli())
            )
        }
    }
}
