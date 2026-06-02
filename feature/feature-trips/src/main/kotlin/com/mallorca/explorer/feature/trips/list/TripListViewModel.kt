package com.mallorca.explorer.feature.trips.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import com.mallorca.explorer.core.domain.usecase.trip.CreateUserTrip
import com.mallorca.explorer.core.domain.usecase.trip.GetUserTrips
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripListUiState(
    val trips: ImmutableList<UserTrip> = persistentListOf(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    getUserTrips: GetUserTrips,
    private val createUserTrip: CreateUserTrip,
    private val userTripRepository: UserTripRepository,
) : ViewModel() {

    val uiState: StateFlow<TripListUiState> = getUserTrips()
        .map { TripListUiState(trips = it.toImmutableList(), isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TripListUiState(),
        )

    fun onCreateTrip(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = createUserTrip(name)
            onCreated(id)
        }
    }

    fun deleteTrip(id: String) {
        viewModelScope.launch { userTripRepository.deleteTrip(id) }
    }

    fun renameTrip(id: String, newName: String) {
        viewModelScope.launch {
            val trip = uiState.value.trips.find { it.id == id } ?: return@launch
            userTripRepository.updateTrip(trip.copy(name = newName))
        }
    }
}
