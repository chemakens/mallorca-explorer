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
import com.mallorca.explorer.notification.NewGemCheckWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.mallorca.explorer.core.data.auth.UserCloudDataSource
import com.mallorca.explorer.notification.createNotificationChannel
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MallorcaApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var prefsDataStore: UserPreferencesDataStore
    @Inject lateinit var localeSource: LocaleSource
    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var userCloudDataSource: UserCloudDataSource

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
        android.util.Log.d("MallorcaApp", "🚀 onCreate called, BuildConfig.DEBUG=${BuildConfig.DEBUG}")
        android.util.Log.d("MallorcaApp", "🚀 Enqueuing SeedDataWorker with REPLACE policy...")
        Timber.d("🚀 Enqueuing SeedDataWorker with REPLACE policy...")
        WorkManager.getInstance(this).enqueueUniqueWork(
            "seed_data",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SeedDataWorker>().build(),
        )
        android.util.Log.d("MallorcaApp", "✅ SeedDataWorker enqueued successfully")
        NewGemCheckWorker.schedule(this)
        createNotificationChannel(this)

        // Registrar token FCM para usuarios ya logueados (actualizaciones de app)
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val uid = firebaseAuth.currentUser?.uid ?: return@launch
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                userCloudDataSource.saveFcmToken(uid, token)
                Timber.d("FCM token registrado al arrancar para uid=$uid")
            } catch (e: Exception) {
                Timber.w(e, "No se pudo registrar token FCM al arrancar")
            }
        }
    }
}
