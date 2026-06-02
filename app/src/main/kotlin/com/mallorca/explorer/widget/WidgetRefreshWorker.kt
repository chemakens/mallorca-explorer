package com.mallorca.explorer.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mallorca.explorer.core.domain.model.WeatherSummary
import com.mallorca.explorer.core.domain.model.windDegToCardinal
import com.mallorca.explorer.core.domain.usecase.weather.GetWeatherForLocation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import androidx.hilt.work.HiltWorker

private const val MALLORCA_LAT = 39.6
private const val MALLORCA_LNG = 2.9

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getWeatherForLocation: GetWeatherForLocation,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val weather = getWeatherForLocation(MALLORCA_LAT, MALLORCA_LNG, forceRefresh = true).first()
                ?: return Result.retry()

            val windKnots = weather.windKnots
            val supStatus = when {
                windKnots > 10f -> "RED"
                windKnots > 5f  -> "YELLOW"
                else            -> "GREEN"
            }
            val weatherEmoji = when (weather.summary) {
                WeatherSummary.SUNNY -> "☀️"
                WeatherSummary.RAINY -> "🌧️"
                WeatherSummary.WINDY -> "💨"
                WeatherSummary.HOT   -> "🌡️"
                WeatherSummary.MILD  -> "⛅"
            }

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(MallorcaWidget::class.java)

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[MallorcaWidget.PREF_TEMP_C]        = weather.tempC.toInt()
                        this[MallorcaWidget.PREF_WIND_KNOTS]    = windKnots
                        this[MallorcaWidget.PREF_WIND_DIR]      = windDegToCardinal(weather.windDirectionDeg)
                        this[MallorcaWidget.PREF_SUP_STATUS]    = supStatus
                        this[MallorcaWidget.PREF_WEATHER_EMOJI] = weatherEmoji
                        this[MallorcaWidget.PREF_UPDATED_EPOCH] = System.currentTimeMillis()
                    }
                }
                MallorcaWidget().update(context, glanceId)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget_refresh",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
            )
        }
    }
}
