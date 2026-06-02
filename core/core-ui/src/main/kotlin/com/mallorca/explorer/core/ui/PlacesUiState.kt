package com.mallorca.explorer.core.ui

import com.mallorca.explorer.core.domain.model.PlaceModel


sealed interface PlacesUiState {
    data object Loading : PlacesUiState
    data class Success(val places: List<PlaceModel>) : PlacesUiState
    data class Error(val message: String) : PlacesUiState
}