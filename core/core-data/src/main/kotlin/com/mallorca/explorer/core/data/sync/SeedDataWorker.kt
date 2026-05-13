package com.mallorca.explorer.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mallorca.explorer.core.data.database.dao.ItineraryDao
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant

@HiltWorker
class SeedDataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val itineraryDao: ItineraryDao,
    private val prefsDataStore: UserPreferencesDataStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isSeeded = prefsDataStore.isSeeded.first()
        if (isSeeded) return Result.success()

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val rawJson = applicationContext.assets.open("seed_data.json")
                .bufferedReader().use { it.readText() }
            val seedData = json.decodeFromString<SeedData>(rawJson)

            placeDao.upsertAll(seedData.places.map { it.toEntity() })

            seedData.itineraries.forEach { itin ->
                itineraryDao.upsertItineraryWithStops(
                    itin.toEntity(),
                    itin.stops.map { stop ->
                        ItineraryStopEntity(
                            itineraryId = itin.id,
                            placeId = stop.place_id,
                            order = stop.order,
                            suggestedDurationMinutes = stop.suggested_duration_minutes,
                            note = stop.note,
                            dayNumber = stop.day_number,
                        )
                    }
                )
            }

            prefsDataStore.setIsSeeded(true)
            Timber.d("Seeded ${seedData.places.size} places and ${seedData.itineraries.size} itineraries")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to seed data")
            Result.retry()
        }
    }

    @Serializable data class SeedData(
        val places: List<SeedPlace> = emptyList(),
        val itineraries: List<SeedItinerary> = emptyList(),
    )

    @Serializable data class SeedPlace(
        val id: String, val name: String, val name_es: String = "",
        val description: String = "", val category: String,
        val latitude: Double, val longitude: Double,
        val address: String? = null, val municipality: String = "",
        val photo_urls: List<String> = emptyList(), val thumbnail_url: String = "",
        val price_level: String? = null, val rating: Float? = null,
        val review_count: Int = 0, val tips: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val website: String? = null, val phone_number: String? = null,
    ) {
        fun toEntity() = PlaceEntity(
            id = id, name = name, nameEs = name_es, description = description,
            category = category.uppercase(), latitude = latitude, longitude = longitude,
            address = address, municipality = municipality,
            photoUrlsJson = Json.encodeToString(ListSerializer(String.serializer()), photo_urls),
            thumbnailUrl = thumbnail_url, openingHoursJson = null,
            priceLevel = price_level?.uppercase(), rating = rating,
            reviewCount = review_count,
            tipsJson = Json.encodeToString(ListSerializer(String.serializer()), tips),
            tagsJson = Json.encodeToString(ListSerializer(String.serializer()), tags),
            website = website, phoneNumber = phone_number,
            lastUpdatedEpoch = Instant.now().toEpochMilli(),
        )
    }

    @Serializable data class SeedItinerary(
        val id: String, val title: String, val description: String = "",
        val category: String, val duration_days: Int = 1,
        val difficulty: String? = null, val cover_photo_url: String = "",
        val total_distance_km: Float? = null,
        val highlights: List<String> = emptyList(),
        val best_seasons: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val stops: List<SeedStop> = emptyList(),
    ) {
        fun toEntity() = ItineraryEntity(
            id = id, title = title, description = description,
            category = category.uppercase(), durationDays = duration_days,
            difficulty = difficulty?.uppercase(), coverPhotoUrl = cover_photo_url,
            totalDistanceKm = total_distance_km,
            highlightsJson = Json.encodeToString(ListSerializer(String.serializer()), highlights),
            bestSeasonsJson = Json.encodeToString(ListSerializer(String.serializer()), best_seasons),
            tagsJson = Json.encodeToString(ListSerializer(String.serializer()), tags),
        )
    }

    @Serializable data class SeedStop(
        val place_id: String, val order: Int,
        val suggested_duration_minutes: Int = 60,
        val note: String? = null, val day_number: Int = 1,
    )
}
