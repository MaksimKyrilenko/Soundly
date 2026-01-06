package com.example.soundly.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.soundly.presentation.components.MiniPlayer
import com.example.soundly.presentation.navigation.Screen
import com.example.soundly.presentation.navigation.SoundlyNavGraph
import com.example.soundly.presentation.navigation.bottomNavItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundlyAppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }
    val showMiniPlayer = currentDestination?.route != Screen.Player.route &&
            currentDestination?.route != Screen.Login.route &&
            currentDestination?.route != Screen.Register.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main content
        SoundlyNavGraph(navController = navController)
        
        // Bottom section with mini player and nav bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Mini Player with animation
            AnimatedVisibility(
                visible = showMiniPlayer,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                MiniPlayer(
                    onClick = { navController.navigate(Screen.Player.route) }
                )
            }
            
            // Floating Navigation Bar
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(32.dp),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            ),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                
                                // Animated scale for selected item
                                val scale by animateFloatAsState(
                                    targetValue = if (selected) 1.1f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "navItemScale"
                                )
                                
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.title,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .scale(scale)
                                        )
                                    },
                                    label = null,
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
