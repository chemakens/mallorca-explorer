package com.mallorca.explorer.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val cacheKey: String, // "{lat}_{lng}_{marine}"
    val lat: Double,
    val lng: Double,
    val tempC: Float,
    val precipMm: Float,
    val windKmh: Float,
    val windDirectionDeg: Int,
    val windGustKmh: Float?,
    val uvIndex: Float,
    val waveHeightM: Float?,
    val wavePeriodS: Float?,
    val seaTempC: Float?,
    val fetchedEpoch: Long,
)
