package com.mallorca.explorer.core.data.network

import com.mallorca.explorer.core.data.network.dto.ItineraryDto
import com.mallorca.explorer.core.data.network.dto.PlaceDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MallorcaApiService {
    @GET("places")
    suspend fun getPlaces(@Query("category") category: String? = null): List<PlaceDto>

    @GET("places/{id}")
    suspend fun getPlace(@Path("id") id: String): PlaceDto

    @GET("itineraries")
    suspend fun getItineraries(@Query("category") category: String? = null): List<ItineraryDto>

    @GET("itineraries/{id}")
    suspend fun getItinerary(@Path("id") id: String): ItineraryDto
}
