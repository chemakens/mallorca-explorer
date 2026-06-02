package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.data.database.dao.WeatherDao
import com.mallorca.explorer.core.data.database.entity.WeatherCacheEntity
import com.mallorca.explorer.core.data.network.OpenMeteoApiService
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 min

class WeatherRepositoryImpl @Inject constructor(
    private val weatherDao: WeatherDao,
    private val openMeteoApi: OpenMeteoApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WeatherRepository {

    override fun getWeatherForLocation(
        lat: Double,
        lng: Double,
        includeMarine: Boolean,
        forceRefresh: Boolean,
    ): Flow<WeatherCondition?> = flow {
        val key = "${lat.round4()}${lng.round4()}$includeMarine"
        val now = System.currentTimeMillis()

        val cached = weatherDao.getByKey(key)
        if (!forceRefresh && cached != null && cached.fetchedEpoch + CACHE_TTL_MS > now) {
            emit(cached.toDomain())
            return@flow
        }

        try {
            val forecastUrl = buildForecastUrl(lat, lng)
            val forecast = openMeteoApi.getForecast(forecastUrl)

            var waveHeight: Float? = null
            var wavePeriod: Float? = null
            var seaTemp: Float? = null

            if (includeMarine) {
                val marineUrl = buildMarineUrl(lat, lng)
                runCatching { openMeteoApi.getMarineConditions(marineUrl) }
                    .onSuccess { marine ->
                        waveHeight = marine.current.waveHeightM
                        wavePeriod = marine.current.wavePeriodS
                        seaTemp = marine.current.seaTempC
                    }
                    .onFailure { Timber.w(it, "Marine API failed for $lat,$lng") }
            }

            val entity = WeatherCacheEntity(
                cacheKey = key,
                lat = lat,
                lng = lng,
                tempC = forecast.current.temperatureC,
                precipMm = forecast.current.precipitation,
                windKmh = forecast.current.windSpeedKmh,
                windDirectionDeg = forecast.current.windDirectionDeg,
                windGustKmh = forecast.current.windGustKmh,
                uvIndex = forecast.current.uvIndex,
                waveHeightM = waveHeight,
                wavePeriodS = wavePeriod,
                seaTempC = seaTemp,
                fetchedEpoch = now,
            )
            weatherDao.upsert(entity)
            weatherDao.deleteOlderThan(now - CACHE_TTL_MS * 4)
            emit(entity.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Weather fetch failed for $lat,$lng")
            emit(cached?.toDomain())
        }
    }.flowOn(ioDispatcher)

    private fun buildForecastUrl(lat: Double, lng: Double) =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lng" +
            "&current=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index" +
            "&timezone=Europe%2FMadrid"

    private fun buildMarineUrl(lat: Double, lng: Double) =
        "https://marine-api.open-meteo.com/v1/marine" +
            "?latitude=$lat&longitude=$lng" +
            "&current=wave_height,wave_period,sea_surface_temperature" +
            "&timezone=Europe%2FMadrid"

    private fun Double.round4() = String.format("%.4f", this)

    private fun WeatherCacheEntity.toDomain() = WeatherCondition(
        lat = lat,
        lng = lng,
        tempC = tempC,
        precipMm = precipMm,
        windKmh = windKmh,
        windDirectionDeg = windDirectionDeg,
        windGustKmh = windGustKmh,
        uvIndex = uvIndex,
        waveHeightM = waveHeightM,
        wavePeriodS = wavePeriodS,
        seaTempC = seaTempC,
        fetchedEpoch = fetchedEpoch,
    )
}
