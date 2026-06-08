package com.mallorca.explorer.core.data.repository

import android.content.Context
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.database.toDomain
import com.mallorca.explorer.core.data.database.toEntity
import com.mallorca.explorer.core.data.network.MallorcaApiService
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.model.PlaceModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Serializable
private data class SeedWrapper(val places: List<PlaceSeedDto>)

@Serializable
private data class PlaceSeedDto(
    val id: String,
    val name: String,
    @SerialName("name_es") val nameEs: String = "",
    val description: String = "",
    val category: String = "BEACH",
    val latitude: Double,
    val longitude: Double,
    val municipality: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    val rating: Float? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
    val tips: List<String> = emptyList(),
    @SerialName("price_level") val priceLevel: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    val address: String? = null,
    val website: String? = null,
    @SerialName("ticket_url") val ticketUrl: String? = null,
    val tags: List<String> = emptyList(),
)

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val apiService: MallorcaApiService,
    @ApplicationContext private val context: Context,
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

    override fun getNearbyPlaces(center: LatLng, radiusKm: Double): Flow<List<Place>> =
        placeDao.getAllPlaces().map { entities ->
            entities.map { it.toDomain() }
                .filter { haversineKm(center, it.location) <= radiusKm }
                .sortedBy { haversineKm(center, it.location) }
        }

    override suspend fun refreshPlaces() {
        try {
            val dtos = apiService.getPlaces()
            placeDao.upsertAll(dtos.map { dto ->
                Place(
                    id = dto.id,
                    name = dto.name,
                    nameEs = dto.nameEs,
                    description = dto.description,
                    category = Category.valueOf(dto.category.uppercase()),
                    location = LatLng(dto.latitude, dto.longitude),
                    address = dto.address,
                    municipality = dto.municipality,
                    photoUrls = dto.photoUrls,
                    thumbnailUrl = dto.thumbnailUrl,
                    rating = dto.rating,
                    reviewCount = dto.reviewCount,
                    tips = dto.tips,
                    website = dto.website,
                    phoneNumber = dto.phoneNumber,
                ).toEntity()
            })
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh places from network")
        }
    }
    override suspend fun loadPlacesFromAssets(): List<PlaceModel> {
        if (placeDao.count() > 0) return emptyList()
        return try {
            context.assets.open("seed_data.json").use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val seed = json.decodeFromString<SeedWrapper>(jsonString)
                val entities = seed.places.map { dto ->
                    PlaceEntity(
                        id = dto.id,
                        name = dto.name,
                        nameEs = dto.nameEs,
                        description = dto.description,
                        category = dto.category,
                        latitude = dto.latitude,
                        longitude = dto.longitude,
                        address = dto.address,
                        municipality = dto.municipality,
                        photoUrlsJson = dto.photoUrls.toJson(),
                        thumbnailUrl = dto.thumbnailUrl,
                        openingHoursJson = null,
                        priceLevel = dto.priceLevel,
                        rating = dto.rating,
                        reviewCount = dto.reviewCount,
                        tipsJson = dto.tips.toJson(),
                        website = dto.website,
                        phoneNumber = null,
                        ticketUrl = dto.ticketUrl,
                        tagsJson = dto.tags.toJson(),
                        lastUpdatedEpoch = System.currentTimeMillis(),
                    )
                }
                placeDao.upsertAll(entities)
                Timber.d("PlacesRepo: seeded ${entities.size} places from assets")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "PlacesRepo: error loading from assets")
            emptyList()
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
