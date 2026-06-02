package com.mallorca.explorer.feature.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Discount
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.repository.HiddenGemRepository
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.repository.RecentlyViewedRepository
import com.mallorca.explorer.core.domain.repository.VisitedPlaceRepository
import com.mallorca.explorer.core.domain.usecase.discount.GetDiscountsForPlace
import com.mallorca.explorer.core.domain.usecase.favorite.ToggleFavorite
import com.mallorca.explorer.core.domain.usecase.gem.UnlockGem
import com.mallorca.explorer.core.domain.usecase.place.GetPlaceDetail
import com.mallorca.explorer.core.domain.usecase.weather.GetSUPWeatherStatus
import com.mallorca.explorer.core.domain.usecase.weather.GetWeatherForLocation
import com.mallorca.explorer.core.common.LocaleSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val place: Place? = null,
    val weather: WeatherCondition? = null,
    val supStatus: SUPWeatherStatus? = null,
    val isSUPPlace: Boolean = false,
    val discounts: ImmutableList<Discount> = persistentListOf(),
    val isHiddenGem: Boolean = false,
    val isUnlocked: Boolean = false,
    val isVisited: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val locale: String = "en",
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaceDetail: GetPlaceDetail,
    private val toggleFavorite: ToggleFavorite,
    private val getWeatherForLocation: GetWeatherForLocation,
    private val getDiscountsForPlace: GetDiscountsForPlace,
    private val hiddenGemRepository: HiddenGemRepository,
    private val unlockGem: UnlockGem,
    private val getSUPWeatherStatus: GetSUPWeatherStatus,
    private val visitedPlaceRepository: VisitedPlaceRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
    private val placeRepository: PlaceRepository,
    private val localeSource: LocaleSource,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle["placeId"])

    init {
        android.util.Log.e("MallorcaApp", "ViewModel iniciado, registrando vista para: $placeId")

        viewModelScope.launch {
            recentlyViewedRepository.recordView(placeId)
            android.util.Log.d("PlaceDetailVM", ">>> Intentando obtener detalles...")

            try {
                // placeFlow es el flujo que obtienes de tu usecase
                val place = getPlaceDetail(placeId)
                android.util.Log.d("PlaceDetailVM", ">>> Carga exitosa: ${place.hashCode()}")
            } catch (e: Exception) {
                android.util.Log.e("PlaceDetailVM", ">>> Error cargando detalle: ${e.message}", e)
            }
        }
    }


    private val placeFlow = getPlaceDetail(placeId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val weatherFlow = placeFlow.flatMapLatest { place ->
        val isSUP = place?.subCategories?.contains("sup_launch") == true
        if (place?.category == Category.BEACH || isSUP) {
            getWeatherForLocation(place!!.location.latitude, place.location.longitude, includeMarine = isSUP)
        } else {
            flowOf(null)
        }
    }

    private val unlockedGemsFlow = hiddenGemRepository.getUnlockedGemIds()

    val uiState: StateFlow<PlaceDetailUiState> = combine(
        combine(
            placeFlow,
            weatherFlow,
            getDiscountsForPlace(placeId),
            unlockedGemsFlow,
            visitedPlaceRepository.isVisited(placeId),
        ) { place, weather, discounts, unlockedIds, isVisited ->
            val isHiddenGem = place?.subCategories?.contains("hidden_gem") == true
            val isSUP = place?.subCategories?.contains("sup_launch") == true
            val supStatus = if (place != null && weather != null && isSUP) {
                getSUPWeatherStatus(place, weather)
            } else null
            PlaceDetailUiState(
                place = place,
                weather = weather,
                supStatus = supStatus,
                isSUPPlace = isSUP,
                discounts = discounts.toImmutableList(),
                isHiddenGem = isHiddenGem,
                isUnlocked = !isHiddenGem || placeId in unlockedIds,
                isVisited = isVisited,
                isLoading = false,
            )
        },
        localeSource.locale,
    ) { state, locale -> state.copy(locale = locale) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaceDetailUiState(),
        )

    fun onFavoriteToggled() {
        viewModelScope.launch { toggleFavorite(placeId) }
    }

    fun onVisitedToggled() {
        viewModelScope.launch { visitedPlaceRepository.toggleVisited(placeId) }
    }

    fun onUnlockGem() {
        viewModelScope.launch { unlockGem(placeId) }
    }
}
