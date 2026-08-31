package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.data.database.dao.EventDao
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import com.mallorca.explorer.core.data.firebase.EventRemoteDataSource
import com.mallorca.explorer.core.data.sync.NetworkMonitor
import com.mallorca.explorer.core.domain.model.Event
import com.mallorca.explorer.core.domain.model.EventCategory
import com.mallorca.explorer.core.domain.repository.EventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

private const val EVENTS_SYNC_TTL_MS = 60 * 60 * 1000L // 60 min

class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val eventRemoteDataSource: EventRemoteDataSource,
    private val networkMonitor: NetworkMonitor,
    private val prefsDataStore: UserPreferencesDataStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : EventRepository {

    override fun getUpcomingEvents(): Flow<List<Event>> = flow {
        trySyncFromFirestore()
        val eventsFlow = eventDao.getAll().map { entities ->
            val now = System.currentTimeMillis()
            entities.filter { e ->
                (e.endDateEpoch ?: e.startDateEpoch) + 86_400_000L >= now || e.isRecurring
            }.map { e ->
                Event(
                    id = e.id,
                    title = e.title,
                    titleEs = e.titleEs,
                    titleDe = e.titleDe,
                    titleRu = e.titleRu,
                    titleZh = e.titleZh,
                    description = e.description,
                    descriptionEs = e.descriptionEs,
                    descriptionDe = e.descriptionDe,
                    descriptionRu = e.descriptionRu,
                    descriptionZh = e.descriptionZh,
                    category = runCatching { EventCategory.valueOf(e.category) }.getOrDefault(EventCategory.CULTURE),
                    startDateEpoch = e.startDateEpoch,
                    endDateEpoch = e.endDateEpoch,
                    municipality = e.municipality,
                    address = e.address,
                    isFree = e.isFree,
                    price = e.price,
                    imageUrl = e.imageUrl,
                    isRecurring = e.isRecurring,
                    recurringDayOfWeek = e.recurringDayOfWeek,
                    websiteUrl = e.websiteUrl,
                )
            }
        }
        emitAll(eventsFlow)
    }.flowOn(ioDispatcher)

    private suspend fun trySyncFromFirestore() {
        val now = System.currentTimeMillis()
        val lastSynced = prefsDataStore.eventsLastSyncedEpoch.first()
        if (now - lastSynced < EVENTS_SYNC_TTL_MS) return
        if (!networkMonitor.isOnline.first()) return

        try {
            val remoteEvents = eventRemoteDataSource.fetchAll()
            if (remoteEvents.isNotEmpty()) {
                eventDao.upsertAll(remoteEvents)
                prefsDataStore.setEventsLastSyncedEpoch(now)
            } else {
                Timber.w("Firestore events sync returned an empty list — skipping replace to avoid wiping local data")
            }
        } catch (e: Exception) {
            Timber.w(e, "Firestore event sync failed — keeping cached local data")
        }
    }
}
