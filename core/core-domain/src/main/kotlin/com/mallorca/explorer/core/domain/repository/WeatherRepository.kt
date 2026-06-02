package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.WeatherCondition
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun getWeatherForLocation(lat: Double, lng: Double, includeMarine: Boolean, forceRefresh: Boolean = false): Flow<WeatherCondition?>
}
