package com.mallorca.explorer.core.domain.usecase.event

import com.mallorca.explorer.core.domain.model.Event
import com.mallorca.explorer.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUpcomingEvents @Inject constructor(private val repo: EventRepository) {
    operator fun invoke(): Flow<List<Event>> = repo.getUpcomingEvents()
}
