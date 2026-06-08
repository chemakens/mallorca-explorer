package com.mallorca.explorer.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mallorca.explorer.core.data.database.dao.DiscountDao
import com.mallorca.explorer.core.data.database.dao.EventDao
import com.mallorca.explorer.core.data.database.dao.FavoriteDao
import com.mallorca.explorer.core.data.database.dao.HiddenGemDao
import com.mallorca.explorer.core.data.database.dao.ItineraryDao
import com.mallorca.explorer.core.data.database.dao.PlaceDao
import com.mallorca.explorer.core.data.database.dao.RecentlyViewedDao
import com.mallorca.explorer.core.data.database.dao.StopProgressDao
import com.mallorca.explorer.core.data.database.dao.UserTripDao
import com.mallorca.explorer.core.data.database.dao.VisitedPlaceDao
import com.mallorca.explorer.core.data.database.dao.WeatherDao
import com.mallorca.explorer.core.data.database.entity.DiscountEntity
import com.mallorca.explorer.core.data.database.entity.EventEntity
import com.mallorca.explorer.core.data.database.entity.FavoriteEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryEntity
import com.mallorca.explorer.core.data.database.entity.ItineraryStopEntity
import com.mallorca.explorer.core.data.database.entity.PlaceEntity
import com.mallorca.explorer.core.data.database.entity.RecentlyViewedEntity
import com.mallorca.explorer.core.data.database.entity.StopProgressEntity
import com.mallorca.explorer.core.data.database.entity.UnlockedGemEntity
import com.mallorca.explorer.core.data.database.entity.UserTripEntity
import com.mallorca.explorer.core.data.database.entity.UserTripStopEntity
import com.mallorca.explorer.core.data.database.entity.VisitedPlaceEntity
import com.mallorca.explorer.core.data.database.entity.WeatherCacheEntity

@Database(
    entities = [
        PlaceEntity::class,
        ItineraryEntity::class,
        ItineraryStopEntity::class,
        UserTripEntity::class,
        UserTripStopEntity::class,
        FavoriteEntity::class,
        WeatherCacheEntity::class,
        EventEntity::class,
        StopProgressEntity::class,
        UnlockedGemEntity::class,
        DiscountEntity::class,
        VisitedPlaceEntity::class,
        RecentlyViewedEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class MallorcaDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun userTripDao(): UserTripDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun weatherDao(): WeatherDao
    abstract fun eventDao(): EventDao
    abstract fun stopProgressDao(): StopProgressDao
    abstract fun hiddenGemDao(): HiddenGemDao
    abstract fun discountDao(): DiscountDao
    abstract fun visitedPlaceDao(): VisitedPlaceDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE itineraries ADD COLUMN weatherConfigJson TEXT")
                database.execSQL("ALTER TABLE itineraries ADD COLUMN qrEntryPointJson TEXT")
                database.execSQL("ALTER TABLE itineraries ADD COLUMN commercialBlockJson TEXT")
                database.execSQL("ALTER TABLE itineraries ADD COLUMN routeWaypointsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE places ADD COLUMN descriptionEs TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE places ADD COLUMN nameDe TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE places ADD COLUMN nameRu TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE places ADD COLUMN nameZh TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE places ADD COLUMN descriptionDe TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE places ADD COLUMN descriptionRu TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE places ADD COLUMN descriptionZh TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE places ADD COLUMN tipsEsJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE places ADD COLUMN tipsDeJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE places ADD COLUMN tipsRuJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE places ADD COLUMN tipsZhJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE itineraries ADD COLUMN galleryPhotosJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE events ADD COLUMN titleDe TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE events ADD COLUMN titleRu TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE events ADD COLUMN titleZh TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE events ADD COLUMN descriptionDe TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE events ADD COLUMN descriptionRu TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE events ADD COLUMN descriptionZh TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE discounts ADD COLUMN headlineEs TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN headlineDe TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN headlineRu TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN headlineZh TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN termsEs TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN termsDe TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN termsRu TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE discounts ADD COLUMN termsZh TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
