package com.mallorca.explorer.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mallorca.explorer.core.data.database.dao.DiscountDao
import com.mallorca.explorer.core.data.database.dao.EventDao
import com.mallorca.explorer.core.data.database.dao.ItineraryDao
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.entity.DiscountEntity
import com.mallorca.explorer.core.data.database.entity.EventEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@HiltWorker
class SeedDataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val itineraryDao: ItineraryDao,
    private val eventDao: EventDao,
    private val discountDao: DiscountDao,
    private val prefsDataStore: UserPreferencesDataStore,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val currentVersion = prefsDataStore.seedVersion.first()
        if (currentVersion >= CURRENT_SEED_VERSION) return Result.success()

        return try {
            val rawJson = applicationContext.assets.open("seed_data.json")
                .bufferedReader().use { it.readText() }
            val seedData = json.decodeFromString<SeedData>(rawJson)

            // Hard-delete places that were removed from the seed in previous versions.
            // upsertAll never removes rows, so this explicit step is required.
            placeDao.deleteByIds(DELETED_PLACE_IDS)
            itineraryDao.deleteStopsByPlaceIds(DELETED_PLACE_IDS)

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
                            noteEs = stop.note_es,
                            noteDe = stop.note_de,
                            dayNumber = stop.day_number,
                        )
                    }
                )
            }

            eventDao.upsertAll(seedData.events.map { it.toEntity() })
            discountDao.upsertAll(seedData.discounts.map { it.toEntity() })

            prefsDataStore.setIsSeeded(true)
            prefsDataStore.setSeedVersion(CURRENT_SEED_VERSION)
            Timber.d("Seeded ${seedData.places.size} places, ${seedData.itineraries.size} itineraries, ${seedData.events.size} events (v$CURRENT_SEED_VERSION)")
            Result.success()
        } catch (e: SerializationException) {
            Timber.e(e, "Seed JSON is malformed — permanent failure, no retry")
            Result.failure()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Seed data contains invalid value — permanent failure, no retry")
            Result.failure()
        } catch (e: Exception) {
            Timber.e(e, "Failed to seed data — will retry")
            Result.retry()
        }
    }

    companion object {
        const val CURRENT_SEED_VERSION = 99

        // Places removed from seed_data.json that must be deleted from the local DB.
        // Add new IDs here whenever a place is retired from the seed.
        val DELETED_PLACE_IDS = listOf(
            "gem-cala-es-pontarro",
            "gem-punta-galinda",
            "ruta-cala-binis",
            "arta",
            "gem-bassa-de-can-coll",
            "gem-font-de-la-vila",
            "gem-cova-ses-bruixes",
        )
    }

    @Serializable data class SeedData(
        val places: List<SeedPlace> = emptyList(),
        val itineraries: List<SeedItinerary> = emptyList(),
        val events: List<SeedEvent> = emptyList(),
        val discounts: List<SeedDiscount> = emptyList(),
    )

    @Serializable data class SeedPhotoUrl(
        val url: String,
        val source: String = "OTHER",
        val author: String? = null,
    )

    @Serializable data class SeedPlace(
        val id: String, val name: String, val name_es: String = "",
        val description: String = "", val description_es: String = "", val category: String,
        val name_de: String = "", val name_ru: String = "", val name_zh: String = "",
        val description_de: String = "", val description_ru: String = "", val description_zh: String = "",
        val tips_es: List<String> = emptyList(), val tips_de: List<String> = emptyList(),
        val tips_ru: List<String> = emptyList(), val tips_zh: List<String> = emptyList(),
        val latitude: Double, val longitude: Double,
        val address: String? = null, val municipality: String = "",
        val photo_urls: List<SeedPhotoUrl> = emptyList(), val thumbnail_url: String = "",
        val price_level: String? = null, val rating: Float? = null,
        val review_count: Int = 0, val tips: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val website: String? = null, val phone_number: String? = null,
        val ticket_url: String? = null,
    ) {
        fun toEntity(): PlaceEntity {
            val photoImagesJson = Json.encodeToString(
                ListSerializer(SeedPhotoUrl.serializer()),
                photo_urls,
            )
            return PlaceEntity(
                id = id, name = name, nameEs = name_es, description = description, descriptionEs = description_es,
                nameDe = name_de, nameRu = name_ru, nameZh = name_zh,
                descriptionDe = description_de, descriptionRu = description_ru, descriptionZh = description_zh,
                tipsEsJson = Json.encodeToString(ListSerializer(String.serializer()), tips_es),
                tipsDeJson = Json.encodeToString(ListSerializer(String.serializer()), tips_de),
                tipsRuJson = Json.encodeToString(ListSerializer(String.serializer()), tips_ru),
                tipsZhJson = Json.encodeToString(ListSerializer(String.serializer()), tips_zh),
                category = category.uppercase(), latitude = latitude, longitude = longitude,
                address = address, municipality = municipality,
                photoUrlsJson = photoImagesJson,
                thumbnailUrl = thumbnail_url, openingHoursJson = null,
                priceLevel = price_level?.uppercase(), rating = rating,
                reviewCount = review_count,
                tipsJson = Json.encodeToString(ListSerializer(String.serializer()), tips),
                tagsJson = Json.encodeToString(ListSerializer(String.serializer()), tags),
                website = website, phoneNumber = phone_number,
                ticketUrl = ticket_url,
                lastUpdatedEpoch = Instant.now().toEpochMilli(),
            )
        }
    }

    @Serializable data class SeedTranslation(
        val title: String? = null,
        val description: String? = null,
        val highlights: List<String>? = null,
    )

    @Serializable data class SeedWeatherConfig(
        val fetch_lat: Double = 0.0, val fetch_lng: Double = 0.0,
        val beach_facing_deg: Int = 0, val max_safe_wind_knots: Float = 10f,
        val max_safe_gust_knots: Float = 14f, val tramuntana_warning: Boolean = false,
        val tramuntana_note_es: String = "", val cave_entry_max_wave_m: Float? = null,
        val cave_entry_max_wind_knots: Float? = null,
    )

    @Serializable data class SeedQrEntryPoint(
        val qr_id: String = "", val source_beach: String = "",
        val deep_link: String = "", val show_weather_block_on_entry: Boolean = false,
    )

    @Serializable data class SeedCommercialBlock(
        val enabled: Boolean = false, val partner_name: String = "",
        val cta_label_es: String = "", val phone: String = "", val whatsapp: String = "",
        val partner_lat: Double = 0.0, val partner_lng: Double = 0.0,
        val discount_code: String = "", val discount_pct: Int = 0, val discount_note_es: String = "",
    )

    @Serializable data class SeedRouteWaypoint(
        val order: Int = 0, val name: String = "", val role: String = "WAYPOINT",
        val lat: Double = 0.0, val lng: Double = 0.0, val note_es: String = "",
        val distance_from_prev_km: Float = 0f, val conditional: Boolean = false,
        val condition_note_es: String = "",
    )

    @Serializable data class SeedItinerary(
        val id: String, val title: String, val description: String = "",
        val category: String, val duration_days: Int = 1,
        val difficulty: String? = null, val cover_photo_url: String = "",
        val total_distance_km: Float? = null,
        val highlights: List<String> = emptyList(),
        val best_seasons: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val translations: Map<String, SeedTranslation> = emptyMap(),
        val stops: List<SeedStop> = emptyList(),
        val weather_config: SeedWeatherConfig? = null,
        val qr_entry_point: SeedQrEntryPoint? = null,
        val commercial_block: SeedCommercialBlock? = null,
        val route_waypoints: List<SeedRouteWaypoint> = emptyList(),
        val gallery_photos: List<String> = emptyList(),
    ) {
        fun toEntity(): ItineraryEntity {
            val j = Json
            val translationsJson = if (translations.isEmpty()) "{}" else
                j.encodeToString(MapSerializer(String.serializer(), SeedTranslation.serializer()), translations)
            return ItineraryEntity(
                id = id, title = title, description = description,
                category = category.uppercase(), durationDays = duration_days,
                difficulty = difficulty?.uppercase(), coverPhotoUrl = cover_photo_url,
                totalDistanceKm = total_distance_km,
                highlightsJson = j.encodeToString(ListSerializer(String.serializer()), highlights),
                bestSeasonsJson = j.encodeToString(ListSerializer(String.serializer()), best_seasons),
                tagsJson = j.encodeToString(ListSerializer(String.serializer()), tags),
                translationsJson = translationsJson,
                weatherConfigJson = weather_config?.let { j.encodeToString(SeedWeatherConfig.serializer(), it) },
                qrEntryPointJson = qr_entry_point?.let { j.encodeToString(SeedQrEntryPoint.serializer(), it) },
                commercialBlockJson = commercial_block?.let { j.encodeToString(SeedCommercialBlock.serializer(), it) },
                routeWaypointsJson = j.encodeToString(ListSerializer(SeedRouteWaypoint.serializer()), route_waypoints),
                galleryPhotosJson = j.encodeToString(ListSerializer(String.serializer()), gallery_photos),
            )
        }
    }

    @Serializable data class SeedStop(
        val place_id: String, val order: Int,
        val suggested_duration_minutes: Int = 60,
        val note: String? = null,
        val note_es: String? = null,
        val note_de: String? = null,
        val day_number: Int = 1,
    )

    @Serializable data class SeedEvent(
        val id: String,
        val title: String,
        val title_es: String = "",
        val title_de: String = "",
        val title_ru: String = "",
        val title_zh: String = "",
        val description: String = "",
        val description_es: String = "",
        val description_de: String = "",
        val description_ru: String = "",
        val description_zh: String = "",
        val category: String,
        val start_date: String,
        val end_date: String? = null,
        val municipality: String = "",
        val address: String? = null,
        val is_free: Boolean = true,
        val price: String? = null,
        val image_url: String? = null,
        val is_recurring: Boolean = false,
        val recurring_day_of_week: Int? = null,
    ) {
        fun toEntity(): EventEntity {
            fun parseDate(s: String): Long =
                LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return EventEntity(
                id = id, title = title, titleEs = title_es,
                titleDe = title_de, titleRu = title_ru, titleZh = title_zh,
                description = description, descriptionEs = description_es,
                descriptionDe = description_de, descriptionRu = description_ru, descriptionZh = description_zh,
                category = category.uppercase(),
                startDateEpoch = parseDate(start_date),
                endDateEpoch = end_date?.let { parseDate(it) },
                municipality = municipality, address = address,
                isFree = is_free, price = price, imageUrl = image_url,
                isRecurring = is_recurring, recurringDayOfWeek = recurring_day_of_week,
            )
        }
    }

    @Serializable data class SeedDiscount(
        val id: String,
        val place_id: String,
        val partner_name: String,
        val headline: String,
        val headline_es: String = "",
        val headline_de: String = "",
        val headline_ru: String = "",
        val headline_zh: String = "",
        val terms: String = "",
        val terms_es: String = "",
        val terms_de: String = "",
        val terms_ru: String = "",
        val terms_zh: String = "",
        val code: String,
        val valid_until: String,
    ) {
        fun toEntity(): DiscountEntity {
            fun parseDate(s: String): Long =
                LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return DiscountEntity(
                id = id, placeId = place_id, partnerName = partner_name,
                headline = headline,
                headlineEs = headline_es, headlineDe = headline_de,
                headlineRu = headline_ru, headlineZh = headline_zh,
                terms = terms,
                termsEs = terms_es, termsDe = terms_de,
                termsRu = terms_ru, termsZh = terms_zh,
                code = code,
                validUntilEpoch = parseDate(valid_until),
            )
        }
    }
}
