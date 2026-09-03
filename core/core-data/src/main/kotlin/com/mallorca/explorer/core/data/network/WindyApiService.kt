package com.mallorca.explorer.core.data.network

import com.mallorca.explorer.core.data.network.dto.WindyForecastResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface WindyApiService {
    @POST("point-forecast/v2")
    suspend fun getPointForecast(@Body request: WindyForecastRequestBody): WindyForecastResponseDto
}

/**
 * Request body para Windy API point-forecast.
 *
 * @param lat Latitud
 * @param lon Longitud
 * @param model Modelo meteorológico (gfs, ecmwf, etc.)
 * @param parameters Parámetros a obtener (wind_u-surface, wind_v-surface, etc.)
 * @param levels Lista de niveles atmosféricos (surface para superficie)
 * @param key API key de Windy
 */
data class WindyForecastRequestBody(
    val lat: Double,
    val lon: Double,
    val model: String = "gfs",
    val parameters: List<String> = listOf(
        "wind_u-surface",
        "wind_v-surface",
        "wind_u-gust-surface",
        "wind_v-gust-surface"
    ),
    val levels: List<String> = listOf("surface"),
    val key: String,
)
