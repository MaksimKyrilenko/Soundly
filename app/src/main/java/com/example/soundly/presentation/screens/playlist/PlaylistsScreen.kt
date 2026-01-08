package com.example.soundly.presentation.screens.playlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.soundly.presentation.theme.backgroundGradient
import com.example.soundly.presentation.theme.LocalColorPalette
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.domain.model.Playlist
import com.example.soundly.domain.model.Track
import com.example.soundly.presentation.components.TrackSelectionDialog
import com.example.soundly.presentation.navigation.Screen

enum class PlaylistSortOption(val label: String, val icon: ImageVector) {
    NAME_ASC("По названию (А-Я)", Icons.AutoMirrored.Filled.Sort),
    NAME_DESC("По названию (Я-А)", Icons.AutoMirrored.Filled.Sort),
    DATE_NEW("Сначала новые", Icons.Default.Schedule),
    DATE_OLD("Сначала старые", Icons.Default.History),
    TRACKS_COUNT("По количеству треков", Icons.Default.MusicNote)
}

enum class PlaylistViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    navController: NavController,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val palette = LocalColorPalette.current
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(PlaylistSortOption.DATE_NEW) }
    var viewMode by remember { mutableStateOf(PlaylistViewMode.LIST) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedPlaylistForMenu by remember { mutableStateOf<Playlist?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    val sortedPlaylists = remember(uiState.playlists, sortOption, searchQuery) {
        val filtered = if (searchQuery.isBlank()) uiState.playlists
        else uiState.playlists.filter { 
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
        
        when (sortOption) {
            PlaylistSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            PlaylistSortOption.DATE_NEW -> filtered.sortedByDescending { it.createdAt }
            PlaylistSortOption.DATE_OLD -> filtered.sortedBy { it.createdAt }
            PlaylistSortOption.TRACKS_COUNT -> filtered.sortedByDescending { it.trackIds.size }
        }
    }
    
    val totalTracks = uiState.playlists.sumOf { it.trackIds.size }
    val totalPlaylists = uiState.playlists.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient())
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "header"
        ) { searching ->
            if (searching) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x30FFFFFF)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск плейлистов...", color = palette.textSecondary) },
                        leadingIcon = {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Default.ArrowBack, "Назад", tint = palette.textPrimary)
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Очистить", tint = palette.textPrimary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = palette.textPrimary,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Плейлисты",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "$totalPlaylists плейлистов • $totalTracks треков",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Поиск", tint = palette.textPrimary)
                        }
                        IconButton(onClick = { 
                            viewMode = if (viewMode == PlaylistViewMode.LIST) 
                                PlaylistViewMode.GRID else PlaylistViewMode.LIST
                        }) {
                            Icon(
                                if (viewMode == PlaylistViewMode.LIST) Icons.Default.GridView 
                                else Icons.Default.ViewList,
                                "Вид",
                                tint = palette.textPrimary
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка", tint = palette.textPrimary)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                PlaylistSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { sortOption = option; showSortMenu = false },
                                        leadingIcon = { Icon(option.icon, null) },
                                        trailingIcon = {
                                            if (sortOption == option) {
                                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        QuickActionsRow(
            onCreateClick = { showCreateDialog = true },
            onFavoritesClick = { navController.navigate(Screen.Favorites.route) },
            onRecentClick = { }
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (sortedPlaylists.isEmpty()) {
            if (searchQuery.isNotEmpty()) {
                EmptySearchState(query = searchQuery, onClear = { searchQuery = "" })
            } else {
                EmptyPlaylistsState(modifier = Modifier.fillMaxSize(), onCreateClick = { showCreateDialog = true })
            }
        } else {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "viewMode"
            ) { mode ->
                when (mode) {
                    PlaylistViewMode.LIST -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = sortedPlaylists, key = { it.id }) { playlist ->
                                PlaylistListItem(
                                    playlist = playlist,
                                    onClick = { navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedPlaylistForMenu = playlist },
                                    onPlayClick = { viewModel.loadPlaylistDetail(playlist.id); viewModel.playPlaylist() },
                                    modifier = Modifier.animateItem()
                                )
                            }
                            item { Spacer(modifier = Modifier.height(140.dp)) }
                        }
                    }
                    PlaylistViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = sortedPlaylists, key = { it.id }) { playlist ->
                                PlaylistGridItem(
                                    playlist = playlist,
                                    onClick = { navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedPlaylistForMenu = playlist },
                                    modifier = Modifier.animateItem()
                                )
                            }
                            item { Spacer(modifier = Modifier.height(140.dp)) }
                            item { Spacer(modifier = Modifier.height(140.dp)) }
                        }
                    }
                }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.padding(end = 16.dp, bottom = 100.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, "Создать")
            Spacer(Modifier.width(8.dp))
            Text("Создать")
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            availableTracks = uiState.availableTracks,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, trackIds, coverUri ->
                viewModel.createPlaylistWithTracks(name, description, trackIds, coverUri)
                showCreateDialog = false
            }
        )
    }
    
    selectedPlaylistForMenu?.let { playlist ->
        PlaylistContextMenu(
            playlist = playlist,
            onDismiss = { selectedPlaylistForMenu = null },
            onEdit = { showEditDialog = true },
            onDelete = { showDeleteConfirmDialog = true },
            onShare = { selectedPlaylistForMenu = null },
            onDuplicate = {
                viewModel.createPlaylistWithTracks("${playlist.name} (копия)", playlist.description, playlist.trackIds, playlist.coverUri)
                selectedPlaylistForMenu = null
            }
        )
    }
    
    if (showDeleteConfirmDialog && selectedPlaylistForMenu != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false; selectedPlaylistForMenu = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить плейлист?", color = palette.textPrimary) },
            text = { Text("Плейлист \"${selectedPlaylistForMenu?.name}\" будет удалён.", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { selectedPlaylistForMenu?.let { viewModel.deletePlaylist(it.id) }; showDeleteConfirmDialog = false; selectedPlaylistForMenu = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false; selectedPlaylistForMenu = null }) { Text("Отмена") } },
            containerColor = palette.cardMid
        )
    }
    
    if (showEditDialog && selectedPlaylistForMenu != null) {
        EditPlaylistDialog(
            playlist = selectedPlaylistForMenu!!,
            onDismiss = { showEditDialog = false; selectedPlaylistForMenu = null },
            onSave = { name, description, coverUri ->
                viewModel.updatePlaylistInfo(selectedPlaylistForMenu!!.id, name, description, coverUri)
                showEditDialog = false; selectedPlaylistForMenu = null
            }
        )
    }
}

