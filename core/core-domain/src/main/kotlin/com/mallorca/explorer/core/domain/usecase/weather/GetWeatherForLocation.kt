package com.mallorca.explorer.core.domain.usecase.weather

import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWeatherForLocation @Inject constructor(
    private val weatherRepository: WeatherRepository,
) {
    operator fun invoke(lat: Double, lng: Double, includeMarine: Boolean = false, forceRefresh: Boolean = false): Flow<WeatherCondition?> =
        weatherRepository.getWeatherForLocation(lat, lng, includeMarine, forceRefresh)
}
