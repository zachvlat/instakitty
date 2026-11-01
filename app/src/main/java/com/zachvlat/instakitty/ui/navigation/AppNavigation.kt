package com.zachvlat.instakitty.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.ui.explore.ExploreScreen
import com.zachvlat.instakitty.ui.following.FollowingScreen
import com.zachvlat.instakitty.ui.home.HomeScreen
import com.zachvlat.instakitty.ui.post.PostScreen
import com.zachvlat.instakitty.ui.saved.SavedScreen
import com.zachvlat.instakitty.ui.setup.SetupScreen
import com.zachvlat.instakitty.ui.settings.SettingsScreen
import com.zachvlat.instakitty.ui.user.UserScreen

enum class BottomTab(val label: String, val route: String, val icon: ImageVector) {
    Home("Home", "home", Icons.Filled.Home),
    Explore("Explore", "explore", Icons.Filled.Search),
    Following("Following", "following", Icons.Filled.Person),
    Saved("Saved", "saved", Icons.Filled.Bookmark),
    Settings("Settings", "settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    dataStore: SettingsDataStore,
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
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

    var selectedTab by remember { mutableStateOf(BottomTab.Home) }

    // Every #hashtag link lands on the Explore tab with the topic already searched.
    val openHashtag: (String) -> Unit = { tag ->
        selectedTab = BottomTab.Explore
        navController.navigate("explore?query=${Uri.encode(tag)}") {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    LaunchedEffect(deepLinkRoute, startDestination) {
        android.util.Log.d(
            "InstaKitty.DeepLink",
            "AppNavigation deepLinkRoute=$deepLinkRoute startDestination=$startDestination"
        )
        if (deepLinkRoute != null && startDestination == "home") {
            navController.navigate(deepLinkRoute) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = false
                }
                launchSingleTop = true
            }
            selectedTab = when {
                deepLinkRoute.startsWith("user/") -> BottomTab.Following
                deepLinkRoute.startsWith("post/") -> BottomTab.Following
                else -> BottomTab.Home
            }
            onDeepLinkHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = {
                                selectedTab = tab
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
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
                    onNavigateToPost = { shortcode ->
                        navController.navigate("post/$shortcode")
                    }
                )
            }
            composable(
                route = "explore?query={query}",
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val initialQuery = backStackEntry.arguments?.getString("query").orEmpty()
                ExploreScreen(
                    initialQuery = initialQuery,
                    onPostClick = { shortcode ->
                        navController.navigate("post/$shortcode")
                    },
                    onUserClick = { username ->
                        navController.navigate("user/$username")
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
            composable("saved") {
                SavedScreen(
                    onOpenPost = { shortcode ->
                        navController.navigate("post/$shortcode")
                    },
                    onOpenUser = { username ->
                        navController.navigate("user/$username")
                    },
                    onHashtagClick = openHashtag
                )
            }
            composable(
                route = "post/{shortcode}?siblings={siblings}",
                arguments = listOf(
                    navArgument("shortcode") { type = NavType.StringType },
                    navArgument("siblings") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val shortcode = backStackEntry.arguments?.getString("shortcode") ?: return@composable
                val siblings = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("siblings")
                        .orEmpty()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
                PostScreen(
                    shortcode = shortcode,
                    siblings = siblings,
                    onBack = { navController.popBackStack() },
                    onUserClick = { username ->
                        navController.navigate("user/$username")
                    },
                    onHashtagClick = openHashtag
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
                    onPostClick = { shortcode, siblings ->
                        navController.navigate(
                            "post/$shortcode?siblings=${Uri.encode(siblings.joinToString(","))}"
                        )
                    },
                    onUserClick = { mentionedUsername ->
                        navController.navigate("user/$mentionedUsername")
                    },
                    onHashtagClick = openHashtag
                )
            }
        }
    }
}
