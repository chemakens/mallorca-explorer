package com.mallorca.explorer.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.common.LocaleSource
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.usecase.favorite.GetFavorites
import com.mallorca.explorer.core.domain.usecase.favorite.ToggleFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val places: ImmutableList<Place> = persistentListOf(),
    val isLoading: Boolean = true,
    val locale: String = "en",
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    getFavorites: GetFavorites,
    private val toggleFavorite: ToggleFavorite,
    private val localeSource: LocaleSource,
) : ViewModel() {

    val uiState: StateFlow<FavoritesUiState> = combine(
        getFavorites(),
        localeSource.locale,
    ) { places, locale ->
        FavoritesUiState(places = places.toImmutableList(), isLoading = false, locale = locale)
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FavoritesUiState(),
        )

    fun onFavoriteRemoved(placeId: String) {
        viewModelScope.launch { toggleFavorite(placeId) }
    }
}
