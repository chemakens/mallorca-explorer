package com.mallorca.explorer.core.data.database

import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.CommercialBlock
import com.mallorca.explorer.core.domain.model.Difficulty
import com.mallorca.explorer.core.domain.model.Favorite
import com.mallorca.explorer.core.domain.model.ImageSource
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.ItineraryStop
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.PlaceImage
import com.mallorca.explorer.core.domain.model.PriceLevel
import com.mallorca.explorer.core.domain.model.QrEntryPoint
import com.mallorca.explorer.core.domain.model.RouteWaypoint
import com.mallorca.explorer.core.domain.model.SupWeatherConfig
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.model.UserTripStop
import com.mallorca.explorer.core.domain.model.WaypointRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Locale

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
private val strListSerializer = ListSerializer(String.serializer())
private fun List<String>.toJson() = json.encodeToString(strListSerializer, this)
private fun String.toStringList(): List<String> = json.decodeFromString(strListSerializer, this)

@Serializable
private data class PlaceImageJson(
    val url: String,
    val source: String = "OTHER",
    val author: String? = null,
)

private val placeImageListSerializer = ListSerializer(PlaceImageJson.serializer())

private fun List<PlaceImage>.toPhotoJson(): String =
    json.encodeToString(placeImageListSerializer, map { PlaceImageJson(it.url, it.source.name, it.author) })

private fun String.toPlaceImageList(): List<PlaceImage> = runCatching {
    json.decodeFromString(placeImageListSerializer, this).map {
        PlaceImage(it.url, runCatching { ImageSource.valueOf(it.source.uppercase()) }.getOrDefault(ImageSource.OTHER), it.author)
    }
}.getOrElse {
    runCatching { json.decodeFromString(strListSerializer, this).map { PlaceImage(url = it) } }
        .getOrDefault(emptyList())
}

internal fun List<String>.toPlaceImageJson(): String =
    json.encodeToString(placeImageListSerializer, map { PlaceImageJson(url = it) })

@Serializable
private data class ItineraryTranslation(
    val title: String? = null,
    val description: String? = null,
    val highlights: List<String>? = null,
)

private val translationMapSerializer = MapSerializer(String.serializer(), ItineraryTranslation.serializer())

@Serializable
private data class ItineraryWeatherConfig(
    val fetch_lat: Double = 0.0, val fetch_lng: Double = 0.0,
    val beach_facing_deg: Int = 0, val max_safe_wind_knots: Float = 10f,
    val max_safe_gust_knots: Float = 14f, val tramuntana_warning: Boolean = false,
    val tramuntana_note_es: String = "", val cave_entry_max_wave_m: Float? = null,
    val cave_entry_max_wind_knots: Float? = null,
)

@Serializable
private data class ItineraryQrEntryPoint(
    val qr_id: String = "", val source_beach: String = "",
    val deep_link: String = "", val show_weather_block_on_entry: Boolean = false,
)

@Serializable
private data class ItineraryCommercialBlock(
    val enabled: Boolean = false, val partner_name: String = "",
    val cta_label_es: String = "", val phone: String = "", val whatsapp: String = "",
    val partner_lat: Double = 0.0, val partner_lng: Double = 0.0,
    val discount_code: String = "", val discount_pct: Int = 0, val discount_note_es: String = "",
)

@Serializable
private data class ItineraryRouteWaypoint(
    val order: Int = 0, val name: String = "", val role: String = "WAYPOINT",
    val lat: Double = 0.0, val lng: Double = 0.0, val note_es: String = "",
    val distance_from_prev_km: Float = 0f, val conditional: Boolean = false,
    val condition_note_es: String = "",
)

private val waypointListSerializer = ListSerializer(ItineraryRouteWaypoint.serializer())

private fun parseTranslations(json_str: String): Map<String, ItineraryTranslation> =
    runCatching { json.decodeFromString(translationMapSerializer, json_str) }.getOrDefault(emptyMap())

fun PlaceEntity.toDomain(): Place = Place(
    id = id,
    name = name,
    nameEs = nameEs,
    description = description,
    descriptionEs = descriptionEs,
    nameDe = nameDe,
    nameRu = nameRu,
    nameZh = nameZh,
    descriptionDe = descriptionDe,
    descriptionRu = descriptionRu,
    descriptionZh = descriptionZh,
    category = Category.valueOf(category),
    subCategories = tagsJson.toStringList(),
    location = LatLng(latitude, longitude),
    address = address,
    municipality = municipality,
    photoUrls = photoUrlsJson.toPlaceImageList(),
    thumbnailUrl = thumbnailUrl,
    priceLevel = priceLevel?.let { runCatching { PriceLevel.valueOf(it) }.getOrNull() },
    rating = rating,
    reviewCount = reviewCount,
    tips = tipsJson.toStringList(),
    tipsEs = tipsEsJson.toStringList(),
    tipsDe = tipsDeJson.toStringList(),
    tipsRu = tipsRuJson.toStringList(),
    tipsZh = tipsZhJson.toStringList(),
    website = website,
    phoneNumber = phoneNumber,
    ticketUrl = ticketUrl,
    lastUpdated = Instant.ofEpochMilli(lastUpdatedEpoch),
)

