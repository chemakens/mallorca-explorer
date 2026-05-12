package com.mallorca.explorer.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mallorca.explorer.core.data.database.dao.FavoriteDao
import com.mallorca.explorer.core.data.database.dao.ItineraryDao
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.dao.UserTripDao
import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity

@Database(
    entities = [
        PlaceEntity::class,
        ItineraryEntity::class,
        ItineraryStopEntity::class,
        UserTripEntity::class,
        UserTripStopEntity::class,
        FavoriteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MallorcaDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun userTripDao(): UserTripDao
    abstract fun favoriteDao(): FavoriteDao
}
