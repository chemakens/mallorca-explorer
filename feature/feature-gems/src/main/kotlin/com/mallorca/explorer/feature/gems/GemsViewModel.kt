package com.mallorca.explorer.feature.gems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.usecase.gem.GetUnlockedGems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GemItem(
    val place: Place,
    val isUnlocked: Boolean,
)

data class GemsUiState(
    val gems: ImmutableList<GemItem> = persistentListOf(),
    val isLoading: Boolean = true,
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
)

@HiltViewModel
class GemsViewModel @Inject constructor(
    placeRepository: PlaceRepository,
    getUnlockedGems: GetUnlockedGems,
) : ViewModel() {

    val uiState: StateFlow<GemsUiState> = combine(
        placeRepository.getAllPlaces(),
        getUnlockedGems(),
    ) { places, unlockedIds ->
        val gemPlaces = places.filter { it.subCategories.contains("hidden_gem") }
        val items = gemPlaces.map { GemItem(it, it.id in unlockedIds) }
        GemsUiState(
            gems = items.toImmutableList(),
            isLoading = false,
            unlockedCount = items.count { it.isUnlocked },
            totalCount = items.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GemsUiState(),
    )
}
