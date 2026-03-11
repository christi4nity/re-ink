package com.reink.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reink.VolumeKey
import com.reink.ui.feed.FeedScreen
import com.reink.ui.home.HomeScreen
import com.reink.ui.reader.ReaderScreen
import com.reink.ui.readlater.ReadLaterScreen
import com.reink.ui.archive.ArchiveScreen
import com.reink.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

private val bottomNavScreens = listOf(Screen.Home, Screen.Feed, Screen.ReadLater, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReInkNavGraph(
    volumeKeyEvents: SharedFlow<VolumeKey> = MutableSharedFlow(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavScreens.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            icon = {},
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onArticleClick = { articleId ->
                        navController.navigate(Screen.Reader.createRoute("article", articleId))
                    },
                    onReadLaterClick = { readLaterId ->
                        navController.navigate(Screen.Reader.createRoute("readlater", readLaterId))
                    },
                )
            }
            composable(Screen.Feed.route) {
                FeedScreen(
                    onArticleClick = { articleId ->
                        navController.navigate(Screen.Reader.createRoute("article", articleId))
                    },
                )
            }
            composable(Screen.ReadLater.route) {
                ReadLaterScreen(
                    onItemClick = { readLaterId ->
                        navController.navigate(Screen.Reader.createRoute("readlater", readLaterId))
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToArchive = {
                        navController.navigate(Screen.Archive.route)
                    },
                )
            }
            composable(Screen.Archive.route) {
                ArchiveScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("itemType") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.LongType },
                ),
            ) {
                ReaderScreen(
                    onBack = { navController.popBackStack() },
                    volumeKeyEvents = volumeKeyEvents,
                )
            }
        }
    }
}
