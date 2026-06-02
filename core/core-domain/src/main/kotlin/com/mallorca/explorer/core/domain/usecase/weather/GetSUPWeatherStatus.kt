package com.mallorca.explorer.core.domain.usecase.weather

import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WeatherCondition
import javax.inject.Inject

class GetSUPWeatherStatus @Inject constructor() {
    operator fun invoke(place: Place, weather: WeatherCondition): SUPWeatherStatus? {
        val beachFacingDeg = place.subCategories
            .firstOrNull { it.startsWith("facing:") }
            ?.removePrefix("facing:")
            ?.toIntOrNull()
            ?: return null

        return SUPWeatherStatus(
            placeId = place.id,
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
