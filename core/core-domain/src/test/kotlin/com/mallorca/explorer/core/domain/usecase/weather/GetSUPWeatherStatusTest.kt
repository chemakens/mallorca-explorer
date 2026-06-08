package com.mallorca.explorer.core.domain.usecase.weather

import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.SUPTrafficLight
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.model.WindCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GetSUPWeatherStatusTest {

    private val useCase = GetSUPWeatherStatus()

    // Helpers

    private fun place(subCategories: List<String> = emptyList()) = Place(
        id = "beach-1",
        name = "Test Beach",
        nameEs = "Playa Test",
        description = "",
        category = Category.BEACH,
        subCategories = subCategories,
        location = LatLng(39.7, 2.9),
        municipality = "Pollença",
    )

    private fun weather(
        windKmh: Float = 10f,
        windDirectionDeg: Int = 0,
        windGustKmh: Float? = null,
    ) = WeatherCondition(
        lat = 39.7, lng = 2.9,
        tempC = 22f, precipMm = 0f,
        windKmh = windKmh,
        windDirectionDeg = windDirectionDeg,
        windGustKmh = windGustKmh,
        uvIndex = 4f,
        fetchedEpoch = 0L,
    )

    // --- Null-return cases ---

    @Test
    fun `returns null when subCategories is empty`() {
        assertNull(useCase(place(), weather()))
    }

    @Test
    fun `returns null when no facing subCategory present`() {
        assertNull(useCase(place(listOf("sup_launch", "snorkel")), weather()))
    }

    @Test
    fun `returns null when facing value is not a valid integer`() {
        assertNull(useCase(place(listOf("facing:north")), weather()))
    }

    // --- Correct field extraction ---

    @Test
    fun `extracts beachFacingDeg from subCategories`() {
        val result = useCase(place(listOf("sup_launch", "facing:135")), weather())
        assertNotNull(result)
        assertEquals(135, result!!.beachFacingDeg)
    }

    @Test
    fun `copies weather fields into status`() {
        val w = weather(windKmh = 18.52f, windDirectionDeg = 90, windGustKmh = 27.78f)
        val result = useCase(place(listOf("facing:90")), w)!!

        assertEquals(w.windKnots, result.windKnots, 0.001f)
        assertEquals(w.windKmh, result.windKmh, 0.001f)
        assertEquals(w.windDirectionDeg, result.windDirectionDeg)
        assertEquals(w.windGustKnots!!, result.windGustKnots!!, 0.001f)
    }

    // --- Wind category classification ---
    // Convention: beachFacingDeg = direction the beach FACES (toward the sea).
    // windGoingDeg = (windDirectionDeg + 180) % 360.
    // OFFSHORE: wind going roughly toward beachFacingDeg (away from shore → danger).
    // ONSHORE:  wind going roughly away from beachFacingDeg (toward land → safe).

    @Test
    fun `ONSHORE when wind blows toward land (sea to shore)`() {
        // Beach faces east (90°). Wind FROM east (dir=90°) → windGoingDeg=270 → diff(270,90)=180 → ONSHORE
        val result = useCase(place(listOf("facing:90")), weather(windDirectionDeg = 90))!!
        assertEquals(WindCategory.ONSHORE, result.windCategory)
    }

    @Test
    fun `OFFSHORE when wind blows toward sea (shore to sea)`() {
        // Beach faces east (90°). Wind FROM west (dir=270°) → windGoingDeg=90 → diff(90,90)=0 → OFFSHORE
        val result = useCase(place(listOf("facing:90")), weather(windDirectionDeg = 270))!!
        assertEquals(WindCategory.OFFSHORE, result.windCategory)
    }

    @Test
    fun `CROSS_SHORE when wind blows laterally`() {
        // Beach faces east (90°). Wind FROM north (dir=0°) → windGoingDeg=180 → diff(180,90)=90 → CROSS_SHORE
        val result = useCase(place(listOf("facing:90")), weather(windDirectionDeg = 0))!!
        assertEquals(WindCategory.CROSS_SHORE, result.windCategory)
    }

    // --- Traffic light ---

    @Test
    fun `trafficLight is GREEN for light onshore wind`() {
        // Onshore, windKmh=7 → windKnots≈3.8 → not in any RED or YELLOW band
        val result = useCase(
            place(listOf("facing:90")),
            weather(windKmh = 7f, windDirectionDeg = 90),
        )!!
        assertEquals(SUPTrafficLight.GREEN, result.trafficLight)
    }

    @Test
    fun `trafficLight is RED for any offshore wind`() {
        // Offshore always RED regardless of speed
        val result = useCase(
            place(listOf("facing:90")),
            weather(windKmh = 5f, windDirectionDeg = 270),
        )!!
        assertEquals(SUPTrafficLight.RED, result.trafficLight)
    }

    @Test
    fun `trafficLight is RED when windKnots exceeds 12`() {
        // Onshore but very strong (windKmh=25 → windKnots≈13.5 > 12)
        val result = useCase(
            place(listOf("facing:90")),
            weather(windKmh = 25f, windDirectionDeg = 90),
        )!!
        assertEquals(WindCategory.ONSHORE, result.windCategory)
        assertEquals(SUPTrafficLight.RED, result.trafficLight)
    }

    @Test
    fun `trafficLight is YELLOW for moderate cross-shore wind`() {
        // Cross-shore, windKmh=12 → windKnots≈6.5 > 5 → YELLOW
        val result = useCase(
            place(listOf("facing:90")),
            weather(windKmh = 12f, windDirectionDeg = 0),
        )!!
        assertEquals(WindCategory.CROSS_SHORE, result.windCategory)
        assertEquals(SUPTrafficLight.YELLOW, result.trafficLight)
    }

    // --- Gust warning ---

    @Test
    fun `hasGustWarning true when gusts exceed 1_4x wind and are above 8 knots`() {
        // windKmh=10 → windKnots≈5.4; gustKmh=20 → gustKnots≈10.8
        // 10.8 > 5.4*1.4=7.56 AND 10.8 > 8 → warning
        val result = useCase(
            place(listOf("facing:90")),
            weather(windKmh = 10f, windGustKmh = 20f),
        )!!
        assertEquals(true, result.hasGustWarning)
    }

    @Test
    fun `hasGustWarning false when gusts are proportional to wind`() {
        // windKmh=20 → windKnots≈10.8; gustKmh=22 → gustKnots≈11.9
        // 11.9 < 10.8*1.4=15.1 → no warning
        val result = useCase(
            place(listOf("facing:90")),
            weather(windKmh = 20f, windGustKmh = 22f),
        )!!
        assertEquals(false, result.hasGustWarning)
    }

    @Test
    fun `hasGustWarning false when no gusts reported`() {
        val result = useCase(place(listOf("facing:90")), weather(windGustKmh = null))!!
        assertEquals(false, result.hasGustWarning)
    }
}
