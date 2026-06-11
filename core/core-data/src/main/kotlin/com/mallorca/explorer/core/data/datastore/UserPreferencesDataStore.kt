package com.mallorca.explorer.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val IS_SEEDED = booleanPreferencesKey("is_seeded")
        val SEED_VERSION = intPreferencesKey("seed_version")
        val LAST_SYNC_EPOCH = longPreferencesKey("last_sync_epoch")
        val LAST_MAP_LAT = doublePreferencesKey("last_map_lat")
        val LAST_MAP_LNG = doublePreferencesKey("last_map_lng")
        val LOCALE = stringPreferencesKey("locale")
        val THEME = stringPreferencesKey("theme")
        val DEV_MODE = booleanPreferencesKey("dev_mode")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val isSeeded: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_SEEDED] ?: false }
    val seedVersion: Flow<Int> = context.dataStore.data.map { it[Keys.SEED_VERSION] ?: 0 }
    val lastSyncEpoch: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_SYNC_EPOCH] ?: 0L }
    val selectedLocale: Flow<String> = context.dataStore.data.map { it[Keys.LOCALE] ?: Locale.getDefault().language }
    val selectedTheme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val devMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEV_MODE] ?: false }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun setIsSeeded(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_SEEDED] = value }
    }

    suspend fun setSeedVersion(version: Int) {
        context.dataStore.edit { it[Keys.SEED_VERSION] = version }
    }

    suspend fun setLastSyncEpoch(epoch: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_EPOCH] = epoch }
    }

    suspend fun setLastMapPosition(lat: Double, lng: Double) {
        context.dataStore.edit {
            it[Keys.LAST_MAP_LAT] = lat
            it[Keys.LAST_MAP_LNG] = lng
        }
    }

    suspend fun setLocale(code: String) {
        context.dataStore.edit { it[Keys.LOCALE] = code }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    suspend fun setDevMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEV_MODE] = enabled }
    }
}
