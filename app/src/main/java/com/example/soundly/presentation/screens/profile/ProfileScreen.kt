package com.example.soundly.presentation.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.presentation.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header with gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        // Avatar and user info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-80).dp)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with purple border
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.avatarUrl != null) {
                            AsyncImage(
                                model = uiState.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isLoggedIn) {
                    Text(
                        text = uiState.userName ?: "Пользователь",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.userEmail ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "В Soundly с ${formatDate(System.currentTimeMillis())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { /* Edit profile */ },
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Редактировать")
                    }
                } else {
                    Text(
                        text = "Гость",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Войдите для синхронизации данных",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { navController.navigate(Screen.Login.route) },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Войти в аккаунт")
                    }
                }
            }
        }

        // Stats
        if (uiState.isLoggedIn) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-60).dp)
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = uiState.stats.playlistsCount.toString(), label = "Плейлистов")
                    StatItem(value = uiState.stats.favoritesCount.toString(), label = "Избранное")
                    StatItem(value = uiState.stats.totalPlays.toString(), label = "Прослушано")
                }
            }
        }

        // Quick actions section
        item {
            SectionTitle(
                title = "Быстрые действия",
                modifier = Modifier.offset(y = if (uiState.isLoggedIn) (-40).dp else 0.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = if (uiState.isLoggedIn) (-40).dp else 0.dp)
            ) {
                ActionItem(
                    icon = Icons.Filled.Favorite,
                    iconTint = Color(0xFFE91E63),
                    title = "Избранные треки",
                    subtitle = "${uiState.stats.favoritesCount} треков",
                    onClick = { navController.navigate(Screen.Favorites.route) }
                )
                ActionItem(
                    icon = Icons.Filled.BarChart,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Статистика",
                    subtitle = "Топ треки и время прослушивания",
                    onClick = { navController.navigate(Screen.Statistics.route) }
                )
            }
        }

        // Appearance section
        item {
            SectionTitle(
                title = "Внешний вид",
                modifier = Modifier.offset(y = if (uiState.isLoggedIn) (-40).dp else 0.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = if (uiState.isLoggedIn) (-40).dp else 0.dp)
            ) {
                SettingItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Тема оформления",
                    subtitle = if (uiState.isDarkTheme) "Тёмная" else "Светлая",
                    onClick = { viewModel.setDarkTheme(!uiState.isDarkTheme) }
                )
            }
        }

        // Playback section
        item {
            SectionTitle(
                title = "Воспроизведение",
                modifier = Modifier.offset(y = if (uiState.isLoggedIn) (-40).dp else 0.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = if (uiState.isLoggedIn) (-40).dp else 0.dp)
            ) {
                SwitchSettingItem(
                    icon = Icons.Outlined.PlayCircle,
                    title = "Автовоспроизведение",
                    checked = uiState.isAutoPlay,
                    onCheckedChange = viewModel::setAutoPlay
                )
                SwitchSettingItem(
                    icon = Icons.Outlined.Shuffle,
                    title = "Перемешивание",
                    checked = uiState.isShuffleEnabled,
                    onCheckedChange = viewModel::setShuffleEnabled
                )
                SettingItem(
                    icon = Icons.Outlined.Equalizer,
                    title = "Эквалайзер",
                    subtitle = "Настройка звука",
                    onClick = { navController.navigate(Screen.Equalizer.route) }
                )
            }
        }

        // Logout button
        if (uiState.isLoggedIn) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = viewModel::logout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-40).dp)
                ) {
                    Text(
                        text = "Выйти из аккаунта",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    return sdf.format(Date(timestamp))
}
