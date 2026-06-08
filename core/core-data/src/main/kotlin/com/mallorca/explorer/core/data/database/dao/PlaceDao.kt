package com.mallorca.explorer.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name ASC")
    fun getAllPlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE category = :category ORDER BY rating DESC")
    fun getPlacesByCategory(category: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    fun getPlaceById(id: String): Flow<PlaceEntity?>

    @Query("""
        SELECT * FROM places
        WHERE name LIKE '%' || :query || '%'
           OR nameEs LIKE '%' || :query || '%'
           OR nameDe LIKE '%' || :query || '%'
           OR nameRu LIKE '%' || :query || '%'
           OR nameZh LIKE '%' || :query || '%'
           OR municipality LIKE '%' || :query || '%'
        ORDER BY rating DESC
        LIMIT 50
    """)
    fun searchPlaces(query: String): Flow<List<PlaceEntity>>

    @Upsert
    suspend fun upsertAll(places: List<PlaceEntity>)

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("""
        SELECT * FROM places
        WHERE latitude  BETWEEN :latMin AND :latMax
          AND longitude BETWEEN :lngMin AND :lngMax
        ORDER BY rating DESC
    """)
    fun getPlacesInBoundingBox(
        latMin: Double, latMax: Double,
        lngMin: Double, lngMax: Double,
    ): Flow<List<PlaceEntity>>

    @Query("DELETE FROM places WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int
}
