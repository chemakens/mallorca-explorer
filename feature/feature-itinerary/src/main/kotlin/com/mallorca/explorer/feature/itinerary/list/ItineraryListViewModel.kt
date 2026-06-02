package com.mallorca.explorer.feature.itinerary.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import com.mallorca.explorer.core.domain.usecase.place.GetPlacesByCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ItineraryListUiState(
    val itineraries: ImmutableList<Itinerary> = persistentListOf(),
    val places: ImmutableList<Place> = persistentListOf(),
    val category: Category? = null,
)

@HiltViewModel
class ItineraryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    itineraryRepository: ItineraryRepository,
    getPlacesByCategory: GetPlacesByCategory,
) : ViewModel() {

    private val categoryName: String? = savedStateHandle["category"]
    private val category: Category? = categoryName?.let {
        runCatching { Category.valueOf(it) }.getOrNull()
    }

    private val itinerariesFlow = if (category != null)
        itineraryRepository.getItinerariesByCategory(category)
    else
        itineraryRepository.getAllItineraries()

    private val placesFlow = if (category != null)
        getPlacesByCategory(category)
    else
        flowOf(emptyList())

    val uiState: StateFlow<ItineraryListUiState> = combine(
        itinerariesFlow, placesFlow
    ) { itineraries, places ->
        ItineraryListUiState(
            itineraries = itineraries.toImmutableList(),
            places = places.toImmutableList(),
            category = category,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItineraryListUiState(category = category),
    )

}
