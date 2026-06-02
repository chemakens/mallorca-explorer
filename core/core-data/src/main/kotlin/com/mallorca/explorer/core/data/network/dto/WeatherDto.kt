package com.mallorca.explorer.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponseDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: ForecastCurrentDto = ForecastCurrentDto(),
)

@Serializable
data class ForecastCurrentDto(
    @SerialName("temperature_2m") val temperatureC: Float = 0f,
    val precipitation: Float = 0f,
    @SerialName("wind_speed_10m") val windSpeedKmh: Float = 0f,
    @SerialName("wind_direction_10m") val windDirectionDeg: Int = 0,
    @SerialName("wind_gusts_10m") val windGustKmh: Float? = null,
    @SerialName("uv_index") val uvIndex: Float = 0f,
)

@Serializable
data class MarineResponseDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: MarineCurrentDto = MarineCurrentDto(),
)

@Serializable
data class MarineCurrentDto(
    @SerialName("wave_height") val waveHeightM: Float? = null,
    @SerialName("wave_period") val wavePeriodS: Float? = null,
    @SerialName("sea_surface_temperature") val seaTempC: Float? = null,
)
