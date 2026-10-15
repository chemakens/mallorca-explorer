package com.mallorca.explorer.core.domain.usecase.weather

import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WeatherCondition
import javax.inject.Inject

class GetSUPWeatherStatus @Inject constructor() {
    /**
     * [place] puede ser null (itinerario sin paradas): en ese caso, o si la parada no trae
     * `facing:`, se usa [fallbackFacingDeg] (p. ej. weather_config.beach_facing_deg).
     * Solo cambia DE DÓNDE sale la orientación; el cálculo del semáforo no se toca.
     */
    operator fun invoke(place: Place?, weather: WeatherCondition, fallbackFacingDeg: Int? = null): SUPWeatherStatus? {
        val beachFacingDeg = place?.subCategories
            ?.firstOrNull { it.startsWith("facing:") }
            ?.removePrefix("facing:")
            ?.toIntOrNull()
            ?: fallbackFacingDeg
            ?: return null

        return SUPWeatherStatus(
            placeId = place?.id ?: "",
            windKnots = weather.windKnots,
            windKmh = weather.windKmh,
            windDirectionDeg = weather.windDirectionDeg,
            windGustKnots = weather.windGustKnots,
            waveHeightM = weather.waveHeightM,
            seaTempC = weather.seaTempC,
            beachFacingDeg = beachFacingDeg,
        )
    }
}
