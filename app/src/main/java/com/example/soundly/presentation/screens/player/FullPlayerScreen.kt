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
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.soundly.presentation.theme.playerGradient
import com.example.soundly.presentation.theme.ProgressGradient
import com.example.soundly.presentation.theme.LocalColorPalette
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.R
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
    
    // Аудио-визуализация из AudioReactiveController
    val amplitude by viewModel.playerController.amplitude.collectAsState()
    val bassLevel by viewModel.playerController.bassLevel.collectAsState()
    val beatDetected by viewModel.playerController.beatDetected.collectAsState()
    val coverScale by viewModel.playerController.coverScale.collectAsState()
    
    // Более агрессивная fallback анимация
    val infiniteTransition = rememberInfiniteTransition(label = "fallbackPulse")
    val fallbackPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "fallbackPulse"
    )
    
    // Используем данные от AudioReactiveController или fallback
    // Если нет данных от Visualizer - используем более заметную fallback анимацию
    val effectiveBassLevel = if (bassLevel > 0.01f) bassLevel else (fallbackPulse * 0.5f)
    val effectiveBeat = beatDetected || (fallbackPulse > 0.85f && playerState.isPlaying)
    val effectiveScale = if (coverScale > 1.01f) {
        coverScale
    } else {
        // Fallback: более заметная пульсация
        1f + (fallbackPulse * 0.12f)
    }
    
    // Детальное логирование для отладки
    LaunchedEffect(amplitude, bassLevel, beatDetected, coverScale, playerState.isPlaying) {
        android.util.Log.d("FullPlayerScreen", 
            "Playing: ${playerState.isPlaying}, " +
            "Amplitude: $amplitude, Bass: $bassLevel, " +
            "Beat: $beatDetected, Scale: $coverScale, " +
            "EffectiveScale: $effectiveScale, FallbackPulse: $fallbackPulse"
        )
    }

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

            // Album Art with audio-reactive animation and visual waves
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Используем scale от AudioReactiveController с плавной анимацией
                val animatedScale by animateFloatAsState(
                    targetValue = if (playerState.isPlaying) effectiveScale else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "audioReactiveScale"
                )
                
                // Внешнее цветное кольцо (волны) - реагирует на басы
                val waveAlpha = if (playerState.isPlaying) 0.6f + (effectiveBassLevel * 0.4f) else 0.3f
                val waveScale = animatedScale * 1.15f
                
                Box(
                    modifier = Modifier
                        .size(340.dp)
                        .graphicsLayer {
                            scaleX = waveScale
                            scaleY = waveScale
                            alpha = waveAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                radius = 400f
                            ),
                            shape = CircleShape
                        )
                )
                
                // Среднее кольцо - пульсирует с битами
                val midWaveScale = if (effectiveBeat) animatedScale * 1.1f else animatedScale * 1.08f
                val midWaveAlpha = if (playerState.isPlaying) 0.5f + (effectiveBassLevel * 0.3f) else 0.2f
                
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .graphicsLayer {
                            scaleX = midWaveScale
                            scaleY = midWaveScale
                            alpha = midWaveAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                radius = 350f
                            ),
                            shape = CircleShape
                        )
                )
                
                // Glow effect - реагирует на музыку
                val glowAlpha = if (playerState.isPlaying) 0.5f + (effectiveBassLevel * 0.4f) else 0.3f
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            alpha = glowAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Круглая обложка с тенью
                Surface(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                        .shadow(
                            elevation = 24.dp,
                            shape = CircleShape,
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        ),
                    shape = CircleShape,
                    color = Color(0xFF2A2355)
                ) {
                    AsyncImage(
                        model = currentTrack?.artworkUri,
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.ic_default_album_art),
                        placeholder = painterResource(R.drawable.ic_default_album_art)
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

                // Эквалайзер и эффекты
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
                        text = "Эффекты",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}
