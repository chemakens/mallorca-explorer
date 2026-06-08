package com.mallorca.explorer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import com.mallorca.explorer.core.ui.theme.MallorcaTheme
import com.mallorca.explorer.navigation.ItineraryDetailRoute
import com.mallorca.explorer.navigation.MallorcaNavHost
import com.mallorca.explorer.navigation.PlaceDetailRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefsDataStore: UserPreferencesDataStore

    private var navController: NavController? = null
    private var showOnboarding by mutableStateOf<Boolean?>(null)

    // Stores the intent that arrived before the NavController was ready (cold start).
    private var pendingDeepLinkIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Capture deep link only on a genuine cold start, not on config changes.
        if (savedInstanceState == null) {
            pendingDeepLinkIntent = intent.takeIf { it.data != null }
        }

        lifecycleScope.launch {
            val completed = prefsDataStore.onboardingCompleted.first()
            if (!completed) prefsDataStore.setOnboardingCompleted()
            showOnboarding = !completed
        }

        setContent {
            val onboarding = showOnboarding ?: return@setContent

            val theme by prefsDataStore.selectedTheme.collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            MallorcaTheme(darkTheme = darkTheme) {
                MallorcaNavHost(
                    showOnboarding = onboarding,
                    onNavControllerReady = { nc ->
                        navController = nc
                        pendingDeepLinkIntent?.let { pending ->
                            tryNavigateFromIntent(nc, pending)
                            pendingDeepLinkIntent = null
                        }
                    },
                )
            }
        }
    }

    // Called when the app is already running and a new intent arrives (QR scan, ADB, etc.).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Timber.d("onNewIntent: ${intent.data}")
        val nc = navController
        if (nc != null) {
            tryNavigateFromIntent(nc, intent)
        } else {
            // NavController not ready yet; defer until onNavControllerReady fires.
            pendingDeepLinkIntent = intent.takeIf { it.data != null }
        }
    }

    private fun tryNavigateFromIntent(nc: NavController, intent: Intent) {
        val uri = intent.data ?: return
        val itineraryId = uri.getQueryParameter("itineraryId")
        val placeId = uri.getQueryParameter("placeId")
        when {
            itineraryId != null -> nc.navigate(ItineraryDetailRoute(itineraryId)) {
                launchSingleTop = true
            }
            placeId != null -> nc.navigate(PlaceDetailRoute(placeId)) {
                launchSingleTop = true
            }
        }
        Timber.d("tryNavigateFromIntent: itineraryId=$itineraryId placeId=$placeId")
    }
}
