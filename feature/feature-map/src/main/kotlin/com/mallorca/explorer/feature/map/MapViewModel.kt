package com.mallorca.explorer.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.common.LocaleSource
import com.mallorca.explorer.core.data.sync.NetworkMonitor
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.usecase.favorite.ToggleFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun Category.routeColor(): String = when (this) {
    Category.BEACH -> "#0277BD"
    Category.HIKING -> "#2E7D32"
    Category.CULTURE -> "#6A1B9A"
    Category.GASTRONOMY -> "#E65100"
    Category.TOWN -> "#37474F"
    Category.VIEWPOINT -> "#C62828"
    Category.ADVENTURE -> "#F9A825"
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val itineraryRepository: ItineraryRepository,
    private val toggleFavorite: ToggleFavorite,
    private val localeSource: LocaleSource,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val activeCategory = MutableStateFlow<Category?>(null)
    private val selectedPlaceId = MutableStateFlow<String?>(null)
    private val selectedGemId = MutableStateFlow<String?>(null)
    private val activeItineraryId = MutableStateFlow<String?>(null)
    private val previewItineraryId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val places = activeCategory.flatMapLatest { cat ->
        if (cat == null) placeRepository.getAllPlaces()
        else placeRepository.getPlacesByCategory(cat)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeItinerary = activeItineraryId.flatMapLatest { id ->
        if (id == null) flowOf(null) else itineraryRepository.getItineraryById(id)
    }

    private val allItineraries = itineraryRepository.getAllItineraries()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<ImmutableList<Place>> = _searchQuery
        .flatMapLatest { query ->
            if (query.length < 2) flowOf(persistentListOf())
            else placeRepository.searchPlaces(query).map { places ->
                places.filter { "hidden_gem" !in it.subCategories }.toImmutableList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    val uiState: StateFlow<MapUiState> = combine(
        combine(
            combine(places, activeCategory, selectedPlaceId) { p, c, s -> Triple(p, c, s) },
            networkMonitor.isOnline,
            combine(activeItinerary, allItineraries, previewItineraryId) { itin, all, previewId ->
                Triple(itin, all, previewId)
            },
        ) { (placeList, cat, selectedId), isOnline, (itinerary, allItin, previewId) ->
            val normalPlaces = placeList.filter { "hidden_gem" !in it.subCategories }
            val hiddenGems = placeList.filter { "hidden_gem" in it.subCategories }
            // When a category is active, the sheet shows ALL places in that category
            // (including hidden gems) so gems like Caló de Moro appear under Beach.
            // When no category is active, the sheet is not shown so normalPlaces suffices.
            val categorySheetPlaces = if (cat != null) placeList else normalPlaces

            val allItineraryRoutes = allItin.map { itin ->
                ItineraryRoute(
                    id = itin.id,
                    title = itin.title,
                    color = itin.category.routeColor(),
                    coords = itin.places
                        .sortedWith(compareBy({ it.dayNumber }, { it.order }))
                        .map { it.place.location }
                        .toImmutableList(),
                    coverPhotoUrl = itin.coverPhoto.url,
                    category = itin.category,
                    durationDays = itin.durationDays,
                )
            }.toImmutableList()

            MapUiState(
                places = normalPlaces.toImmutableList(),
                categorySheetPlaces = categorySheetPlaces.toImmutableList(),
                hiddenGems = hiddenGems.toImmutableList(),
                selectedPlace = selectedId?.let { id -> placeList.find { it.id == id } },
                activeCategory = cat,
                isOffline = !isOnline,
                activeItinerary = itinerary,
                allItineraryRoutes = allItineraryRoutes,
                previewItineraryId = previewId,
            )
        },
        selectedGemId,
        localeSource.locale,
    ) { state, gemId, locale ->
        state.copy(
            selectedGem = gemId?.let { id -> state.hiddenGems.find { it.id == id } },
            locale = locale,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MapUiState(isLoading = true),
        )

    fun setLocale(locale: String) = localeSource.setLocale(locale)
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }
    fun setActiveItinerary(itineraryId: String?) { activeItineraryId.value = itineraryId }
    fun onItineraryMarkerTapped(id: String) { previewItineraryId.value = id }
    fun onPreviewDismissed() { previewItineraryId.value = null }

    fun onCategorySelected(category: Category?) {
        activeCategory.value = if (activeCategory.value == category) null else category
        selectedPlaceId.value = null
    }

    fun onMarkerTapped(placeId: String) {
        selectedGemId.value = null
        selectedPlaceId.value = if (selectedPlaceId.value == placeId) null else placeId
    }

    fun onGemMarkerTapped(placeId: String) {
        selectedPlaceId.value = null
        selectedGemId.value = if (selectedGemId.value == placeId) null else placeId
    }

    fun onGemDismissed() { selectedGemId.value = null }

    fun onBottomSheetDismissed() {
        selectedPlaceId.value = null
        selectedGemId.value = null
    }

    fun onFavoriteToggled(placeId: String) {
        viewModelScope.launch { toggleFavorite(placeId) }
    }
}
