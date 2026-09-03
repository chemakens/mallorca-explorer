package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.data.BuildConfig
import com.mallorca.explorer.core.data.database.dao.WeatherDao
import com.mallorca.explorer.core.data.database.entity.WeatherCacheEntity
import com.mallorca.explorer.core.data.network.OpenMeteoApiService
import com.mallorca.explorer.core.data.network.WindyApiService
import com.mallorca.explorer.core.data.network.WindyForecastRequestBody
import com.mallorca.explorer.core.data.network.dto.ForecastResponseDto
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 min

class WeatherRepositoryImpl @Inject constructor(
    private val weatherDao: WeatherDao,
    private val windyApi: WindyApiService,
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
            // Intentar obtener datos de viento desde Windy primero
            var windKmh: Float? = null
            var windDirectionDeg: Int? = null
            var windGustKmh: Float? = null
            var openMeteoForecast: ForecastResponseDto? = null

            try {
                val windyResponse = windyApi.getPointForecast(
                    WindyForecastRequestBody(
                        lat = lat,
                        lon = lng,
                        key = BuildConfig.WINDY_API_KEY
                    )
                )

                val windSpeedMs = windyResponse.getCurrentWindSpeedMs()
                val windDirection = windyResponse.getCurrentWindDirectionDeg()
                val windGustMs = windyResponse.getCurrentWindGustMs()

                if (windSpeedMs != null && windDirection != null) {
                    // Convertir m/s a km/h
                    windKmh = windSpeedMs * 3.6f
                    windDirectionDeg = windDirection
                    windGustKmh = windGustMs?.let { it * 3.6f }
                    Timber.d("Using Windy data for wind: ${windKmh}km/h @ ${windDirectionDeg}°")
                } else {
                    Timber.w("Windy response incomplete, falling back to Open-Meteo")
                }
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    Timber.w("Windy API rate limit exceeded (429), falling back to Open-Meteo")
                } else {
                    Timber.w(e, "Windy API failed with HTTP ${e.code()}, falling back to Open-Meteo")
                }
            } catch (e: Exception) {
                Timber.w(e, "Windy API failed, falling back to Open-Meteo")
            }

            // Si Windy falló o no devolvió datos completos, obtener datos de Open-Meteo
            val forecastUrl = buildForecastUrl(lat, lng)
            val forecast = openMeteoApi.getForecast(forecastUrl)

            // Usar datos de viento de Windy si están disponibles, sino de Open-Meteo
            val finalWindKmh = windKmh ?: forecast.current.windSpeedKmh
            val finalWindDirectionDeg = windDirectionDeg ?: forecast.current.windDirectionDeg
            val finalWindGustKmh = windGustKmh ?: forecast.current.windGustKmh

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
                windKmh = finalWindKmh,
                windDirectionDeg = finalWindDirectionDeg,
                windGustKmh = finalWindGustKmh,
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
            Timber.e(e, "Weather fetch failed completely for $lat,$lng")
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
