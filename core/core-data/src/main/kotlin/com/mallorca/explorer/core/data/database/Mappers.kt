package com.mallorca.explorer.core.data.database

import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Difficulty
import com.mallorca.explorer.core.domain.model.Favorite
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.ItineraryStop
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.PriceLevel
import com.mallorca.explorer.core.domain.model.UserTrip
import com.mallorca.explorer.core.domain.model.UserTripStop
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }
private val strListSerializer = ListSerializer(String.serializer())
private fun List<String>.toJson() = json.encodeToString(strListSerializer, this)
private fun String.toStringList(): List<String> = json.decodeFromString(strListSerializer, this)

fun PlaceEntity.toDomain(): Place = Place(
    id = id,
    name = name,
    nameEs = nameEs,
    description = description,
    category = Category.valueOf(category),
    subCategories = tagsJson.toStringList(),
    location = LatLng(latitude, longitude),
    address = address,
    municipality = municipality,
    photoUrls = photoUrlsJson.toStringList(),
    thumbnailUrl = thumbnailUrl,
    priceLevel = priceLevel?.let { PriceLevel.valueOf(it) },
    rating = rating,
    reviewCount = reviewCount,
    tips = tipsJson.toStringList(),
    website = website,
    phoneNumber = phoneNumber,
    lastUpdated = Instant.ofEpochMilli(lastUpdatedEpoch),
)

fun Place.toEntity(): PlaceEntity = PlaceEntity(
    id = id,
    name = name,
    nameEs = nameEs,
    description = description,
    category = category.name,
    latitude = location.latitude,
    longitude = location.longitude,
    address = address,
    municipality = municipality,
    photoUrlsJson = photoUrls.toJson(),
    thumbnailUrl = thumbnailUrl,
    openingHoursJson = null,
    priceLevel = priceLevel?.name,
    rating = rating,
    reviewCount = reviewCount,
    tipsJson = tips.toJson(),
    website = website,
    phoneNumber = phoneNumber,
    tagsJson = subCategories.toJson(),
    lastUpdatedEpoch = lastUpdated.toEpochMilli(),
)

fun ItineraryEntity.toDomain(stops: List<Pair<ItineraryStopEntity, Place>>): Itinerary = Itinerary(
    id = id,
    title = title,
    description = description,
    category = Category.valueOf(category),
    durationDays = durationDays,
    difficulty = difficulty?.let { Difficulty.valueOf(it) },
    places = stops.map { (stop, place) ->
        ItineraryStop(
            place = place,
            order = stop.order,
            suggestedDurationMinutes = stop.suggestedDurationMinutes,
            note = stop.note,
            dayNumber = stop.dayNumber,
        )
    },
    coverPhotoUrl = coverPhotoUrl,
    totalDistanceKm = totalDistanceKm,
    highlights = highlightsJson.toStringList(),
    bestSeasons = bestSeasonsJson.toStringList(),
    tags = tagsJson.toStringList(),
)

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
