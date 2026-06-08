package com.mallorca.explorer.core.data.repository

import app.cash.turbine.test
import com.mallorca.explorer.core.data.database.dao.WeatherDao
import com.mallorca.explorer.core.data.database.entity.WeatherCacheEntity
import com.mallorca.explorer.core.data.network.OpenMeteoApiService
import com.mallorca.explorer.core.data.network.dto.ForecastCurrentDto
import com.mallorca.explorer.core.data.network.dto.ForecastResponseDto
import com.mallorca.explorer.core.data.network.dto.MarineCurrentDto
import com.mallorca.explorer.core.data.network.dto.MarineResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val weatherDao: WeatherDao = mockk(relaxUnitFun = true)
    private val openMeteoApi: OpenMeteoApiService = mockk()

    private val repo = WeatherRepositoryImpl(weatherDao, openMeteoApi, dispatcher)

    private val lat = 39.7000
    private val lng = 2.9000

    // cacheKey computed by WeatherRepositoryImpl: "${lat.round4()}${lng.round4()}$includeMarine"
    private fun cacheKey(marine: Boolean) =
        "39.70002.9000$marine"

    private fun cachedEntity(fetchedEpoch: Long, windKmh: Float = 10f) = WeatherCacheEntity(
        cacheKey = cacheKey(false),
        lat = lat, lng = lng,
        tempC = 22f, precipMm = 0f,
        windKmh = windKmh, windDirectionDeg = 90, windGustKmh = null,
        uvIndex = 4f, waveHeightM = null, wavePeriodS = null, seaTempC = null,
        fetchedEpoch = fetchedEpoch,
    )

    private fun forecastResponse(windKmh: Float = 15f) = ForecastResponseDto(
        latitude = lat, longitude = lng,
        current = ForecastCurrentDto(
            temperatureC = 24f, precipitation = 0f,
            windSpeedKmh = windKmh, windDirectionDeg = 90,
            windGustKmh = null, uvIndex = 5f,
        ),
    )

    private val now get() = System.currentTimeMillis()

    // --- Cache hit ---

    @Test
    fun `fresh cache hit returns cached data without API call`() = runTest {
        val entity = cachedEntity(fetchedEpoch = now) // fetched just now → within 30 min TTL
        coEvery { weatherDao.getByKey(cacheKey(false)) } returns entity

        repo.getWeatherForLocation(lat, lng, includeMarine = false).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(entity.windKmh, result!!.windKmh)
            awaitComplete()
        }

        coVerify(exactly = 0) { openMeteoApi.getForecast(any()) }
    }

    // --- Cache miss / expired ---

    @Test
    fun `expired cache triggers API call and returns fresh data`() = runTest {
        val staleEpoch = now - 31 * 60 * 1000L // 31 min ago → expired
        val entity = cachedEntity(fetchedEpoch = staleEpoch, windKmh = 5f)
        coEvery { weatherDao.getByKey(cacheKey(false)) } returns entity
        coEvery { openMeteoApi.getForecast(any()) } returns forecastResponse(windKmh = 15f)

        repo.getWeatherForLocation(lat, lng, includeMarine = false).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(15f, result!!.windKmh)
            awaitComplete()
        }

        coVerify(exactly = 1) { openMeteoApi.getForecast(any()) }
    }

    @Test
    fun `null cache triggers API call`() = runTest {
        coEvery { weatherDao.getByKey(cacheKey(false)) } returns null
        coEvery { openMeteoApi.getForecast(any()) } returns forecastResponse(windKmh = 20f)

        repo.getWeatherForLocation(lat, lng, includeMarine = false).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(20f, result!!.windKmh)
            awaitComplete()
        }

        coVerify(exactly = 1) { openMeteoApi.getForecast(any()) }
    }

    // --- forceRefresh ---

    @Test
    fun `forceRefresh skips valid cache and calls API`() = runTest {
        val entity = cachedEntity(fetchedEpoch = now, windKmh = 5f) // fresh cache
        coEvery { weatherDao.getByKey(cacheKey(false)) } returns entity
        coEvery { openMeteoApi.getForecast(any()) } returns forecastResponse(windKmh = 25f)

        repo.getWeatherForLocation(lat, lng, includeMarine = false, forceRefresh = true).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(25f, result!!.windKmh)
            awaitComplete()
        }

        coVerify(exactly = 1) { openMeteoApi.getForecast(any()) }
    }

    // --- API failure fallback ---

    @Test
    fun `API failure with stale cache emits stale data`() = runTest {
        val staleEntity = cachedEntity(fetchedEpoch = now - 31 * 60 * 1000L, windKmh = 8f)
        coEvery { weatherDao.getByKey(cacheKey(false)) } returns staleEntity
        coEvery { openMeteoApi.getForecast(any()) } throws RuntimeException("network error")

        repo.getWeatherForLocation(lat, lng, includeMarine = false).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(8f, result!!.windKmh) // stale value returned as fallback
            awaitComplete()
        }
    }

    @Test
    fun `API failure with no cache emits null`() = runTest {
        coEvery { weatherDao.getByKey(cacheKey(false)) } returns null
        coEvery { openMeteoApi.getForecast(any()) } throws RuntimeException("network error")

        repo.getWeatherForLocation(lat, lng, includeMarine = false).test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    // --- Marine data ---

    @Test
    fun `marine API failure does not prevent forecast emission`() = runTest {
        coEvery { weatherDao.getByKey(cacheKey(true)) } returns null
        coEvery { openMeteoApi.getForecast(any()) } returns forecastResponse(windKmh = 12f)
        coEvery { openMeteoApi.getMarineConditions(any()) } throws RuntimeException("marine unavailable")

        repo.getWeatherForLocation(lat, lng, includeMarine = true).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(12f, result!!.windKmh)
            assertNull(result.waveHeightM) // marine fields absent when API fails
            awaitComplete()
        }
    }

    @Test
    fun `marine data is merged into result when available`() = runTest {
        coEvery { weatherDao.getByKey(cacheKey(true)) } returns null
        coEvery { openMeteoApi.getForecast(any()) } returns forecastResponse()
        coEvery { openMeteoApi.getMarineConditions(any()) } returns MarineResponseDto(
            current = MarineCurrentDto(waveHeightM = 0.8f, wavePeriodS = 6f, seaTempC = 21f),
        )

        repo.getWeatherForLocation(lat, lng, includeMarine = true).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(0.8f, result!!.waveHeightM)
            assertEquals(6f, result.wavePeriodS)
            assertEquals(21f, result.seaTempC)
            awaitComplete()
        }
    }
}
