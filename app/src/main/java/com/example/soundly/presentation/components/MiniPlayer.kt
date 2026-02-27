package com.example.soundly.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.soundly.presentation.theme.miniPlayerGradient
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.soundly.R
import com.example.soundly.presentation.screens.home.HomeViewModel
import com.example.soundly.presentation.screens.home.formatDuration
import com.example.soundly.presentation.theme.LocalColorPalette

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val currentTrack = playerState.currentTrack
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
            repeatMode = RepeatMode.Reverse
        ),
        label = "fallbackPulse"
    )
    
    // Используем данные от AudioReactiveController или fallback
    val effectiveBassLevel = if (bassLevel > 0.01f) bassLevel else (fallbackPulse * 0.5f)
    val effectiveBeat = beatDetected || (fallbackPulse > 0.85f && playerState.isPlaying)
    val effectiveScale = if (coverScale > 1.01f) {
        coverScale
    } else {
        // Fallback: более заметная пульсация
        1f + (fallbackPulse * 0.12f)
    }
    
    val progress by animateFloatAsState(
        targetValue = if (playerState.duration > 0) {
            playerState.currentPosition.toFloat() / playerState.duration.toFloat()
        } else 0f,
        label = "progress"
    )

    AnimatedVisibility(
        visible = currentTrack != null,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        currentTrack?.let { track ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(20.dp),
                color = palette.cardDark
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    palette.cardMid,
                                    palette.cardDark,
                                    palette.backgroundMid
                                )
                            )
                        )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Album art with animated pulsing effect
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Аудио-реактивная анимация с визуальными волнами
                                // Используем scale напрямую от AudioReactiveController
                                val animatedScale by animateFloatAsState(
                                    targetValue = if (playerState.isPlaying) effectiveScale else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "audioReactiveScale"
                                )
                                
                                // Внешнее свечение - волны
                                if (playerState.isPlaying) {
                                    val waveAlpha = 0.5f + (effectiveBassLevel * 0.5f)
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .graphicsLayer {
                                                scaleX = animatedScale * 1.1f
                                                scaleY = animatedScale * 1.1f
                                                alpha = waveAlpha
                                            }
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                }
                                
                                // Круглая обложка с анимацией
                                AsyncImage(
                                    model = track.artworkUri,
                                    contentDescription = "Album art",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                        }
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(R.drawable.ic_default_album_art),
                                    placeholder = painterResource(R.drawable.ic_default_album_art)
                                )
                            }

                            // Track info
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = palette.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // Time info
                                Text(
                                    text = "${formatDuration(playerState.currentPosition)} / ${formatDuration(playerState.duration)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Previous
                                IconButton(
                                    onClick = { viewModel.playPrevious() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Предыдущий",
                                        modifier = Modifier.size(24.dp),
                                        tint = palette.textPrimary
                                    )
                                }

                                // Play/Pause
                                Surface(
                                    onClick = { viewModel.playPause() },
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = palette.cardLight
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (playerState.isPlaying) "Пауза" else "Воспроизвести",
                                            tint = palette.textPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Next
                                IconButton(
                                    onClick = { viewModel.playNext() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Следующий",
                                        modifier = Modifier.size(24.dp),
                                        tint = palette.textPrimary
                                    )
                                }
                            }
                        }

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.textSecondary.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
