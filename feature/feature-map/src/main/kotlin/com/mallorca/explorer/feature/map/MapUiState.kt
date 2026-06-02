package com.mallorca.explorer.feature.map

import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ItineraryRoute(
    val id: String,
    val title: String,
    val color: String,
    val coords: ImmutableList<LatLng>,
    val coverPhotoUrl: String,
    val category: Category,
    val durationDays: Int,
)

data class MapUiState(
    val places: ImmutableList<Place> = persistentListOf(),
    val selectedPlace: Place? = null,
    val activeCategory: Category? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeItinerary: Itinerary? = null,
    val allItineraryRoutes: ImmutableList<ItineraryRoute> = persistentListOf(),
    val previewItineraryId: String? = null,
    val locale: String = "en",
)
