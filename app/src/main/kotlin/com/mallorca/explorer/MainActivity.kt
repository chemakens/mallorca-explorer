package com.mallorca.explorer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.mallorca.explorer.core.common.LocaleSource
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import com.mallorca.explorer.core.ui.locale.LocalAppLocale
import com.mallorca.explorer.core.ui.theme.MallorcaTheme
import com.mallorca.explorer.navigation.ItineraryDetailRoute
import com.mallorca.explorer.navigation.MallorcaNavHost
import com.mallorca.explorer.navigation.PlaceDetailRoute
import com.mallorca.explorer.navigation.EventsRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefsDataStore: UserPreferencesDataStore
    @Inject lateinit var localeSource: LocaleSource

    private var navController: NavController? = null
    private var showOnboarding by mutableStateOf<Boolean?>(null)

    // Stores the intent that arrived before the NavController was ready (cold start).
    private var pendingDeepLinkIntent: Intent? = null

    // Android <13: aplica el idioma elegido también a los RECURSOS (strings.xml) en cold start,
    // envolviendo el Context con la locale guardada (en 13+ lo gestiona el sistema vía
    // LocaleManager, así que aquí es no-op). No-op también si el usuario no ha elegido idioma.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleOverride.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Capture deep link only on a genuine cold start, not on config changes.
        Timber.d("onCreate: intent.data=${intent.data} savedInstanceState=$savedInstanceState")
        if (savedInstanceState == null) {
            // FCM notification tap: read "link" from extras
            val fcmLink = intent.getStringExtra("link")
            if (fcmLink != null) {
                val modifiedLink = if (fcmLink == "mallorca://events") "mallorca://events?filter=tomorrow" else fcmLink
                pendingDeepLinkIntent = intent.also { it.data = android.net.Uri.parse(modifiedLink) }
            }
            pendingDeepLinkIntent = intent.takeIf { it.data != null }
            Timber.d("onCreate: pendingDeepLinkIntent=${pendingDeepLinkIntent?.data}")
        }

        lifecycleScope.launch {
            val completed = prefsDataStore.onboardingCompleted.first()
            if (!completed) prefsDataStore.setOnboardingCompleted()
            showOnboarding = !completed
        }

        setContent {
            val onboarding = showOnboarding ?: return@setContent

            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                // Notificaciones concedidas
            }

            LaunchedEffect(Unit) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val theme by prefsDataStore.selectedTheme.collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // Idioma activo desde la fuente única (LocaleSource). Se provee como CompositionLocal
            // para que TODA la UI (tarjetas, listas, popups…) localice los textos de datos al
            // instante cuando el usuario cambia de idioma, sin pasar el locale por cada pantalla.
            val appLocale by localeSource.locale.collectAsStateWithLifecycle()
            MallorcaTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAppLocale provides appLocale) {
                    MallorcaNavHost(
                        showOnboarding = onboarding,
                        onNavControllerReady = { nc ->
                            navController = nc
                            pendingDeepLinkIntent?.let { pending ->
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(500)
                                    tryNavigateFromIntent(nc, pending)
                                }
                                pendingDeepLinkIntent = null
                            }
                        },
                    )
                }
            }
        }
    }

    // Called when the app is already running and a new intent arrives (QR scan, ADB, etc.).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // FCM notification tap: read "link" from extras
        val fcmLink = intent.getStringExtra("link")
        if (fcmLink != null) {
            val modifiedLink = if (fcmLink == "mallorca://events") "mallorca://events?filter=tomorrow" else fcmLink
            intent.data = android.net.Uri.parse(modifiedLink)
        }
        Timber.d("onNewIntent: ${intent.data}")
        val nc = navController
        if (nc != null) {
            lifecycleScope.launch {
                kotlinx.coroutines.delay(100)
                tryNavigateFromIntent(nc, intent)
            }
        } else {
            // NavController not ready yet; defer until onNavControllerReady fires.
            pendingDeepLinkIntent = intent.takeIf { it.data != null }
        }
    }

    private fun tryNavigateFromIntent(nc: NavController, intent: Intent) {
        val uri = intent.data
        Timber.d("tryNavigateFromIntent: uri=$uri")
        Toast.makeText(this, "Deep link: $uri", Toast.LENGTH_LONG).show()
        if (uri == null) { Timber.d("tryNavigateFromIntent: uri is null, returning"); return }
        val itineraryId = uri.getQueryParameter("itineraryId")
        val placeId = uri.getQueryParameter("placeId")
        Timber.d("tryNavigateFromIntent: itineraryId=$itineraryId placeId=$placeId")
        when {
            uri.host == "events" -> {
                val filter = uri.getQueryParameter("filter")
                nc.navigate(EventsRoute(filter = filter)) { launchSingleTop = true }
            }
            itineraryId != null -> {
                Timber.d("tryNavigateFromIntent: navigating to ItineraryDetailRoute($itineraryId)")
                nc.navigate(ItineraryDetailRoute(itineraryId))
            }
            placeId != null -> nc.navigate(PlaceDetailRoute(placeId)) {
                launchSingleTop = true
            }
        }
        Timber.d("tryNavigateFromIntent: itineraryId=$itineraryId placeId=$placeId")
    }
}

/**
 * Mirror síncrono del idioma elegido para aplicar los RECURSOS (strings.xml) en Android <13.
 * En 13+ el idioma por app lo gestiona LocaleManager (ver SettingsViewModel.changeLanguage) y
 * esto es no-op. Se guarda en SharedPreferences (no DataStore) porque attachBaseContext necesita
 * leerlo de forma síncrona antes de que arranque la UI. El nombre de fichero/clave se comparte
 * con SettingsViewModel (mismas constantes literales; no hay módulo común con Context).
 */
object LocaleOverride {
    const val PREFS = "app_locale_prefs"
    const val KEY = "app_locale"

    fun persist(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val code = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = java.util.Locale(code)
        java.util.Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
