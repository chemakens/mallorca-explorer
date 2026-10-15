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

    // Los lugares y TODAS las paradas se cargan UNA sola vez y se cruzan en memoria. Antes se
    // suscribía placeDao.getAllPlaces() una vez por itinerario (N×371 lugares en RAM a la vez),
    // lo que agotaba el heap al crecer el nº de itinerarios (cierre OOM al abrir Explorar).
    override fun getAllItineraries(): Flow<List<Itinerary>> =
        localeSource.locale.flatMapLatest { locale ->
            combine(
                itineraryDao.getAllItineraries(),
                itineraryDao.getAllStops(),
                placeDao.getAllPlaces(),
            ) { itinEntities, allStops, places ->
                if (itinEntities.isEmpty()) return@combine emptyList()
                val placeMap = places.associateBy { it.id }.mapValues { it.value.toDomain() }
                val stopsByItin = allStops.groupBy { it.itineraryId }
                itinEntities.map { itin ->
                    itin.toDomain(
                        (stopsByItin[itin.id] ?: emptyList()).mapNotNull { stop ->
                            placeMap[stop.placeId]?.let { stop to it }
                        },
                        locale,
                    )
                }
            }
        }

    override fun getItinerariesByCategory(category: Category): Flow<List<Itinerary>> =
        localeSource.locale.flatMapLatest { locale ->
            combine(
                itineraryDao.getItinerariesByCategory(category.name),
                itineraryDao.getAllStops(),
                placeDao.getAllPlaces(),
            ) { itinEntities, allStops, places ->
                if (itinEntities.isEmpty()) return@combine emptyList()
                val placeMap = places.associateBy { it.id }.mapValues { it.value.toDomain() }
                val stopsByItin = allStops.groupBy { it.itineraryId }
                itinEntities.map { itin ->
                    itin.toDomain(
                        (stopsByItin[itin.id] ?: emptyList()).mapNotNull { stop ->
                            placeMap[stop.placeId]?.let { stop to it }
                        },
                        locale,
                    )
                }
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
