package com.mallorca.explorer.feature.trips.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.UserTrip
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
}
