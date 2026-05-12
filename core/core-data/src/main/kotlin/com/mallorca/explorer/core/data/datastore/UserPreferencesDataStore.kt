package com.mallorca.explorer.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val IS_SEEDED = booleanPreferencesKey("is_seeded")
        val LAST_SYNC_EPOCH = longPreferencesKey("last_sync_epoch")
        val LAST_MAP_LAT = doublePreferencesKey("last_map_lat")
        val LAST_MAP_LNG = doublePreferencesKey("last_map_lng")
    }

    val isSeeded: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_SEEDED] ?: false }
    val lastSyncEpoch: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_SYNC_EPOCH] ?: 0L }

    suspend fun setIsSeeded(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_SEEDED] = value }
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
}
