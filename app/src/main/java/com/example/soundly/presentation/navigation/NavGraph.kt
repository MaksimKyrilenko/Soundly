package com.example.soundly.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.soundly.presentation.screens.discover.DiscoverScreen
import com.example.soundly.presentation.screens.download.DownloadScreen
import com.example.soundly.presentation.screens.home.HomeScreen
import com.example.soundly.presentation.screens.player.FullPlayerScreen
import com.example.soundly.presentation.screens.playlist.PlaylistDetailScreen
import com.example.soundly.presentation.screens.playlist.PlaylistsScreen
import com.example.soundly.presentation.screens.profile.ProfileScreen
import com.example.soundly.presentation.screens.auth.LoginScreen
import com.example.soundly.presentation.screens.auth.RegisterScreen
import com.example.soundly.presentation.screens.equalizer.EqualizerScreen
import com.example.soundly.presentation.screens.favorites.FavoritesScreen
import com.example.soundly.presentation.screens.statistics.StatisticsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Download : Screen("download")
    data object Playlists : Screen("playlists")
    data object Discover : Screen("discover")
    data object Profile : Screen("profile")
    data object Player : Screen("player")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Equalizer : Screen("equalizer")
    data object Favorites : Screen("favorites")
    data object Statistics : Screen("statistics")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
}

// Local easing definitions to avoid import conflicts
private val NavEaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val NavEaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

// Animation specs
private val screenEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(300, easing = NavEaseOutCubic)
) + slideInHorizontally(
    initialOffsetX = { it / 4 },
    animationSpec = tween(300, easing = NavEaseOutCubic)
)

private val screenExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(200, easing = NavEaseInCubic)
) + slideOutHorizontally(
    targetOffsetX = { -it / 4 },
    animationSpec = tween(200, easing = NavEaseInCubic)
)

private val screenPopEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(300, easing = NavEaseOutCubic)
) + slideInHorizontally(
    initialOffsetX = { -it / 4 },
    animationSpec = tween(300, easing = NavEaseOutCubic)
)

private val screenPopExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(200, easing = NavEaseInCubic)
) + slideOutHorizontally(
    targetOffsetX = { it / 4 },
    animationSpec = tween(200, easing = NavEaseInCubic)
)

// Bottom nav fade transition
private val bottomNavEnter: EnterTransition = fadeIn(tween(200))
private val bottomNavExit: ExitTransition = fadeOut(tween(150))

// Player slide up transition
private val playerEnter: EnterTransition = slideInVertically(
    initialOffsetY = { it },
    animationSpec = tween(400, easing = NavEaseOutCubic)
) + fadeIn(tween(300))

private val playerExit: ExitTransition = slideOutVertically(
    targetOffsetY = { it },
    animationSpec = tween(300, easing = NavEaseInCubic)
) + fadeOut(tween(200))

@Composable
fun SoundlyNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { screenEnterTransition },
        exitTransition = { screenExitTransition },
        popEnterTransition = { screenPopEnterTransition },
        popExitTransition = { screenPopExitTransition }
    ) {
        // Bottom nav screens with fade transition
        composable(
            route = Screen.Home.route,
            enterTransition = { bottomNavEnter },
            exitTransition = { bottomNavExit },
            popEnterTransition = { bottomNavEnter },
            popExitTransition = { bottomNavExit }
        ) {
            HomeScreen(navController = navController)
        }
        
        composable(
            route = Screen.Download.route,
            enterTransition = { bottomNavEnter },
            exitTransition = { bottomNavExit },
            popEnterTransition = { bottomNavEnter },
            popExitTransition = { bottomNavExit }
        ) {
            DownloadScreen(navController = navController)
        }
        
        composable(
            route = Screen.Playlists.route,
            enterTransition = { bottomNavEnter },
            exitTransition = { bottomNavExit },
            popEnterTransition = { bottomNavEnter },
            popExitTransition = { bottomNavExit }
        ) {
            PlaylistsScreen(navController = navController)
        }
        
        composable(
            route = Screen.Discover.route,
            enterTransition = { bottomNavEnter },
            exitTransition = { bottomNavExit },
            popEnterTransition = { bottomNavEnter },
            popExitTransition = { bottomNavExit }
        ) {
            DiscoverScreen(navController = navController)
        }
        
        composable(
            route = Screen.Profile.route,
            enterTransition = { bottomNavEnter },
            exitTransition = { bottomNavExit },
            popEnterTransition = { bottomNavEnter },
            popExitTransition = { bottomNavExit }
        ) {
            ProfileScreen(navController = navController)
        }
        
        // Player with slide up animation
        composable(
            route = Screen.Player.route,
            enterTransition = { playerEnter },
            exitTransition = { playerExit },
            popEnterTransition = { playerEnter },
            popExitTransition = { playerExit }
        ) {
            FullPlayerScreen(navController = navController)
        }
        
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }
        
        composable(
            route = Screen.Equalizer.route,
            enterTransition = { playerEnter },
            exitTransition = { playerExit },
            popEnterTransition = { playerEnter },
            popExitTransition = { playerExit }
        ) {
            EqualizerScreen(navController = navController)
        }
        
        composable(Screen.Favorites.route) {
            FavoritesScreen(navController = navController)
        }
        
        composable(Screen.Statistics.route) {
            StatisticsScreen(navController = navController)
        }
        
        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistDetailScreen(navController = navController, playlistId = playlistId)
        }
    }
}
