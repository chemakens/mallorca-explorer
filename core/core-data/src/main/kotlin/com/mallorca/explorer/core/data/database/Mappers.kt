package com.mallorca.explorer.core.data.database

import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity
import com.mallorca.explorer.core.domain.model.BeachFeatures
import com.mallorca.explorer.core.domain.model.BeachSurface
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.CommercialBlock
import com.mallorca.explorer.core.domain.model.PetPolicy
import com.mallorca.explorer.core.domain.model.Difficulty
import com.mallorca.explorer.core.domain.model.Favorite
import com.mallorca.explorer.core.domain.model.ImageSource
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.ItineraryStop
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.MarkerType
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.PlaceImage
import com.mallorca.explorer.core.domain.model.PriceLevel
import com.mallorca.explorer.core.domain.model.QrEntryPoint
import com.mallorca.explorer.core.domain.model.RouteType
import com.mallorca.explorer.core.domain.model.RouteVariant
import com.mallorca.explorer.core.domain.model.RouteWaypoint
import com.mallorca.explorer.core.domain.model.SupWeatherConfig
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.model.UserTripStop
import com.mallorca.explorer.core.domain.model.WaypointRole
import com.mallorca.explorer.core.domain.model.WaypointType
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
    val source_name: String? = null,
    val heroFocalY: Float? = null,
    val caption: String? = null,
    val caption_es: String? = null,
    val caption_de: String? = null,
)

private val placeImageListSerializer = ListSerializer(PlaceImageJson.serializer())

private fun PlaceImageJson.toDomain(): PlaceImage = PlaceImage(
    url,
    runCatching { ImageSource.valueOf(source.uppercase()) }.getOrDefault(ImageSource.OTHER),
    author, source_name, heroFocalY, caption, caption_es, caption_de,
)

private fun List<PlaceImage>.toPhotoJson(): String =
    json.encodeToString(placeImageListSerializer, map {
        PlaceImageJson(it.url, it.source.name, it.author, it.sourceName, it.heroFocalY, it.caption, it.captionEs, it.captionDe)
    })

private fun String.toPlaceImage(): PlaceImage = runCatching {
    json.decodeFromString<PlaceImageJson>(this).toDomain()
}.getOrElse { PlaceImage(url = this) }

private fun String.toPlaceImageList(): List<PlaceImage> = runCatching {
    json.decodeFromString(placeImageListSerializer, this).map { it.toDomain() }
}.getOrElse {
    runCatching { json.decodeFromString(strListSerializer, this).map { PlaceImage(url = it) } }
        .getOrDefault(emptyList())
}

internal fun List<String>.toPlaceImageJson(): String =
    json.encodeToString(placeImageListSerializer, map { PlaceImageJson(url = it) })

