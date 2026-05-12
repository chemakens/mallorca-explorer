package com.mallorca.explorer.core.domain.usecase.trip

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CreateUserTrip @Inject constructor(
    private val userTripRepository: UserTripRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(name: String): String =
        withContext(dispatcher) { userTripRepository.createTrip(name) }
}
