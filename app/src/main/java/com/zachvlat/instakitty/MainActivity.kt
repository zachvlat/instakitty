package com.zachvlat.instakitty

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.ui.navigation.AppNavigation
import com.zachvlat.instakitty.ui.theme.InstakittyTheme

class MainActivity : ComponentActivity() {

    private lateinit var dataStore: SettingsDataStore
    private var pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dataStore = SettingsDataStore(applicationContext)
        pendingDeepLink.value = parseInstagramIntent(intent)

        setContent {
            InstakittyTheme {
                AppNavigation(
                    dataStore = dataStore,
                    deepLinkRoute = pendingDeepLink.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDeepLink.value = parseInstagramIntent(intent)
    }

    private fun parseInstagramIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val host = uri.host?.removePrefix("www.") ?: return null
        if (host != "instagram.com") return null

        val segments = uri.pathSegments.filter { it.isNotEmpty() }

        return when {
            segments.size == 1 && segments[0] != "p" -> {
                "user/${segments[0]}"
            }
            segments.size == 2 && segments[0] == "p" -> {
                "post/${segments[1]}"
            }
            segments.size == 3 && segments[1] == "p" -> {
                "post/${segments[2]}"
            }
            segments.size == 3 && segments[1] == "reel" -> {
                "post/${segments[2]}"
            }
            else -> null
        }
    }
}