@Serializable
private data class BeachFeaturesJson(
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

private fun BeachFeatures.toJsonString(): String = json.encodeToString(
    BeachFeaturesJson.serializer(),
    BeachFeaturesJson(
        surface = surface?.name, shade = shade, showers = showers, food = food,
        lifeguard = lifeguard, accessible = accessible, parking = parking,
        pets = pets?.name, pets_note = petsNote, pets_note_es = petsNoteEs,
        pets_note_de = petsNoteDe, no_services_confirmed = noServicesConfirmed,
    ),
)

private fun String.toBeachFeatures(): BeachFeatures? = runCatching {
    json.decodeFromString(BeachFeaturesJson.serializer(), this).let { j ->
        BeachFeatures(
            surface = j.surface?.let { runCatching { BeachSurface.valueOf(it.uppercase()) }.getOrNull() },
            shade = j.shade, showers = j.showers, food = j.food,
            lifeguard = j.lifeguard, accessible = j.accessible, parking = j.parking,
            pets = j.pets?.let { runCatching { PetPolicy.valueOf(it.uppercase()) }.getOrNull() },
            petsNote = j.pets_note, petsNoteEs = j.pets_note_es, petsNoteDe = j.pets_note_de,
            noServicesConfirmed = j.no_services_confirmed,
        )
    }
}.getOrNull()

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
    val tramuntana_note: String = "", val tramuntana_note_es: String = "", val tramuntana_note_de: String = "", val cave_entry_max_wave_m: Float? = null,
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
private data class ItineraryPolylinePoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

@Serializable
private data class ItineraryRouteVariant(
    val id: String = "",
    val label: String = "",
    val label_es: String = "",
    val label_de: String = "",
    val polyline_points: List<ItineraryPolylinePoint> = emptyList(),
    val distance_km: Float? = null,
    val difficulty: String? = null,
    val duration_hours: String? = null,
    val color: String? = null,
)

private val routeVariantListSerializer = ListSerializer(ItineraryRouteVariant.serializer())

@Serializable
private data class ItineraryRouteWaypoint(
    val order: Int = 0, val name: String = "", val name_es: String = "", val name_de: String = "", val role: String = "WAYPOINT",
    val lat: Double = 0.0, val lng: Double = 0.0,
    val note: String = "", val note_es: String = "", val note_de: String = "",
    val distance_from_prev_km: Float = 0f, val conditional: Boolean = false,
    val condition_note: String = "", val condition_note_es: String = "", val condition_note_de: String = "",
    val type: String = "OTHER",
)

private val waypointListSerializer = ListSerializer(ItineraryRouteWaypoint.serializer())
private val polylinePointListSerializer = ListSerializer(ItineraryPolylinePoint.serializer())

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
    category = runCatching { Category.valueOf(category) }.getOrElse { Category.CULTURE },
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
    markerType = markerType?.let { runCatching { MarkerType.valueOf(it) }.getOrNull() },
    beachFeatures = beachFeaturesJson?.toBeachFeatures(),
    gemAccess = gemAccessJson.toStringList(),
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
    markerType = markerType?.name,
    beachFeaturesJson = beachFeatures?.toJsonString(),
    gemAccessJson = gemAccess.toJson(),
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
        category = runCatching { Category.valueOf(category) }.getOrElse { Category.CULTURE },
        durationDays = durationDays,
        durationHoursText = durationHoursText,
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
        coverPhoto = coverPhotoUrl.toPlaceImage(),
        galleryPhotos = galleryPhotosJson.toPlaceImageList(),
        totalDistanceKm = totalDistanceKm,
        highlights = t?.highlights ?: highlightsJson.toStringList(),
        bestSeasons = bestSeasonsJson.toStringList(),
        tags = tagsJson.toStringList(),
        weatherConfig = weatherConfigJson?.let {
            runCatching { json.decodeFromString<ItineraryWeatherConfig>(it) }.getOrNull()
        }?.let { c ->
            SupWeatherConfig(c.fetch_lat, c.fetch_lng, c.beach_facing_deg, c.max_safe_wind_knots,
                c.max_safe_gust_knots, c.tramuntana_warning, c.tramuntana_note, c.tramuntana_note_es, c.tramuntana_note_de,
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
                RouteWaypoint(w.order, w.name, w.name_es, w.name_de, runCatching { WaypointRole.valueOf(w.role) }.getOrDefault(WaypointRole.WAYPOINT),
                    w.lat, w.lng, w.note, w.note_es, w.note_de, w.distance_from_prev_km, w.conditional,
                    w.condition_note, w.condition_note_es, w.condition_note_de,
                    runCatching { WaypointType.valueOf(w.type) }.getOrDefault(WaypointType.OTHER))
            },
        routePolylinePoints = runCatching { json.decodeFromString(polylinePointListSerializer, routePolylinePointsJson) }
            .getOrDefault(emptyList()).map { p -> LatLng(p.lat, p.lng) },
        routeTrackReviewed = routeTrackReviewed,
        routeVariants = runCatching { json.decodeFromString(routeVariantListSerializer, routeVariantsJson) }
            .getOrDefault(emptyList()).map { v ->
                RouteVariant(
                    id = v.id,
                    label = v.label,
                    labelEs = v.label_es,
                    labelDe = v.label_de,
                    polylinePoints = v.polyline_points.map { p -> LatLng(p.lat, p.lng) },
                    distanceKm = v.distance_km,
                    difficulty = v.difficulty?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() },
                    durationHoursText = v.duration_hours,
                    color = v.color,
                )
            },
        routeType = routeType?.let { runCatching { RouteType.valueOf(it) }.getOrNull() },
        ascentM = ascentM,
        descentM = descentM,
        accessWarning = accessWarning,
        accessWarningEs = accessWarningEs,
        accessWarningDe = accessWarningDe,
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
