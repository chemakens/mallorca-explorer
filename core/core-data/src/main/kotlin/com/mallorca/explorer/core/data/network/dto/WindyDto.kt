package com.mallorca.explorer.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.sqrt

@Serializable
data class WindyForecastResponseDto(
    val ts: List<Long> = emptyList(),
    @SerialName("wind_u-surface") val windU: List<Float> = emptyList(),
    @SerialName("wind_v-surface") val windV: List<Float> = emptyList(),
    @SerialName("wind_u-gust-surface") val windGustU: List<Float>? = null,
    @SerialName("wind_v-gust-surface") val windGustV: List<Float>? = null,
) {
    /**
     * Calcula la velocidad del viento en m/s desde componentes U y V.
     * Speed = sqrt(u² + v²)
     */
    fun getCurrentWindSpeedMs(): Float? {
        if (windU.isEmpty() || windV.isEmpty()) return null
        val u = windU.firstOrNull() ?: return null
        val v = windV.firstOrNull() ?: return null
        return sqrt(u * u + v * v)
    }

    /**
     * Calcula la dirección del viento en grados (0-360) desde componentes U y V.
     * Direction = atan2(u, v) convertido a grados meteorológicos
     */
    fun getCurrentWindDirectionDeg(): Int? {
        if (windU.isEmpty() || windV.isEmpty()) return null
        val u = windU.firstOrNull() ?: return null
        val v = windV.firstOrNull() ?: return null
        // atan2 devuelve radianes, convertir a grados y ajustar para dirección meteorológica
        val deg = Math.toDegrees(atan2(u, v).toDouble()).toFloat()
        // Convertir de dirección matemática a meteorológica (de donde viene el viento)
        val metDeg = (deg + 180) % 360
        return metDeg.toInt()
    }

    /**
     * Calcula la velocidad de ráfaga en m/s desde componentes U y V.
     */
    fun getCurrentWindGustMs(): Float? {
        if (windGustU.isNullOrEmpty() || windGustV.isNullOrEmpty()) return null
        val u = windGustU.firstOrNull() ?: return null
        val v = windGustV.firstOrNull() ?: return null
        return sqrt(u * u + v * v)
    }
}
