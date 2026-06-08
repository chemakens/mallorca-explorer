package com.mallorca.explorer.core.data.di

import android.content.Context
import androidx.room.Room
import com.mallorca.explorer.core.data.database.MallorcaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MallorcaDatabase =
        Room.databaseBuilder(context, MallorcaDatabase::class.java, "mallorca_explorer.db")
            .addMigrations(MallorcaDatabase.MIGRATION_8_9, MallorcaDatabase.MIGRATION_9_10, MallorcaDatabase.MIGRATION_10_11, MallorcaDatabase.MIGRATION_11_12, MallorcaDatabase.MIGRATION_12_13, MallorcaDatabase.MIGRATION_13_14)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePlaceDao(db: MallorcaDatabase) = db.placeDao()
    @Provides fun provideItineraryDao(db: MallorcaDatabase) = db.itineraryDao()
    @Provides fun provideUserTripDao(db: MallorcaDatabase) = db.userTripDao()
    @Provides fun provideFavoriteDao(db: MallorcaDatabase) = db.favoriteDao()
    @Provides fun provideWeatherDao(db: MallorcaDatabase): com.mallorca.explorer.core.data.database.dao.WeatherDao = db.weatherDao()
    @Provides fun provideEventDao(db: MallorcaDatabase) = db.eventDao()
    @Provides fun provideStopProgressDao(db: MallorcaDatabase) = db.stopProgressDao()
    @Provides fun provideHiddenGemDao(db: MallorcaDatabase) = db.hiddenGemDao()
    @Provides fun provideDiscountDao(db: MallorcaDatabase) = db.discountDao()
    @Provides fun provideVisitedPlaceDao(db: MallorcaDatabase) = db.visitedPlaceDao()
    @Provides fun provideRecentlyViewedDao(db: MallorcaDatabase) = db.recentlyViewedDao()
}
