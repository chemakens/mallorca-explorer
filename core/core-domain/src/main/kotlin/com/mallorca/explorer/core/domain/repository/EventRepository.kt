package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.Event
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun getUpcomingEvents(): Flow<List<Event>>
}
