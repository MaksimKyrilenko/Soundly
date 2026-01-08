package com.example.soundly.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.soundly.presentation.theme.playerGradient
import com.example.soundly.presentation.theme.ProgressGradient
import com.example.soundly.presentation.theme.LocalColorPalette
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.domain.model.RepeatMode
import com.example.soundly.presentation.components.AddToPlaylistDialog
import com.example.soundly.presentation.navigation.Screen
import com.example.soundly.presentation.screens.home.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentTrack = playerState.currentTrack
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSliding by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val palette = LocalColorPalette.current

    LaunchedEffect(playerState.currentPosition) {
        if (!isSliding) {
            sliderPosition = playerState.currentPosition.toFloat()
        }
    }
    
    // Диалог добавления в плейлист
    if (showAddToPlaylistDialog && currentTrack != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                viewModel.addToPlaylist(currentTrack.id, playlistId)
                showAddToPlaylistDialog = false
            },
            onCreateNew = {
                showAddToPlaylistDialog = false
                showCreatePlaylistDialog = true
            }
        )
    }
    
    // Диалог создания нового плейлиста
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Новый плейлист", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank() && currentTrack != null) {
                            viewModel.createPlaylistAndAddTrack(newPlaylistName, currentTrack.id)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Отмена")
                }
            },
            containerColor = Color(0xFF2A2355)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerGradient())
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Закрыть",
                        modifier = Modifier.size(32.dp),
                        tint = palette.textPrimary
                    )
                }
                
                Text(
                    text = "Сейчас играет",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                
                IconButton(onClick = { navController.navigate(Screen.Equalizer.route) }) {
                    Icon(
                        Icons.Default.Equalizer,
                        contentDescription = "Эквалайзер",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Album Art with shadow and glow
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                Surface(
                    modifier = Modifier
                        .size(280.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF2A2355)
                ) {
                    AsyncImage(
                        model = currentTrack?.artworkUri,
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Track Info
            Text(
                text = currentTrack?.title ?: "Нет трека",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = currentTrack?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            // Speed/Pitch indicator
            if (playerState.playbackSpeed != 1.0f || playerState.pitch != 1.0f) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x40FFFFFF),
                    onClick = { navController.navigate(Screen.Equalizer.route) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format("%.2f", playerState.playbackSpeed)}x" +
                                    if (playerState.pitch != 1.0f && playerState.pitch != playerState.playbackSpeed) 
                                        " • pitch ${String.format("%.2f", playerState.pitch)}x" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isSliding = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(sliderPosition.toLong())
                        isSliding = false
                    },
                    valueRange = 0f..playerState.duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.textPrimary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = palette.textSecondary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(sliderPosition.toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Text(
                        text = formatDuration(playerState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Перемешать",
                        tint = if (playerState.shuffleEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            palette.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущий",
                        modifier = Modifier.size(36.dp),
                        tint = palette.textPrimary
                    )
                }

                // Play button with gradient background
                Surface(
                    onClick = { viewModel.playPause() },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Пауза" else "Воспроизвести",
                            modifier = Modifier.size(40.dp),
                            tint = palette.textPrimary
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий",
                        modifier = Modifier.size(36.dp),
                        tint = palette.textPrimary
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (playerState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Повтор",
                        tint = if (playerState.repeatMode != RepeatMode.OFF) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            palette.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Additional Actions (3 кнопки)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Избранное
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { currentTrack?.let { viewModel.toggleFavorite(it.id) } },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = if (currentTrack?.isFavorite == true)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else
                            Color(0x30FFFFFF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (currentTrack?.isFavorite == true) 
                                    Icons.Filled.Favorite 
                                else 
                                    Icons.Outlined.FavoriteBorder,
                                contentDescription = "Избранное",
                                tint = if (currentTrack?.isFavorite == true) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    palette.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Избранное",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentTrack?.isFavorite == true)
                            MaterialTheme.colorScheme.primary
                        else
                            palette.textSecondary
                    )
                }

                // Добавить в плейлист
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { showAddToPlaylistDialog = true },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color(0x30FFFFFF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Добавить в плейлист",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Плейлист",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }

                // Эквалайзер
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { navController.navigate(Screen.Equalizer.route) },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color(0x30FFFFFF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Эквалайзер",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Эквалайзер",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}
