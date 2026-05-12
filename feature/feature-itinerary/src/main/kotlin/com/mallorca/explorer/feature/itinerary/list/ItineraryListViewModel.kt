package com.mallorca.explorer.feature.itinerary.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ItineraryListUiState(
    val itineraries: ImmutableList<Itinerary> = persistentListOf(),
    val category: Category? = null,
)

@HiltViewModel
class ItineraryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val categoryName: String? = savedStateHandle["category"]
    private val category: Category? = categoryName?.let {
        runCatching { Category.valueOf(it) }.getOrNull()
    }

    val uiState: StateFlow<ItineraryListUiState> = (
        if (category != null) itineraryRepository.getItinerariesByCategory(category)
        else itineraryRepository.getAllItineraries()
    ).map { list ->
        ItineraryListUiState(
            itineraries = list.toImmutableList(),
            category = category,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItineraryListUiState(category = category),
    )
}
