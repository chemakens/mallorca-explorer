package com.mallorca.explorer.core.data.repository

import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.toDomain
import com.mallorca.explorer.core.data.network.MallorcaApiService
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val apiService: MallorcaApiService,
    private val json: Json,
) : PlaceRepository {

    private val strListSerializer = ListSerializer(String.serializer())
    private fun List<String>.toJson() = json.encodeToString(strListSerializer, this)

    override fun getAllPlaces(): Flow<List<Place>> =
        placeDao.getAllPlaces().map { entities -> entities.map { it.toDomain() } }

    override fun getPlacesByCategory(category: Category): Flow<List<Place>> =
        placeDao.getPlacesByCategory(category.name).map { entities -> entities.map { it.toDomain() } }

    override fun getPlaceById(id: String): Flow<Place?> =
        placeDao.getPlaceById(id).map { it?.toDomain() }

    override fun searchPlaces(query: String): Flow<List<Place>> =
        placeDao.searchPlaces(query).map { entities -> entities.map { it.toDomain() } }

    override fun getNearbyPlaces(center: LatLng, radiusKm: Double): Flow<List<Place>> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(center.latitude)))
        return placeDao.getPlacesInBoundingBox(
            latMin = center.latitude - latDelta,
            latMax = center.latitude + latDelta,
            lngMin = center.longitude - lngDelta,
            lngMax = center.longitude + lngDelta,
        ).map { entities ->
            entities.map { it.toDomain() }
                .filter { haversineKm(center, it.location) <= radiusKm }
                .sortedBy { haversineKm(center, it.location) }
        }
    }

    override suspend fun refreshPlaces() {
        try {
            val dtos = apiService.getPlaces()
            val nowEpoch = System.currentTimeMillis()
            placeDao.upsertAll(dtos.map { dto ->
                com.mallorca.explorer.core.data.database.entity.PlaceEntity(
                    id = dto.id,
                    name = dto.name,
                    nameEs = dto.nameEs,
                    nameDe = dto.nameDe,
                    nameRu = dto.nameRu,
                    nameZh = dto.nameZh,
                    description = dto.description,
                    descriptionEs = dto.descriptionEs,
                    descriptionDe = dto.descriptionDe,
                    descriptionRu = dto.descriptionRu,
                    descriptionZh = dto.descriptionZh,
                    category = dto.category.uppercase(),
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    address = dto.address,
                    municipality = dto.municipality,
                    photoUrlsJson = json.encodeToString(strListSerializer, dto.photoUrls),
                    thumbnailUrl = dto.thumbnailUrl,
                    openingHoursJson = null,
                    priceLevel = dto.priceLevel,
                    rating = dto.rating,
                    reviewCount = dto.reviewCount,
                    tipsJson = json.encodeToString(strListSerializer, dto.tips),
                    tipsEsJson = json.encodeToString(strListSerializer, dto.tipsEs),
                    tipsDeJson = json.encodeToString(strListSerializer, dto.tipsDe),
                    tipsRuJson = json.encodeToString(strListSerializer, dto.tipsRu),
                    tipsZhJson = json.encodeToString(strListSerializer, dto.tipsZh),
                    website = dto.website,
                    phoneNumber = dto.phoneNumber,
                    ticketUrl = dto.ticketUrl,
                    tagsJson = json.encodeToString(strListSerializer, dto.tags),
                    lastUpdatedEpoch = nowEpoch,
                )
            })
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh places from network")
        }
    }

    private fun haversineKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
