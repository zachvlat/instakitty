package com.zachvlat.instakitty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.ui.navigation.AppNavigation
import com.zachvlat.instakitty.ui.theme.InstakittyTheme

class MainActivity : ComponentActivity() {

    private lateinit var dataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dataStore = SettingsDataStore(applicationContext)
        setContent {
            InstakittyTheme {
                AppNavigation(dataStore = dataStore)
            }
        }
    }
}
