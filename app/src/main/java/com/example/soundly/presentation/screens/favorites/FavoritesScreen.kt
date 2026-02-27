package com.example.soundly.presentation.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.R
import com.example.soundly.domain.model.Track
import com.example.soundly.presentation.screens.home.formatDuration
import com.example.soundly.presentation.theme.backgroundGradient
import com.example.soundly.presentation.theme.LocalColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    navController: NavController,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val palette = LocalColorPalette.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Избранное", color = palette.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = palette.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient())
        ) {
            if (uiState.tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x30FFFFFF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет избранных треков",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Добавляйте треки в избранное нажатием на ❤️",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.playAll() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.textPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Воспроизвести", color = palette.textPrimary)
                            }
                            OutlinedButton(
                                onClick = { viewModel.shufflePlay() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = palette.textPrimary
                                )
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Перемешать")
                            }
                        }
                    }

                    items(uiState.tracks, key = { it.id }) { track ->
                        FavoriteTrackItem(
                            track = track,
                            onClick = { viewModel.playTrack(track) },
                            onRemoveClick = { viewModel.removeFromFavorites(track.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteTrackItem(
    track: Track,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            palette.cardDark,
                            palette.cardMid,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_default_album_art),
                placeholder = painterResource(R.drawable.ic_default_album_art)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                Text(
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            
            // Duration and favorite button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Удалить из избранного",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        }
    }
}