@Composable
fun QuickActionsRow(onCreateClick: () -> Unit, onFavoritesClick: () -> Unit, onRecentClick: () -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { QuickActionChip(icon = Icons.Default.Add, label = "Создать", onClick = onCreateClick, isPrimary = true) }
        item { QuickActionChip(icon = Icons.Default.Favorite, label = "Избранное", onClick = onFavoritesClick) }
        item { QuickActionChip(icon = Icons.Default.History, label = "Недавние", onClick = onRecentClick) }
    }
}

@Composable
fun QuickActionChip(icon: ImageVector, label: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    val palette = LocalColorPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color(0x30FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (isPrimary) MaterialTheme.colorScheme.primary else palette.textPrimary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (isPrimary) MaterialTheme.colorScheme.primary else palette.textPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListItem(playlist: Playlist, onClick: () -> Unit, onLongClick: () -> Unit, onPlayClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalColorPalette.current
    Surface(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(coverUri = playlist.coverUri, size = 64.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary)
                if (playlist.description.isNotBlank()) {
                    Text(text = playlist.description, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(14.dp), tint = palette.textSecondary)
                    Text(text = "${playlist.trackIds.size} треков", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    if (playlist.isSynced) { Icon(Icons.Default.Cloud, "Синхронизировано", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                }
            }
            if (playlist.trackIds.isNotEmpty()) {
                Surface(onClick = onPlayClick, modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, "Воспроизвести", modifier = Modifier.size(24.dp), tint = Color.White) }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistGridItem(playlist: Playlist, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalColorPalette.current
    Surface(
        modifier = modifier.fillMaxWidth().aspectRatio(0.85f).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PlaylistCover(coverUri = playlist.coverUri, size = 100.dp, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = palette.textPrimary)
            Text(text = "${playlist.trackIds.size} треков", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        }
    }
}

@Composable
fun PlaylistCover(coverUri: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(size), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
        Box(contentAlignment = Alignment.Center) {
            when {
                coverUri?.startsWith("emoji:") == true -> Text(coverUri.removePrefix("emoji:"), style = MaterialTheme.typography.headlineLarge)
                coverUri != null -> AsyncImage(model = coverUri, contentDescription = "Обложка", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size / 2))
            }
        }
    }
}

@Composable
fun EmptyPlaylistsState(modifier: Modifier = Modifier, onCreateClick: () -> Unit) {
    val palette = LocalColorPalette.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            val infiniteTransition = rememberInfiniteTransition(label = "empty")
            val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.1f, animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse), label = "scale")
            Surface(modifier = Modifier.size(120.dp).scale(scale), shape = RoundedCornerShape(32.dp), color = Color(0x40FFFFFF)) {
                Box(contentAlignment = Alignment.Center) { Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Нет плейлистов", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Создайте свой первый плейлист\nи добавьте любимые треки", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onCreateClick, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Создать плейлист", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

@Composable
fun EmptySearchState(query: String, onClear: () -> Unit) {
    val palette = LocalColorPalette.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text("По запросу \"$query\"", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onClear) { Text("Очистить поиск") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistContextMenu(playlist: Playlist, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit, onDuplicate: () -> Unit) {
    val palette = LocalColorPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), containerColor = palette.cardMid) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaylistCover(coverUri = playlist.coverUri, size = 56.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary)
                    Text("${playlist.trackIds.size} треков", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = palette.textSecondary.copy(alpha = 0.2f))
            ContextMenuItem(icon = Icons.Default.Edit, title = "Редактировать", onClick = { onDismiss(); onEdit() })
            ContextMenuItem(icon = Icons.Default.ContentCopy, title = "Дублировать", onClick = { onDismiss(); onDuplicate() })
            ContextMenuItem(icon = Icons.Default.Share, title = "Поделиться", onClick = { onDismiss(); onShare() })
            ContextMenuItem(icon = Icons.Default.Delete, title = "Удалить", onClick = { onDismiss(); onDelete() }, isDestructive = true)
        }
    }
}

@Composable
fun ContextMenuItem(icon: ImageVector, title: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val palette = LocalColorPalette.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, color = if (isDestructive) MaterialTheme.colorScheme.error else palette.textPrimary) },
        leadingContent = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else palette.textSecondary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


@Composable
fun CreatePlaylistDialog(availableTracks: List<Track>, onDismiss: () -> Unit, onCreate: (String, String, List<String>, String?) -> Unit) {
    val palette = LocalColorPalette.current
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var selectedTrackIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    
    val presetIcons = listOf("🎵", "🎶", "🎸", "🎹", "🎺", "🎻", "🥁", "🎤", "❤️", "⭐", "🔥", "💜", "🌙", "☀️", "🌊", "🎧", "💿", "🎼")

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Новый плейлист", fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Surface(modifier = Modifier.size(100.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selectedIcon != null) Text(selectedIcon!!, style = MaterialTheme.typography.displayMedium)
                                else Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text("Выберите иконку", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in presetIcons.chunked(6)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { icon ->
                                    Surface(
                                        modifier = Modifier.size(44.dp).clickable { selectedIcon = if (selectedIcon == icon) null else icon },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selectedIcon == icon) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                        border = if (selectedIcon == icon) null else ButtonDefaults.outlinedButtonBorder
                                    ) { Box(contentAlignment = Alignment.Center) { Text(icon, style = MaterialTheme.typography.titleLarge) } }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Название плейлиста", color = palette.textSecondary) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Title, null, tint = palette.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary, cursorColor = palette.textPrimary, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = palette.textSecondary.copy(alpha = 0.3f))
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it }, label = { Text("Описание (необязательно)", color = palette.textSecondary) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Description, null, tint = palette.textSecondary) }, minLines = 2, maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary, cursorColor = palette.textPrimary, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = palette.textSecondary.copy(alpha = 0.3f))
                    )
                }
            },
            confirmButton = { Button(onClick = { step = 2 }, enabled = name.isNotBlank(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Далее", color = Color.White); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp), tint = Color.White) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
            shape = RoundedCornerShape(28.dp),
            containerColor = LocalColorPalette.current.cardMid
        )
    } else {
        TrackSelectionDialog(
            tracks = availableTracks, selectedTrackIds = selectedTrackIds,
            onTrackToggle = { trackId -> selectedTrackIds = if (trackId in selectedTrackIds) selectedTrackIds - trackId else selectedTrackIds + trackId },
            onDismiss = { step = 1 },
            onConfirm = { val coverUri = selectedIcon?.let { "emoji:$it" }; onCreate(name, description, selectedTrackIds.toList(), coverUri) },
            title = "Добавить треки в \"$name\"", searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it }
        )
    }
}

