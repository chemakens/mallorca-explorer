package com.mallorca.explorer.core.domain.model

data class SUPWeatherStatus(
    val placeId: String,
    val windKnots: Float,
    val windKmh: Float,
    val windDirectionDeg: Int,
    val windGustKnots: Float?,
    val waveHeightM: Float?,
    val seaTempC: Float?,
    val beachFacingDeg: Int,
) {
    // Dirección hacia donde VA el viento (opuesta a "de donde viene")
    val windGoingDeg: Int get() = (windDirectionDeg + 180) % 360

    val windCategory: WindCategory get() {
        val diff = angularDiff(windGoingDeg, beachFacingDeg)
        return when {
            diff < 45  -> WindCategory.OFFSHORE    // viento sale hacia el mar — PELIGRO
            diff > 135 -> WindCategory.ONSHORE     // viento empuja hacia tierra — seguro
            else       -> WindCategory.CROSS_SHORE
        }
    }

    val trafficLight: SUPTrafficLight get() = when {
        windCategory == WindCategory.OFFSHORE                             -> SUPTrafficLight.RED
        windKnots > 12f                                                   -> SUPTrafficLight.RED
        windKnots > 10f                                                   -> SUPTrafficLight.RED
        windCategory == WindCategory.CROSS_SHORE && windKnots > 5f       -> SUPTrafficLight.YELLOW
        windKnots in 6f..10f                                             -> SUPTrafficLight.YELLOW
        else                                                              -> SUPTrafficLight.GREEN
    }

    val hasGustWarning: Boolean
        get() = (windGustKnots ?: 0f) > windKnots * 1.4f && (windGustKnots ?: 0f) > 8f

    private fun angularDiff(a: Int, b: Int): Int {
        val diff = Math.abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }
}

enum class WindCategory {
    ONSHORE,    // viento de mar → tierra (seguro)
    OFFSHORE,   // viento de tierra → mar (peligroso)
    CROSS_SHORE // lateral
}

enum class SUPTrafficLight(
    val emoji: String,
    val label: String,
    val detail: String,
) {
    GREEN(
        "🟢",
        "Mar Espejo — Ideal",
        "Condiciones perfectas para todos los niveles",
    ),
    YELLOW(
        "🟡",
        "Precaución",
        "Experiencia moderada. No alejarse de la costa",
    ),
    RED(
        "🔴",
        "No Recomendado",
        "Condiciones peligrosas hoy",
    ),
}

fun windDegToCardinal(deg: Int): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return dirs[((deg + 22.5) / 45).toInt() % 8]
}
