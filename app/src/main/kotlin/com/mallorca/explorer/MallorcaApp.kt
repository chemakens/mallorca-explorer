package com.mallorca.explorer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mallorca.explorer.core.common.LocaleSource
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import com.mallorca.explorer.core.data.sync.SeedDataWorker
import com.mallorca.explorer.notification.DailyEventCheckWorker
import com.mallorca.explorer.notification.createNotificationChannel
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MallorcaApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var prefsDataStore: UserPreferencesDataStore
    @Inject lateinit var localeSource: LocaleSource

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "MallorcaExplorer/1.0 (Android; okhttp/4.12.0)")
                            .build()
                    )
                }
                .build()
        )
        .build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            localeSource.setLocale(prefsDataStore.selectedLocale.first())
        }
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        WorkManager.getInstance(this).enqueueUniqueWork(
            "seed_data",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SeedDataWorker>().build(),
        )
        createNotificationChannel(this)
        DailyEventCheckWorker.schedule(this)
    }
}
