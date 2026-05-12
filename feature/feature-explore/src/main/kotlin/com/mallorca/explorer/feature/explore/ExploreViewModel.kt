package com.mallorca.explorer.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.usecase.place.SearchPlaces
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
import javax.inject.Inject

data class ExploreUiState(
    val featuredItineraries: ImmutableList<Itinerary> = persistentListOf(),
    val popularPlaces: ImmutableList<Place> = persistentListOf(),
    val searchResults: ImmutableList<Place> = persistentListOf(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val itineraryRepository: ItineraryRepository,
    private val searchPlaces: SearchPlaces,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val searchResults = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else searchPlaces(query)
        }

    val uiState: StateFlow<ExploreUiState> = combine(
        itineraryRepository.getAllItineraries(),
        placeRepository.getAllPlaces(),
        searchQuery,
        searchResults,
    ) { itineraries, places, query, results ->
        ExploreUiState(
            featuredItineraries = itineraries.take(6).toImmutableList(),
            popularPlaces = places.sortedByDescending { it.rating }.take(10).toImmutableList(),
            searchResults = results.toImmutableList(),
            searchQuery = query,
            isSearching = query.isNotBlank(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExploreUiState(),
    )

    fun onSearchQueryChanged(query: String) { searchQuery.value = query }
    fun onSearchCleared() { searchQuery.value = "" }
}
