package com.example.soundly.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        title = "Главная",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        route = Screen.Download.route,
        title = "Медиа",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download
    ),
    BottomNavItem(
        route = Screen.Playlists.route,
        title = "Листы",
        selectedIcon = Icons.Filled.QueueMusic,
        unselectedIcon = Icons.Outlined.QueueMusic
    ),
    BottomNavItem(
        route = Screen.Discover.route,
        title = "Открыть",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore
    ),
    BottomNavItem(
        route = Screen.Profile.route,
        title = "Я",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)
