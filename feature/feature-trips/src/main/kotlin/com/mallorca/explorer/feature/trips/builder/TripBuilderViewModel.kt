package com.mallorca.explorer.feature.trips.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import com.mallorca.explorer.core.domain.usecase.place.SearchPlaces
import com.mallorca.explorer.core.domain.model.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripBuilderUiState(
    val trip: UserTrip? = null,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val searchResults: ImmutableList<Place> = persistentListOf(),
    val showAddSheet: Boolean = false,
)

@HiltViewModel
class TripBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userTripRepository: UserTripRepository,
    private val searchPlaces: SearchPlaces,
) : ViewModel() {

    private val tripId: String? = savedStateHandle["tripId"]
    private val searchQuery = MutableStateFlow("")
    private val showAddSheet = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    private val searchResults = searchQuery
        .debounce(300)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else searchPlaces(q) }

    val uiState: StateFlow<TripBuilderUiState> = combine(
        if (tripId != null) userTripRepository.getTripById(tripId) else flowOf(null),
        searchQuery,
        searchResults,
        showAddSheet,
    ) { trip, query, results, showSheet ->
        TripBuilderUiState(
            trip = trip,
            isLoading = false,
            searchQuery = query,
            searchResults = results.toImmutableList(),
            showAddSheet = showSheet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripBuilderUiState(),
    )

    fun onSearchQueryChanged(query: String) { searchQuery.value = query }
    fun onShowAddSheet() { showAddSheet.value = true }
    fun onDismissAddSheet() { showAddSheet.value = false; searchQuery.value = "" }

    fun renameTrip(newName: String) {
        val id = tripId ?: return
        viewModelScope.launch {
            val trip = uiState.value.trip ?: return@launch
            userTripRepository.updateTrip(trip.copy(name = newName))
        }
    }

    fun onAddPlace(place: Place) {
        val id = tripId ?: return
        viewModelScope.launch {
            val currentStops = uiState.value.trip?.stops ?: emptyList()
            userTripRepository.addStop(
                id,
                com.mallorca.explorer.core.domain.model.UserTripStop(
                    place = place,
                    order = currentStops.size,
                )
            )
            onDismissAddSheet()
        }
    }

    fun onRemovePlace(placeId: String) {
        val id = tripId ?: return
        viewModelScope.launch { userTripRepository.removeStop(id, placeId) }
    }

    fun onReorderPlaces(orderedIds: List<String>) {
        val id = tripId ?: return
        viewModelScope.launch { userTripRepository.reorderStops(id, orderedIds) }
    }
}
