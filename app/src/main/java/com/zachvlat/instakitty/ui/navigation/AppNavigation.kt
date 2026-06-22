package com.zachvlat.instakitty.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.ui.following.FollowingScreen
import com.zachvlat.instakitty.ui.home.HomeScreen
import com.zachvlat.instakitty.ui.post.PostScreen
import com.zachvlat.instakitty.ui.setup.SetupScreen
import com.zachvlat.instakitty.ui.settings.SettingsScreen
import com.zachvlat.instakitty.ui.user.UserScreen

enum class BottomTab(val label: String, val route: String, val icon: ImageVector) {
    Home("Home", "home", Icons.Filled.Home),
    Following("Following", "following", Icons.Filled.Person),
    Settings("Settings", "settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(dataStore: SettingsDataStore) {
    val navController = rememberNavController()
    val isConfigured by dataStore.isConfigured.collectAsState(initial = null)

    val startDestination = when (isConfigured) {
        true -> "home"
        false -> "setup"
        null -> null
    }

    if (startDestination == null) {
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != null && currentRoute != "setup"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable("setup") {
                SetupScreen(
                    onConfigured = {
                        navController.navigate("home") {
                            popUpTo("setup") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    onNavigateToUser = { username ->
                        navController.navigate("user/$username")
                    },
                    onNavigateToPost = { shortcode ->
                        navController.navigate("post/$shortcode")
                    }
                )
            }
            composable("settings") {
                SettingsScreen()
            }
            composable("following") {
                FollowingScreen(
                    onUserClick = { username ->
                        navController.navigate("user/$username")
                    }
                )
            }
            composable(
                route = "post/{shortcode}",
                arguments = listOf(navArgument("shortcode") { type = NavType.StringType })
            ) { backStackEntry ->
                val shortcode = backStackEntry.arguments?.getString("shortcode") ?: return@composable
                PostScreen(
                    shortcode = shortcode,
                    onBack = { navController.popBackStack() },
                    onUserClick = { username ->
                        navController.navigate("user/$username")
                    }
                )
            }
            composable(
                route = "user/{username}",
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username") ?: return@composable
                UserScreen(
                    username = username,
                    onBack = { navController.popBackStack() },
                    onPostClick = { shortcode ->
                        navController.navigate("post/$shortcode")
                    }
                )
            }
        }
    }
}
