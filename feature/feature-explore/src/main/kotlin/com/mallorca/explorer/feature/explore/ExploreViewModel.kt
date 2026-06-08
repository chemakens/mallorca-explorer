package com.mallorca.explorer.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Event
import com.mallorca.explorer.core.domain.model.EventCategory
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.repository.RecentlyViewedRepository
import com.mallorca.explorer.core.domain.repository.VisitedPlaceRepository
import com.mallorca.explorer.core.domain.usecase.event.GetUpcomingEvents
import com.mallorca.explorer.core.domain.usecase.place.SearchPlaces
import com.mallorca.explorer.core.domain.usecase.weather.GetWeatherForLocation
import com.mallorca.explorer.core.common.LocaleSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject

private const val MALLORCA_LAT = 39.6
private const val MALLORCA_LNG = 2.9

enum class EventTimeFilter(val label: String) {
    ALL("Todos"),
    THIS_WEEK("Esta semana"),
    THIS_MONTH("Este mes"),
    NEXT_MONTH("Próximo mes"),
}

data class ExploreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalPlaceCount: Int = 0,
    val featuredItineraries: ImmutableList<Itinerary> = persistentListOf(),
    val popularPlaces: ImmutableList<Place> = persistentListOf(),
    val supPlaces: ImmutableList<Place> = persistentListOf(),
    val recentPlaces: ImmutableList<Place> = persistentListOf(),
    val searchResults: ImmutableList<Place> = persistentListOf(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val weather: WeatherCondition? = null,
    val upcomingEvents: ImmutableList<Event> = persistentListOf(),
    val minRating: Float? = null,
    val nearMeEnabled: Boolean = false,
    val userLocation: LatLng? = null,
    val eventTimeFilter: EventTimeFilter = EventTimeFilter.ALL,
    val eventCategoryFilter: EventCategory? = null,
    val locale: String = "en",
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val itineraryRepository: ItineraryRepository,
    private val searchPlaces: SearchPlaces,
    private val getWeatherForLocation: GetWeatherForLocation,
    private val getUpcomingEvents: GetUpcomingEvents,
    private val visitedPlaceRepository: VisitedPlaceRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
    private val localeSource: LocaleSource,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshTrigger = MutableStateFlow(0)
    private val _minRating = MutableStateFlow<Float?>(null)
    private val _nearMeEnabled = MutableStateFlow(false)
    private val _userLocation = MutableStateFlow<LatLng?>(null)
    private val _eventTimeFilter = MutableStateFlow(EventTimeFilter.ALL)
    private val _eventCategoryFilter = MutableStateFlow<EventCategory?>(null)

    @OptIn(FlowPreview::class)
    private val searchResults = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else searchPlaces(query)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val weatherFlow = _refreshTrigger.flatMapLatest { trigger ->
        getWeatherForLocation(MALLORCA_LAT, MALLORCA_LNG, forceRefresh = trigger > 0)
    }

    private data class EventFilters(
        val nearMe: Boolean,
        val userLoc: LatLng?,
        val timeFilter: EventTimeFilter,
        val catFilter: EventCategory?,
    )

    private val contentState = combine(
        combine(
            itineraryRepository.getAllItineraries(),
            placeRepository.getAllPlaces(),
            searchQuery,
        ) { itineraries, places, query -> Triple(itineraries, places, query) },
        combine(
            searchResults,
            weatherFlow,
            getUpcomingEvents(),
        ) { results, weather, events -> Triple(results, weather, events) },
        combine(
            visitedPlaceRepository.getAllVisitedIds(),
            recentlyViewedRepository.getRecentIds(),
            _minRating,
        ) { visitedIds, recentIds, minRating -> Triple(visitedIds, recentIds, minRating) },
        combine(_nearMeEnabled, _userLocation, _eventTimeFilter, _eventCategoryFilter) { nearMe, loc, timeFilter, catFilter ->
            EventFilters(nearMe, loc, timeFilter, catFilter)
        },
    ) { (itineraries, places, query), (results, weather, events), (visitedIds, recentIds, minRating), (nearMe, userLoc, timeFilter, catFilter) ->
        val visitedSet = visitedIds.toSet()

        val allPlaces = places.map { p -> p.copy(isVisited = p.id in visitedSet) }
        val supPlaces = allPlaces.filter { it.subCategories.contains("sup_launch") }

        var popularPlaces = allPlaces.filter { !it.subCategories.contains("sup_launch") }
        minRating?.let { min -> popularPlaces = popularPlaces.filter { (it.rating ?: 0f) >= min } }

        popularPlaces = if (nearMe && userLoc != null) {
            popularPlaces.map { p -> p.copy(distanceKm = haversineKm(userLoc, p.location)) }
                .sortedBy { it.distanceKm }
        } else {
            popularPlaces.sortedByDescending { it.rating }
        }

        val recentPlaces = recentIds.mapNotNull { id -> allPlaces.find { it.id == id } }.take(8)

        val now = System.currentTimeMillis()
        val weekMs = 7 * 86_400_000L
        val monthMs = 30 * 86_400_000L
        var filteredEvents = when (timeFilter) {
            EventTimeFilter.ALL -> events
            EventTimeFilter.THIS_WEEK -> events.filter { e ->
                e.isRecurring || e.startDateEpoch in now..(now + weekMs)
                    || (e.endDateEpoch ?: e.startDateEpoch) in now..(now + weekMs)
            }
            EventTimeFilter.THIS_MONTH -> events.filter { e ->
                e.isRecurring || e.startDateEpoch in now..(now + monthMs)
                    || (e.endDateEpoch ?: e.startDateEpoch) in now..(now + monthMs)
            }
            EventTimeFilter.NEXT_MONTH -> events.filter { e ->
                e.isRecurring || e.startDateEpoch in (now + monthMs)..(now + 2 * monthMs)
            }
        }
        catFilter?.let { cat -> filteredEvents = filteredEvents.filter { it.category == cat } }

        ExploreUiState(
            isLoading = false,
            totalPlaceCount = allPlaces.size,
            featuredItineraries = itineraries.take(6).toImmutableList(),
            popularPlaces = popularPlaces.toImmutableList(),
            supPlaces = supPlaces.toImmutableList(),
            recentPlaces = recentPlaces.toImmutableList(),
            searchResults = results.map { p -> p.copy(isVisited = p.id in visitedSet) }.toImmutableList(),
            searchQuery = query,
            isSearching = query.isNotBlank(),
            weather = weather,
            upcomingEvents = filteredEvents.toImmutableList(),
            minRating = minRating,
            nearMeEnabled = nearMe,
            userLocation = userLoc,
            eventTimeFilter = timeFilter,
            eventCategoryFilter = catFilter,
        )
    }

    val uiState: StateFlow<ExploreUiState> = combine(
        combine(contentState, _isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) },
        localeSource.locale,
    ) { state, locale -> state.copy(locale = locale) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExploreUiState(),
    )

    fun onSearchQueryChanged(query: String) { searchQuery.value = query }
    fun onSearchCleared() { searchQuery.value = "" }

    fun setMinRating(rating: Float?) { _minRating.value = rating }
    fun setEventTimeFilter(filter: EventTimeFilter) { _eventTimeFilter.value = filter }
    fun setEventCategoryFilter(cat: EventCategory?) { _eventCategoryFilter.value = cat }

    fun setLocation(lat: Double, lng: Double) {
        _userLocation.value = LatLng(lat, lng)
        _nearMeEnabled.value = true
    }

    fun toggleNearMe() { _nearMeEnabled.update { !it } }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshTrigger.update { it + 1 }
            delay(1_500)
            _isRefreshing.value = false
        }
    }

    private fun haversineKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