fun Place.toEntity(): PlaceEntity = PlaceEntity(
    id = id,
    name = name,
    nameEs = nameEs,
    description = description,
    descriptionEs = descriptionEs,
    nameDe = nameDe,
    nameRu = nameRu,
    nameZh = nameZh,
    descriptionDe = descriptionDe,
    descriptionRu = descriptionRu,
    descriptionZh = descriptionZh,
    tipsEsJson = tipsEs.toJson(),
    tipsDeJson = tipsDe.toJson(),
    tipsRuJson = tipsRu.toJson(),
    tipsZhJson = tipsZh.toJson(),
    category = category.name,
    latitude = location.latitude,
    longitude = location.longitude,
    address = address,
    municipality = municipality,
    photoUrlsJson = photoUrls.toPhotoJson(),
    thumbnailUrl = thumbnailUrl,
    openingHoursJson = null,
    priceLevel = priceLevel?.name,
    rating = rating,
    reviewCount = reviewCount,
    tipsJson = tips.toJson(),
    website = website,
    phoneNumber = phoneNumber,
    ticketUrl = ticketUrl,
    tagsJson = subCategories.toJson(),
    lastUpdatedEpoch = lastUpdated.toEpochMilli(),
)

fun ItineraryEntity.toDomain(
    stops: List<Pair<ItineraryStopEntity, Place>>,
    locale: String = Locale.getDefault().language,
): Itinerary {
    val translations = parseTranslations(translationsJson)
    val t = translations[locale]
    return Itinerary(
        id = id,
        title = t?.title ?: title,
        description = t?.description ?: description,
        category = Category.valueOf(category),
        durationDays = durationDays,
        difficulty = difficulty?.let { Difficulty.valueOf(it) },
        places = stops.map { (stop, place) ->
            val localizedNote = when (locale) {
                "es" -> stop.noteEs ?: stop.note
                "de" -> stop.noteDe ?: stop.note
                else -> stop.note
            }
            ItineraryStop(
                place = place,
                order = stop.order,
                suggestedDurationMinutes = stop.suggestedDurationMinutes,
                note = localizedNote,
                dayNumber = stop.dayNumber,
            )
        },
        coverPhotoUrl = coverPhotoUrl,
        galleryPhotoUrls = galleryPhotosJson.toStringList(),
        totalDistanceKm = totalDistanceKm,
        highlights = t?.highlights ?: highlightsJson.toStringList(),
        bestSeasons = bestSeasonsJson.toStringList(),
        tags = tagsJson.toStringList(),
        weatherConfig = weatherConfigJson?.let {
            runCatching { json.decodeFromString<ItineraryWeatherConfig>(it) }.getOrNull()
        }?.let { c ->
            SupWeatherConfig(c.fetch_lat, c.fetch_lng, c.beach_facing_deg, c.max_safe_wind_knots,
                c.max_safe_gust_knots, c.tramuntana_warning, c.tramuntana_note_es,
                c.cave_entry_max_wave_m, c.cave_entry_max_wind_knots)
        },
        qrEntryPoint = qrEntryPointJson?.let {
            runCatching { json.decodeFromString<ItineraryQrEntryPoint>(it) }.getOrNull()
        }?.let { q -> QrEntryPoint(q.qr_id, q.source_beach, q.deep_link, q.show_weather_block_on_entry) },
        commercialBlock = commercialBlockJson?.let {
            runCatching { json.decodeFromString<ItineraryCommercialBlock>(it) }.getOrNull()
        }?.let { b ->
            CommercialBlock(b.enabled, b.partner_name, b.cta_label_es, b.phone, b.whatsapp,
                b.partner_lat, b.partner_lng, b.discount_code, b.discount_pct, b.discount_note_es)
        },
        routeWaypoints = runCatching { json.decodeFromString(waypointListSerializer, routeWaypointsJson) }
            .getOrDefault(emptyList()).map { w ->
                RouteWaypoint(w.order, w.name, runCatching { WaypointRole.valueOf(w.role) }.getOrDefault(WaypointRole.WAYPOINT),
                    w.lat, w.lng, w.note_es, w.distance_from_prev_km, w.conditional, w.condition_note_es)
            },
    )
}

fun UserTripEntity.toDomain(stops: List<Pair<UserTripStopEntity, Place>>): UserTrip = UserTrip(
    id = id,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAtEpoch),
    updatedAt = Instant.ofEpochMilli(updatedAtEpoch),
    stops = stops.map { (stop, place) ->
        UserTripStop(
            place = place,
            order = stop.order,
            userNote = stop.userNote,
            suggestedDurationMinutes = stop.suggestedDurationMinutes,
            isVisited = stop.isVisited,
            visitedAt = stop.visitedAtEpoch?.let { Instant.ofEpochMilli(it) },
        )
    },
    notes = notes,
    isSynced = isSynced,
)

fun FavoriteEntity.toDomain(): Favorite = Favorite(
    placeId = placeId,
    savedAt = Instant.ofEpochMilli(savedAtEpoch),
)