@Composable
fun EditPlaylistDialog(playlist: Playlist, onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    val palette = LocalColorPalette.current
    var name by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf(playlist.description) }
    var selectedIcon by remember { mutableStateOf(playlist.coverUri?.removePrefix("emoji:")?.takeIf { playlist.coverUri?.startsWith("emoji:") == true }) }
    val presetIcons = listOf("🎵", "🎶", "🎸", "🎹", "🎺", "🎻", "🥁", "🎤", "❤️", "⭐", "🔥", "💜", "🌙", "☀️", "🌊", "🎧", "💿", "🎼")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать плейлист", fontWeight = FontWeight.Bold, color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Surface(modifier = Modifier.size(100.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (selectedIcon != null) Text(selectedIcon!!, style = MaterialTheme.typography.displayMedium)
                            else Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text("Иконка", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in presetIcons.chunked(6)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { icon ->
                                Surface(
                                    modifier = Modifier.size(44.dp).clickable { selectedIcon = if (selectedIcon == icon) null else icon },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedIcon == icon) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                    border = if (selectedIcon == icon) null else ButtonDefaults.outlinedButtonBorder
                                ) { Box(contentAlignment = Alignment.Center) { Text(icon, style = MaterialTheme.typography.titleLarge) } }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Название", color = palette.textSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary, cursorColor = palette.textPrimary, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = palette.textSecondary.copy(alpha = 0.3f))
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Описание", color = palette.textSecondary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary, cursorColor = palette.textPrimary, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = palette.textSecondary.copy(alpha = 0.3f))
                )
            }
        },
        confirmButton = { Button(onClick = { val coverUri = selectedIcon?.let { "emoji:$it" }; onSave(name, description, coverUri) }, enabled = name.isNotBlank(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Сохранить", color = Color.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = LocalColorPalette.current.cardMid
    )
}
