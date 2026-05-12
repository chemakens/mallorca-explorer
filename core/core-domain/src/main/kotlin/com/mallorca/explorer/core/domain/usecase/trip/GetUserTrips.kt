package com.mallorca.explorer.core.domain.usecase.trip

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetUserTrips @Inject constructor(
    private val userTripRepository: UserTripRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<List<UserTrip>> =
        userTripRepository.getUserTrips().flowOn(dispatcher)
}
