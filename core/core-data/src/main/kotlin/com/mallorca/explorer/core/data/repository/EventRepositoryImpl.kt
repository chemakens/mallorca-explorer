package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.EventDao
import com.mallorca.explorer.core.domain.model.Event
import com.mallorca.explorer.core.domain.model.EventCategory
import com.mallorca.explorer.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
) : EventRepository {

    override fun getUpcomingEvents(): Flow<List<Event>> =
        eventDao.getAll().map { entities ->
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
                )
            }
        }
}
