package com.mallorca.explorer.feature.map

import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Place
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class MapUiState(
    val places: ImmutableList<Place> = persistentListOf(),
    val selectedPlace: Place? = null,
    val activeCategory: Category? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
