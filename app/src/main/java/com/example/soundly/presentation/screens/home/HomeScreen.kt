package com.example.soundly.presentation.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.domain.model.Track
import com.example.soundly.presentation.components.AddToPlaylistDialog
import com.example.soundly.presentation.components.DeleteConfirmDialog
import com.example.soundly.presentation.components.EditTrackDialog
import com.example.soundly.presentation.components.PlayingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Локальные", "Популярное", "Избранное")
    val context = LocalContext.current
    
    // Dialog states
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.refreshTracks()
        }
    }

    // Check and request permissions on first load only
    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == 
                PermissionChecker.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == 
                PermissionChecker.PERMISSION_GRANTED
        }
        
        if (hasPermission) {
            // Only load if not already loaded
            viewModel.initialLoadIfNeeded()
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Поиск треков...") },
            leadingIcon = { 
                Icon(
                    Icons.Outlined.Search, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Tabs
        TabRow(
            selectedTabIndex = uiState.selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.selectedTab == index,
                    onClick = { viewModel.onTabSelected(index) },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index == 2) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (uiState.selectedTab == index) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                title,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Track count and refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Найдено: ${uiState.tracks.size} треков",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { viewModel.refreshTracks() }) {
                Text(
                    "Обновить",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val displayTracks = when (uiState.selectedTab) {
                2 -> uiState.favoriteTracks
                else -> uiState.tracks
            }

            if (displayTracks.isEmpty() && !uiState.isRefreshing) {
                EmptyState(
                    message = when (uiState.selectedTab) {
                        2 -> "Нет избранных треков"
                        else -> "Треки не найдены"
                    },
                    onRequestPermission = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions)
                    }
                )
            } else {
                val playerState by viewModel.playerState.collectAsState()
                val currentTrackId = playerState.currentTrack?.id
                val isPlaying = playerState.isPlaying
                
                @OptIn(ExperimentalMaterial3Api::class)
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refreshTracks() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 160.dp)
                    ) {
                        items(displayTracks, key = { it.id }) { track ->
                            TrackItem(
                                track = track,
                                onClick = { viewModel.playTrack(track) },
                                onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                                onAddToPlaylistClick = {
                                    selectedTrackForPlaylist = track
                                    showAddToPlaylistDialog = true
                                },
                                onEditClick = { title, artist, album ->
                                    viewModel.updateTrack(track.id, title, artist, album)
                                },
                                onDeleteClick = { viewModel.deleteTrack(track.id) },
                                isCurrentTrack = track.id == currentTrackId,
                                isPlaying = track.id == currentTrackId && isPlaying
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Add to playlist dialog
    if (showAddToPlaylistDialog && selectedTrackForPlaylist != null) {
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { 
                showAddToPlaylistDialog = false
                selectedTrackForPlaylist = null
            },
            onPlaylistSelected = { playlistId ->
                selectedTrackForPlaylist?.let { track ->
                    viewModel.addTrackToPlaylist(playlistId, track.id)
                }
                showAddToPlaylistDialog = false
                selectedTrackForPlaylist = null
            },
            onCreateNew = {
                // Navigate to create playlist or show create dialog
                showAddToPlaylistDialog = false
                selectedTrackForPlaylist = null
            }
        )
    }
}

@Composable
fun TrackItem(
    track: Track,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit = {},
    onEditClick: ((String, String, String) -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column {
        ListItem(
            modifier = Modifier
                .clickable(onClick = onClick)
                .then(
                    if (isCurrentTrack) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    } else Modifier
                ),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            headlineContent = {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (track.isLocal) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Локальный",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = "Album art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // Playing indicator overlay
                    if (isCurrentTrack) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingIndicator(
                                isPlaying = isPlaying,
                                barCount = 4,
                                barWidth = 3.dp,
                                maxBarHeight = 20.dp,
                                minBarHeight = 6.dp,
                                barColor = Color.White,
                                spacing = 3.dp
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Меню",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (track.isFavorite) "Удалить из избранного" else "В избранное") },
                                onClick = {
                                    showMenu = false
                                    onFavoriteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Добавить в плейлист") },
                                onClick = { 
                                    showMenu = false
                                    onAddToPlaylistClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                }
                            )
                            if (onEditClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Редактировать") },
                                    onClick = {
                                        showMenu = false
                                        showEditDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                            }
                            if (onDeleteClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Удалить") },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )
        // Gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isCurrentTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            if (isCurrentTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
    
    if (showEditDialog && onEditClick != null) {
        EditTrackDialog(
            track = track,
            onDismiss = { showEditDialog = false },
            onSave = { title, artist, album ->
                onEditClick(title, artist, album)
                showEditDialog = false
            }
        )
    }
    
    if (showDeleteDialog && onDeleteClick != null) {
        DeleteConfirmDialog(
            title = "Удалить трек?",
            message = "Трек \"${track.title}\" будет удалён",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDeleteClick()
                showDeleteDialog = false
            }
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    onRequestPermission: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Разрешить доступ к музыке")
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
