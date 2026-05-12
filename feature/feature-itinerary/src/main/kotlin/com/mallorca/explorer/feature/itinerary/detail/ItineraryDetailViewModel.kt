package com.mallorca.explorer.feature.itinerary.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.usecase.itinerary.GetItineraryById
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ItineraryDetailUiState(
    val itinerary: Itinerary? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ItineraryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getItineraryById: GetItineraryById,
) : ViewModel() {

    private val itineraryId: String = checkNotNull(savedStateHandle["itineraryId"])

    val uiState: StateFlow<ItineraryDetailUiState> = getItineraryById(itineraryId)
        .map { ItineraryDetailUiState(itinerary = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ItineraryDetailUiState(),
        )
}
