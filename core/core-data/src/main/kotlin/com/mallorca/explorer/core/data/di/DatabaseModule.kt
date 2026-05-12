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
            .build()

    @Provides fun providePlaceDao(db: MallorcaDatabase) = db.placeDao()
    @Provides fun provideItineraryDao(db: MallorcaDatabase) = db.itineraryDao()
    @Provides fun provideUserTripDao(db: MallorcaDatabase) = db.userTripDao()
    @Provides fun provideFavoriteDao(db: MallorcaDatabase) = db.favoriteDao()
}
