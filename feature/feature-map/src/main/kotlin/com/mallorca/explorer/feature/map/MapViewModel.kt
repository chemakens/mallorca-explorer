package com.mallorca.explorer.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.data.sync.NetworkMonitor
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.usecase.favorite.ToggleFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val toggleFavorite: ToggleFavorite,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val activeCategory = MutableStateFlow<Category?>(null)
    private val selectedPlaceId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val places = activeCategory.flatMapLatest { cat ->
        if (cat == null) placeRepository.getAllPlaces()
        else placeRepository.getPlacesByCategory(cat)
    }

    val uiState: StateFlow<MapUiState> = combine(
        places,
        activeCategory,
        selectedPlaceId,
        networkMonitor.isOnline,
    ) { placeList, cat, selectedId, isOnline ->
        MapUiState(
            places = placeList.toImmutableList(),
            selectedPlace = selectedId?.let { id -> placeList.find { it.id == id } },
            activeCategory = cat,
            isOffline = !isOnline,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState(isLoading = true),
    )

    fun onCategorySelected(category: Category?) {
        activeCategory.value = if (activeCategory.value == category) null else category
        selectedPlaceId.value = null
    }

    fun onMarkerTapped(placeId: String) {
        selectedPlaceId.value = if (selectedPlaceId.value == placeId) null else placeId
    }

    fun onBottomSheetDismissed() { selectedPlaceId.value = null }

    fun onFavoriteToggled(placeId: String) {
        viewModelScope.launch { toggleFavorite(placeId) }
    }
}
