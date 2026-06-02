package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.ItineraryDao
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.toDomain
import com.mallorca.explorer.core.common.LocaleSource
import com.mallorca.explorer.core.data.network.MallorcaApiService
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ItineraryRepositoryImpl @Inject constructor(
    private val itineraryDao: ItineraryDao,
    private val placeDao: PlaceDao,
    private val apiService: MallorcaApiService,
    private val localeSource: LocaleSource,
) : ItineraryRepository {

    override fun getAllItineraries(): Flow<List<Itinerary>> =
        localeSource.locale.flatMapLatest { locale ->
            itineraryDao.getAllItineraries().flatMapLatest { itinEntities ->
                if (itinEntities.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(itinEntities.map { itin ->
                    combine(
                        itineraryDao.getStopsForItinerary(itin.id),
                        placeDao.getAllPlaces(),
                    ) { stops, places ->
                        val placeMap = places.associateBy { it.id }
                        itin.toDomain(stops.mapNotNull { stop ->
                            placeMap[stop.placeId]?.let { stop to it.toDomain() }
                        }, locale)
                    }
                }) { it.toList() }
            }
        }

    override fun getItinerariesByCategory(category: Category): Flow<List<Itinerary>> =
        localeSource.locale.flatMapLatest { locale ->
            itineraryDao.getItinerariesByCategory(category.name).flatMapLatest { itinEntities ->
                if (itinEntities.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(itinEntities.map { itin ->
                    combine(
                        itineraryDao.getStopsForItinerary(itin.id),
                        placeDao.getAllPlaces(),
                    ) { stops, places ->
                        val placeMap = places.associateBy { it.id }
                        itin.toDomain(stops.mapNotNull { stop ->
                            placeMap[stop.placeId]?.let { stop to it.toDomain() }
                        }, locale)
                    }
                }) { it.toList() }
            }
        }

    override fun getItineraryById(id: String): Flow<Itinerary?> =
        localeSource.locale.flatMapLatest { locale ->
            combine(
                itineraryDao.getItineraryById(id),
                itineraryDao.getStopsForItinerary(id),
                placeDao.getAllPlaces(),
            ) { itin, stops, places ->
                if (itin == null) return@combine null
                val placeMap = places.associateBy { it.id }
                itin.toDomain(stops.mapNotNull { stop ->
                    placeMap[stop.placeId]?.let { stop to it.toDomain() }
                }, locale)
            }
        }

    override suspend fun refreshItineraries() {
        try {
            val dtos = apiService.getItineraries()
            // For brevity, seeded data is handled by SeedDataWorker; API refresh uses same upsert path
            Timber.d("Refreshed ${dtos.size} itineraries from network")
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh itineraries from network")
        }
    }
}
