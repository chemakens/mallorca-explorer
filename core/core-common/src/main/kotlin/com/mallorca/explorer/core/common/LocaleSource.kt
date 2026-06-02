package com.mallorca.explorer.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the active locale across the app's reactive pipeline.
 * Screens push updates via setLocale(); the repository flatMapLatests on it so all
 * itinerary flows re-map instantly when the device language changes.
 */
@Singleton
class LocaleSource @Inject constructor() {
    private val _locale = MutableStateFlow(Locale.getDefault().language)
    val locale: StateFlow<String> = _locale.asStateFlow()

    fun setLocale(locale: String) {
        if (_locale.value != locale) _locale.value = locale
    }
}
