package com.mallorca.explorer.feature.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.usecase.favorite.ToggleFavorite
import com.mallorca.explorer.core.domain.usecase.place.GetPlaceDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val place: Place? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaceDetail: GetPlaceDetail,
    private val toggleFavorite: ToggleFavorite,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle["placeId"])

    val uiState: StateFlow<PlaceDetailUiState> = getPlaceDetail(placeId)
        .map { PlaceDetailUiState(place = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaceDetailUiState(),
        )

    fun onFavoriteToggled() {
        viewModelScope.launch { toggleFavorite(placeId) }
    }
}
