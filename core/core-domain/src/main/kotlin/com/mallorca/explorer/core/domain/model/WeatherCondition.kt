package com.mallorca.explorer.core.domain.model

data class WeatherCondition(
    val lat: Double,
    val lng: Double,
    val tempC: Float,
    val precipMm: Float,
    val windKmh: Float,
    val windDirectionDeg: Int = 0,
    val windGustKmh: Float? = null,
    val uvIndex: Float,
    val waveHeightM: Float? = null,
    val wavePeriodS: Float? = null,
    val seaTempC: Float? = null,
    val fetchedEpoch: Long,
) {
    val windKnots: Float get() = windKmh / 1.852f
    val windGustKnots: Float? get() = windGustKmh?.div(1.852f)
    val summary: WeatherSummary
        get() = when {
            precipMm >= 1f -> WeatherSummary.RAINY
            windKmh >= 30f -> WeatherSummary.WINDY
            tempC >= 32f -> WeatherSummary.HOT
            tempC >= 20f && precipMm < 0.5f -> WeatherSummary.SUNNY
            else -> WeatherSummary.MILD
        }

    val recommendedCategories: List<Category>
        get() = when (summary) {
            WeatherSummary.RAINY -> listOf(Category.CULTURE, Category.GASTRONOMY, Category.TOWN)
            WeatherSummary.WINDY -> listOf(Category.CULTURE, Category.GASTRONOMY, Category.HIKING)
            WeatherSummary.HOT -> listOf(Category.BEACH, Category.VIEWPOINT, Category.TOWN)
            WeatherSummary.SUNNY -> listOf(Category.BEACH, Category.ADVENTURE, Category.HIKING, Category.VIEWPOINT)
            WeatherSummary.MILD -> listOf(Category.HIKING, Category.CULTURE, Category.ADVENTURE, Category.TOWN)
        }

    val bannerEmoji: String get() = summary.emoji
    val bannerText: String get() = summary.bannerText(tempC)
}

enum class WeatherSummary(val emoji: String) {
    SUNNY("☀️"), RAINY("🌧️"), WINDY("💨"), HOT("🌡️"), MILD("⛅");

    fun bannerText(tempC: Float): String = when (this) {
        SUNNY -> "Día soleado · ${tempC.toInt()}°C — ideal para playa y aventura"
        RAINY -> "Lluvia prevista — te recomendamos cultura y gastronomía"
        WINDY -> "Viento fuerte — evita rutas costeras hoy"
        HOT -> "${tempC.toInt()}°C — perfecto para playa y miradores"
        MILD -> "${tempC.toInt()}°C nublado — ideal para senderismo y pueblos"
    }
}
