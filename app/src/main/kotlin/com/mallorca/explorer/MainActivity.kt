package com.mallorca.explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mallorca.explorer.core.ui.theme.MallorcaTheme
import com.mallorca.explorer.navigation.MallorcaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MallorcaTheme {
                MallorcaNavHost()
            }
        }
    }
}
