package com.mallorca.explorer.core.data.network

import com.mallorca.explorer.core.data.network.dto.ForecastResponseDto
import com.mallorca.explorer.core.data.network.dto.MarineResponseDto
import retrofit2.http.GET
import retrofit2.http.Url

interface OpenMeteoApiService {
    @GET
    suspend fun getForecast(@Url url: String): ForecastResponseDto

    @GET
    suspend fun getMarineConditions(@Url url: String): MarineResponseDto
}
