package com.mallorca.explorer.core.data.di

import com.mallorca.explorer.core.common.DefaultDispatcher
import com.mallorca.explorer.core.common.IoDispatcher
import com.mallorca.explorer.core.common.MainDispatcher
import com.mallorca.explorer.core.data.repository.DiscountRepositoryImpl
import com.mallorca.explorer.core.data.repository.RecentlyViewedRepositoryImpl
import com.mallorca.explorer.core.data.repository.VisitedPlaceRepositoryImpl
import com.mallorca.explorer.core.data.repository.EventRepositoryImpl
import com.mallorca.explorer.core.data.repository.FavoriteRepositoryImpl
import com.mallorca.explorer.core.data.repository.HiddenGemRepositoryImpl
import com.mallorca.explorer.core.data.repository.ItineraryRepositoryImpl
import com.mallorca.explorer.core.data.repository.PlaceRepositoryImpl
import com.mallorca.explorer.core.data.repository.StopProgressRepositoryImpl
import com.mallorca.explorer.core.data.repository.UserTripRepositoryImpl
import com.mallorca.explorer.core.data.repository.WeatherRepositoryImpl
import com.mallorca.explorer.core.domain.repository.DiscountRepository
import com.mallorca.explorer.core.domain.repository.RecentlyViewedRepository
import com.mallorca.explorer.core.domain.repository.VisitedPlaceRepository
import com.mallorca.explorer.core.domain.repository.EventRepository
import com.mallorca.explorer.core.domain.repository.FavoriteRepository
import com.mallorca.explorer.core.domain.repository.HiddenGemRepository
import com.mallorca.explorer.core.domain.repository.ItineraryRepository
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import com.mallorca.explorer.core.domain.repository.StopProgressRepository
import com.mallorca.explorer.core.domain.repository.UserTripRepository
import com.mallorca.explorer.core.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds @Singleton
    abstract fun bindItineraryRepository(impl: ItineraryRepositoryImpl): ItineraryRepository

    @Binds @Singleton
    abstract fun bindUserTripRepository(impl: UserTripRepositoryImpl): UserTripRepository

    @Binds @Singleton
    abstract fun bindFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds @Singleton
    abstract fun bindStopProgressRepository(impl: StopProgressRepositoryImpl): StopProgressRepository

    @Binds @Singleton
    abstract fun bindHiddenGemRepository(impl: HiddenGemRepositoryImpl): HiddenGemRepository

    @Binds @Singleton
    abstract fun bindDiscountRepository(impl: DiscountRepositoryImpl): DiscountRepository

    @Binds @Singleton
    abstract fun bindVisitedPlaceRepository(impl: VisitedPlaceRepositoryImpl): VisitedPlaceRepository

    @Binds @Singleton
    abstract fun bindRecentlyViewedRepository(impl: RecentlyViewedRepositoryImpl): RecentlyViewedRepository

    companion object {
        @Provides @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides @DefaultDispatcher
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides @MainDispatcher
        fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    }
}
