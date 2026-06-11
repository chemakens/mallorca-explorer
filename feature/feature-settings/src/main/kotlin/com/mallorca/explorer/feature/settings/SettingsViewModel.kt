package com.mallorca.explorer.feature.settings

import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mallorca.explorer.core.common.LocaleSource
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localeSource: LocaleSource,
    private val prefsDataStore: UserPreferencesDataStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val currentLocale: StateFlow<String> = localeSource.locale
    val currentTheme: StateFlow<String> = prefsDataStore.selectedTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val devMode: StateFlow<Boolean> = prefsDataStore.devMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val versionName: String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: Exception) { "—" }

    private val _devTapCount = MutableStateFlow(0)
    val devTapCount: StateFlow<Int> = _devTapCount.asStateFlow()

    fun onVersionTapped() {
        val next = _devTapCount.value + 1
        if (next >= 7) {
            _devTapCount.value = 0
            viewModelScope.launch { prefsDataStore.setDevMode(true) }
        } else {
            _devTapCount.value = next
        }
    }

    fun setDevMode(enabled: Boolean) {
        viewModelScope.launch { prefsDataStore.setDevMode(enabled) }
    }

    fun changeLanguage(localeCode: String) {
        localeSource.setLocale(localeCode)
        viewModelScope.launch { prefsDataStore.setLocale(localeCode) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            localeManager.applicationLocales = LocaleList.forLanguageTags(localeCode)
        }
    }

    fun changeTheme(theme: String) {
        viewModelScope.launch { prefsDataStore.setTheme(theme) }
    }
}
