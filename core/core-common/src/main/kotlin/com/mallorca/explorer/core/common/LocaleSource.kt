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
    // Valor inicial: idioma del sistema normalizado a es/en/de (si no, inglés). El valor real
    // (la elección persistida del usuario) lo fija MallorcaApp al arrancar vía setLocale().
    private val _locale = MutableStateFlow(normalizeDefault(Locale.getDefault().language))
    val locale: StateFlow<String> = _locale.asStateFlow()

    fun setLocale(locale: String) {
        if (_locale.value != locale) _locale.value = locale
    }

    companion object {
        /** Normaliza el idioma POR DEFECTO a es/en/de; cualquier otro cae en inglés. */
        fun normalizeDefault(code: String?): String = when (code?.lowercase()?.take(2)) {
            "es" -> "es"
            "de" -> "de"
            else -> "en"
        }
    }
}
