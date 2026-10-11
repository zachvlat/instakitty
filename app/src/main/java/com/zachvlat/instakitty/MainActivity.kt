package com.zachvlat.instakitty

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.ui.navigation.AppNavigation
import com.zachvlat.instakitty.ui.theme.InstakittyTheme
import com.zachvlat.instakitty.util.InstagramLinkParser

class MainActivity : ComponentActivity() {

    private lateinit var dataStore: SettingsDataStore
    private var pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dataStore = SettingsDataStore(applicationContext)
        pendingDeepLink.value = parseIntent(intent)

        setContent {
            InstakittyTheme {
                AppNavigation(
                    dataStore = dataStore,
                    deepLinkRoute = pendingDeepLink.value,
                    onDeepLinkHandled = { pendingDeepLink.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?): String? {
        if (intent == null) return null
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)?.toString()
            ?: intent.clipData?.getItemAt(0)?.text?.toString()
        val route = when (intent.action) {
            Intent.ACTION_SEND -> InstagramLinkParser.routeFromSharedText(sharedText)
                ?: InstagramLinkParser.routeFromUrl(intent.data?.toString())
            else -> InstagramLinkParser.routeFromUrl(intent.data?.toString())
        }
        Log.d(
            TAG,
            "intent action=${intent.action} data=${intent.data} text=$sharedText route=$route"
        )
        return route
    }

    companion object {
        private const val TAG = "InstaKitty.DeepLink"
    }
}
