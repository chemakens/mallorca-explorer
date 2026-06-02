package com.mallorca.explorer.feature.trips.selector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.model.UserTripStop
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import com.mallorca.explorer.core.domain.usecase.place.GetPlaceDetail
import com.mallorca.explorer.core.domain.usecase.trip.CreateUserTrip
import com.mallorca.explorer.core.domain.usecase.trip.GetUserTrips
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripSelectorUiState(
    val trips: ImmutableList<UserTrip> = persistentListOf(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TripSelectorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getUserTrips: GetUserTrips,
    private val createUserTrip: CreateUserTrip,
    private val userTripRepository: UserTripRepository,
    private val getPlaceDetail: GetPlaceDetail,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle["placeId"])

    val uiState: StateFlow<TripSelectorUiState> = getUserTrips()
        .map { TripSelectorUiState(trips = it.toImmutableList(), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripSelectorUiState())

    fun addToTrip(tripId: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val place = getPlaceDetail(placeId).first()
            val currentStops = userTripRepository.getTripById(tripId).first()?.stops ?: emptyList()
            userTripRepository.addStop(tripId, UserTripStop(place = place, order = currentStops.size))
            onDone(tripId)
        }
    }

    fun createTripAndAdd(name: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val newTripId = createUserTrip(name)
            val place = getPlaceDetail(placeId).first()
            userTripRepository.addStop(newTripId, UserTripStop(place = place, order = 0))
            onDone(newTripId)
        }
    }
}
