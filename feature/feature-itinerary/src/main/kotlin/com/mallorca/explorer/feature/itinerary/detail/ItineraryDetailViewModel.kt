package com.mallorca.explorer.feature.itinerary.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.repository.StopProgressRepository
import com.mallorca.explorer.core.domain.usecase.itinerary.GetItineraryById
import com.mallorca.explorer.core.domain.usecase.weather.GetSUPWeatherStatus
import com.mallorca.explorer.core.domain.usecase.weather.GetWeatherForLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItineraryDetailUiState(
    val itinerary: Itinerary? = null,
    val visitedPlaceIds: ImmutableSet<String> = persistentSetOf(),
    val weather: WeatherCondition? = null,
    val supStatus: SUPWeatherStatus? = null,
    val isLoading: Boolean = true,
    val weatherIsStale: Boolean = false,
    val weatherLoadFailed: Boolean = false,
    val showQrWelcome: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItineraryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getItineraryById: GetItineraryById,
    private val stopProgressRepository: StopProgressRepository,
    private val getWeatherForLocation: GetWeatherForLocation,
    private val getSUPWeatherStatus: GetSUPWeatherStatus,
) : ViewModel() {

    private val itineraryId: String = checkNotNull(savedStateHandle["itineraryId"])

    private val itineraryFlow = getItineraryById(itineraryId)
    private val _qrWelcomeDismissed = MutableStateFlow(false)

    private val weatherFlow = itineraryFlow.flatMapLatest { itin ->
        val cfg = itin?.weatherConfig ?: return@flatMapLatest flowOf(null)
        getWeatherForLocation(cfg.fetchLat, cfg.fetchLng, includeMarine = true)
    }

    val uiState: StateFlow<ItineraryDetailUiState> = combine(
        itineraryFlow,
        stopProgressRepository.getVisitedStops(itineraryId),
        weatherFlow,
        _qrWelcomeDismissed,
    ) { itinerary, visited, weather, qrDismissed ->
        val now = System.currentTimeMillis()
        val supPlace = itinerary?.places?.firstOrNull { "sup_launch" in it.place.subCategories }?.place
        val supStatus = if (itinerary?.isSUPRoute == true && supPlace != null && weather != null)
            getSUPWeatherStatus(supPlace, weather) else null
        ItineraryDetailUiState(
            itinerary = itinerary,
            visitedPlaceIds = visited.toImmutableSet(),
            weather = weather,
            supStatus = supStatus,
            isLoading = false,
            weatherIsStale = weather != null && (now - weather.fetchedEpoch) > 30 * 60 * 1000L,
            weatherLoadFailed = itinerary?.isSUPRoute == true && weather == null,
            showQrWelcome = itinerary?.isQrLanding == true && !qrDismissed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItineraryDetailUiState(),
    )

    fun toggleStop(placeId: String) {
        viewModelScope.launch { stopProgressRepository.toggleStop(itineraryId, placeId) }
    }

    fun dismissQrWelcome() { _qrWelcomeDismissed.value = true }
}
