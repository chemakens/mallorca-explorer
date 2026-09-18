package com.mallorca.explorer.core.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mallorca.explorer.core.domain.model.EventCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_preferences"
)

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED_CATEGORIES = stringSetPreferencesKey("enabled_event_categories")
        val GEM_NOTIFICATIONS_ENABLED = booleanPreferencesKey("gem_notifications_enabled")
        val LAST_SEEN_SEED_VERSION = intPreferencesKey("last_seen_seed_version")
    }

    // Si el set está vacío = todas las categorías activas (comportamiento por defecto)
    val enabledCategories: Flow<Set<EventCategory>> = context.notificationDataStore.data.map { prefs ->
        val stored = prefs[Keys.ENABLED_CATEGORIES]
        if (stored.isNullOrEmpty()) EventCategory.entries.toSet()
        else stored.mapNotNull { name -> EventCategory.entries.find { it.name == name } }.toSet()
    }

    val gemNotificationsEnabled: Flow<Boolean> = context.notificationDataStore.data.map { prefs ->
        prefs[Keys.GEM_NOTIFICATIONS_ENABLED] ?: true
    }

    val lastSeenSeedVersion: Flow<Int> = context.notificationDataStore.data.map { prefs ->
        prefs[Keys.LAST_SEEN_SEED_VERSION] ?: 0
    }

    suspend fun setEnabledCategories(categories: Set<EventCategory>) {
        context.notificationDataStore.edit { prefs ->
            if (categories.size == EventCategory.entries.size) {
                prefs.remove(Keys.ENABLED_CATEGORIES) // todas activas = no guardamos nada
            } else {
                prefs[Keys.ENABLED_CATEGORIES] = categories.map { it.name }.toSet()
            }
        }
    }

    suspend fun setGemNotificationsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[Keys.GEM_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setLastSeenSeedVersion(version: Int) {
        context.notificationDataStore.edit { prefs ->
            prefs[Keys.LAST_SEEN_SEED_VERSION] = version
        }
    }
}
