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
        android.util.Log.d("SeedDataWorker", "🌱 doWork() called")
        val currentVersion = prefsDataStore.seedVersion.first()
        android.util.Log.d("SeedDataWorker", "🌱 Current version: $currentVersion, Target: $CURRENT_SEED_VERSION")
        Timber.d("🌱 SeedDataWorker started. Current version: $currentVersion, Target: $CURRENT_SEED_VERSION")

        if (currentVersion >= CURRENT_SEED_VERSION) {
            android.util.Log.d("SeedDataWorker", "✅ Seed already up to date (v$currentVersion), skipping")
            Timber.d("✅ Seed already up to date (v$currentVersion), skipping")
            return Result.success()
        }

        android.util.Log.d("SeedDataWorker", "📦 Starting seed process from v$currentVersion to v$CURRENT_SEED_VERSION...")
        Timber.d("📦 Starting seed process from v$currentVersion to v$CURRENT_SEED_VERSION...")
        return try {
            val rawJson = applicationContext.assets.open("seed_data.json")
                .bufferedReader().use { it.readText() }
            val seedData = json.decodeFromString<SeedData>(rawJson)

            // Hard-delete places that were removed from the seed in previous versions.
            // upsertAll never removes rows, so this explicit step is required.
            placeDao.deleteByIds(DELETED_PLACE_IDS)
            itineraryDao.deleteStopsByPlaceIds(DELETED_PLACE_IDS)
            itineraryDao.deleteStopsByItineraryIds(DELETED_ITINERARY_IDS)
            itineraryDao.deleteByIds(DELETED_ITINERARY_IDS)
            eventDao.deleteByIds(DELETED_EVENT_IDS)

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

            prefsDataStore.setEventsLastSyncedEpoch(System.currentTimeMillis())
            prefsDataStore.setIsSeeded(true)
            prefsDataStore.setSeedVersion(CURRENT_SEED_VERSION)
            android.util.Log.d("SeedDataWorker", "✅ Seed completed successfully: ${seedData.places.size} places, ${seedData.itineraries.size} itineraries, ${seedData.events.size} events (v$CURRENT_SEED_VERSION)")
            Timber.d("✅ Seed completed successfully: ${seedData.places.size} places, ${seedData.itineraries.size} itineraries, ${seedData.events.size} events (v$CURRENT_SEED_VERSION)")
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
        const val CURRENT_SEED_VERSION = 421

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
            "bodega-trevin",
            "restaurant-mola",
            "restaurant-juanito",
            "kayak-soller",
            "gtrentals-motorworld",
            "gem-mirador-sestaca",
            "punta-negra-sant-vicenc",
            // Duplicados retirados de Firestore y del seed (2026-10-01): 13 gemelos + estudi-miro.
            "can-prunera",
            "palau-solleric",
            "la-seu",
            "museu-mallorca-cap-de-bou",
            "son-marroig-deia",
            "puig-de-maria-pollenca",
            "puig-de-lofre",
            "coves-del-drach",
            "torre-de-ses-animes",
            "santuari-de-lluc",
            "far-cap-de-ses-salnes",
            "es-baluard-interior",
            "port-de-valldemossa",
            "estudi-miro",
            // Duplicados/limpieza v82 (2026-10-02): se conserva la gema o la ficha BEACH correcta.
            "mirador-ses-animes",      // = gem-torre-verger (gema)
            "llucalcari",              // = cala-llucalcari (gema); además mal ubicado
            "cala-mondrago",           // = cala-mondrag (se conserva la playa BEACH)
            "llaut-cala-sant-vicenc",  // un llaüt es una embarcación, no un lugar
            "sant-elm",                // = playa-sant-elm (ya existe la playa)
            // Duplicados v83 (2026-10-02): se conserva la ficha más completa.
            "castell-dalaro",          // = castell-alaro (se conserva el de HIKING, mucho más completo)
            "port-de-manacor",         // = porto-cristo (mismo núcleo: Porto Cristo es el puerto de Manacor)
            // Unificación v87 (2026-10-03): mismo lugar, se conserva la cumbre.
            "torrent-de-lofre",        // = puig-ofre (Torrent y Puig de l'Ofre son el mismo sitio)
            // Unificación v89 (2026-10-05): mismo lugar (la cima del Puig de Maria), se conserva puig-de-maria.
            "pollenca-des-del-puig-de-maria",  // = puig-de-maria (la vista de Pollença desde la cima)
        )

        // Itineraries removed from seed_data.json that must be deleted from the local DB.
        val DELETED_ITINERARY_IDS = listOf(
            "itin-gastronomy",
            "itin-lrt-paseo-maritimo",
            "itin-lrt-tramuntana-oeste",
            "itin-lrt-tramuntana-norte",
            "itin-lrt-sierra-tramuntana",
            // Fusionado como variante "norte" de itin-sup-portals-vells (alias en NavHost).
            "itin-sup-portals-vells-nord",
        )

        // Events removed from seed_data.json (past events, duplicates) that must be
        // deleted from the local DB — upsertAll never removes rows on its own.
        val DELETED_EVENT_IDS = listOf(
            "event-mallorca-live-2026",
            "event-sant-joan-2026",
            "event-triatlon-alcudia-2026",
            "event-copa-rey-vela-2026",
            "event-es-vermar-binissalem-2026", // duplicate of event-vermar-binissalem-2026
        )

        // NOTE: "disc-gtrentals-2026" was archived out of seed_data.json along with
        // gtrentals-motorworld and its itin-lrt-* itineraries above, but there is no
        // DELETED_DISCOUNT_IDS mechanism — discountDao only ever upserts, never deletes.
        // Existing installs will keep this orphaned discount row until a deletion
        // path for discounts is added.
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
        val source_name: String? = null,
        val heroFocalY: Float? = null,
        val caption: String? = null,
        val caption_es: String? = null,
        val caption_de: String? = null,
    )

    @Serializable data class SeedBeachFeatures(
        val surface: String? = null,
        val shade: Boolean? = null,
        val showers: Boolean? = null,
        val food: Boolean? = null,
        val lifeguard: Boolean? = null,
        val accessible: Boolean? = null,
        val parking: Boolean? = null,
        val pets: String? = null,
        val pets_note: String? = null,
        val pets_note_es: String? = null,
        val pets_note_de: String? = null,
        val no_services_confirmed: Boolean? = null,
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
        val gem_access: List<String> = emptyList(),
        val marker_type: String? = null,
        val beach_features: SeedBeachFeatures? = null,
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
                markerType = marker_type?.uppercase(),
                beachFeaturesJson = beach_features?.let {
                    Json.encodeToString(SeedBeachFeatures.serializer(), it)
                },
                gemAccessJson = Json.encodeToString(ListSerializer(String.serializer()), gem_access),
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
        val tramuntana_note: String = "", val tramuntana_note_es: String = "", val tramuntana_note_de: String = "", val cave_entry_max_wave_m: Float? = null,
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
        val order: Int = 0, val name: String = "", val name_es: String = "", val name_de: String = "", val role: String = "WAYPOINT",
        val lat: Double = 0.0, val lng: Double = 0.0,
        val note: String = "", val note_es: String = "", val note_de: String = "",
        val distance_from_prev_km: Float = 0f, val conditional: Boolean = false,
        val condition_note: String = "", val condition_note_es: String = "", val condition_note_de: String = "",
        val type: String = "OTHER",
    )

    @Serializable data class SeedPolylinePoint(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
    )

    @Serializable data class SeedRouteVariant(
        val id: String = "",
        val label: String = "",
        val label_es: String = "",
        val label_de: String = "",
        val polyline_points: List<SeedPolylinePoint> = emptyList(),
        val distance_km: Float? = null,
        val difficulty: String? = null,
        val duration_hours: String? = null,
        val color: String? = null,
    )

    @Serializable data class SeedItinerary(
        val id: String, val title: String, val description: String = "",
        val category: String, val duration_days: Int = 1,
        val duration_hours: String? = null,
        val difficulty: String? = null, val cover_photo: SeedPhotoUrl = SeedPhotoUrl(""),
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
        val route_polyline_points: List<SeedPolylinePoint> = emptyList(),
        val gallery_photos: List<SeedPhotoUrl> = emptyList(),
        val route_track_reviewed: Boolean = false,
        val route_variants: List<SeedRouteVariant> = emptyList(),
        val route_type: String? = null,
        val ascent_m: Int? = null,
        val descent_m: Int? = null,
        val access_warning: String? = null,
        val access_warning_es: String? = null,
        val access_warning_de: String? = null,
    ) {
        fun toEntity(): ItineraryEntity {
            val j = Json
            val translationsJson = if (translations.isEmpty()) "{}" else
                j.encodeToString(MapSerializer(String.serializer(), SeedTranslation.serializer()), translations)
            return ItineraryEntity(
                id = id, title = title, description = description,
                category = category.uppercase(), durationDays = duration_days,
                difficulty = difficulty?.uppercase(),
                coverPhotoUrl = j.encodeToString(SeedPhotoUrl.serializer(), cover_photo),
                totalDistanceKm = total_distance_km,
                highlightsJson = j.encodeToString(ListSerializer(String.serializer()), highlights),
                bestSeasonsJson = j.encodeToString(ListSerializer(String.serializer()), best_seasons),
                tagsJson = j.encodeToString(ListSerializer(String.serializer()), tags),
                translationsJson = translationsJson,
                weatherConfigJson = weather_config?.let { j.encodeToString(SeedWeatherConfig.serializer(), it) },
                qrEntryPointJson = qr_entry_point?.let { j.encodeToString(SeedQrEntryPoint.serializer(), it) },
                commercialBlockJson = commercial_block?.let { j.encodeToString(SeedCommercialBlock.serializer(), it) },
                routeWaypointsJson = j.encodeToString(ListSerializer(SeedRouteWaypoint.serializer()), route_waypoints),
                routePolylinePointsJson = j.encodeToString(ListSerializer(SeedPolylinePoint.serializer()), route_polyline_points),
                galleryPhotosJson = j.encodeToString(ListSerializer(SeedPhotoUrl.serializer()), gallery_photos),
                routeTrackReviewed = route_track_reviewed,
                durationHoursText = duration_hours,
                routeVariantsJson = j.encodeToString(ListSerializer(SeedRouteVariant.serializer()), route_variants),
                routeType = route_type,
                ascentM = ascent_m,
                descentM = descent_m,
                accessWarning = access_warning,
                accessWarningEs = access_warning_es,
                accessWarningDe = access_warning_de,
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
        val website_url: String? = null,
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
                websiteUrl = website_url,
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
