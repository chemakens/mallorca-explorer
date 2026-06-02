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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
