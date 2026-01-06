package com.example.soundly.presentation.screens.playlist

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.example.soundly.domain.model.Track
import com.example.soundly.presentation.components.TrackSelectionDialog
import com.example.soundly.presentation.navigation.Screen
import com.example.soundly.presentation.theme.backgroundGradient
import com.example.soundly.presentation.theme.LocalColorPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    playlistId: String,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.detailUiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf<Track?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(playlistId) { viewModel.loadPlaylistDetail(playlistId) }
    
    val playlist = uiState.playlist
    val tracks = uiState.tracks
    val totalDuration = remember(tracks) {
        val ms = tracks.sumOf { it.duration }
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        if (h > 0) "${h}ч ${m}мин" else "${m} мин"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, "Редактировать") }
                    IconButton(onClick = { showAddTracksDialog = true }) { Icon(Icons.Default.Add, "Добавить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            playlist == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, null, Modifier.size(64.dp), MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text("Плейлист не найден")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("Назад") }
                }
            }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(backgroundGradient()).padding(padding)) {
                item { PDHeader(playlist.name, playlist.description, playlist.coverUri, tracks.size, totalDuration, { viewModel.playPlaylist() }, { viewModel.playPlaylistShuffled() }, tracks.isNotEmpty()) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Треки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FilledTonalIconButton(onClick = { showAddTracksDialog = true }, Modifier.size(36.dp)) { Icon(Icons.Default.Add, "Добавить", Modifier.size(20.dp)) }
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(80.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text("Плейлист пуст", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { showAddTracksDialog = true }, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить треки") }
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                        PDTrackItem(track, i + 1, playerState.currentTrack?.id == track.id && playerState.isPlaying, playerState.currentTrack?.id == track.id, { viewModel.playTrack(track) }, { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showTrackMenu = track }, Modifier.animateItem())
                    }
                    item { Spacer(Modifier.height(140.dp)) }
                }
            }
        }
        if (playerState.currentTrack != null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.BottomCenter) {
                PDMiniPlayer(playerState.currentTrack!!, playerState.isPlaying, if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration else 0f, { viewModel.playPause() }, { viewModel.playNext() }, { viewModel.playPrevious() }, { navController.navigate(Screen.Player.route) })
            }
        }
    }
    if (showEditDialog && playlist != null) { EditPlaylistDialog(playlist, { showEditDialog = false }, { n, d, c -> viewModel.updatePlaylistInfo(playlistId, n, d, c); showEditDialog = false }) }
    if (showAddTracksDialog) {
        val avail = uiState.allTracks.filter { t -> t.id !in tracks.map { it.id } }
        var sel by remember { mutableStateOf(setOf<String>()) }
        var q by remember { mutableStateOf("") }
        TrackSelectionDialog(avail, sel, { id -> sel = if (id in sel) sel - id else sel + id }, { showAddTracksDialog = false; sel = emptySet() }, { viewModel.addTracksToPlaylist(playlistId, sel.toList()); showAddTracksDialog = false; sel = emptySet() }, "Добавить треки", q, { q = it })
    }
    showTrackMenu?.let { t -> PDTrackMenu(t, { showTrackMenu = null }, { viewModel.playTrack(t); showTrackMenu = null }, { showDeleteConfirm = t; showTrackMenu = null }) }
    showDeleteConfirm?.let { t ->
        AlertDialog({ showDeleteConfirm = null }, { Button({ viewModel.removeTrackFromPlaylist(playlistId, t.id); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") } }, Modifier, { TextButton({ showDeleteConfirm = null }) { Text("Отмена") } }, { Icon(Icons.Default.RemoveCircle, null) }, { Text("Удалить из плейлиста?") }, { Text("Трек \"${t.title}\" будет удалён.") })
    }
}


@Composable
fun PDHeader(name: String, desc: String, cover: String?, cnt: Int, dur: String, onPlay: () -> Unit, onShuffle: () -> Unit, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val tr = rememberInfiniteTransition(label = "c")
        val sc by tr.animateFloat(1f, 1.02f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "s")
        Surface(Modifier.size(180.dp).scale(sc), RoundedCornerShape(24.dp), MaterialTheme.colorScheme.primaryContainer, shadowElevation = 16.dp) {
            Box(contentAlignment = Alignment.Center) {
                when { cover?.startsWith("emoji:") == true -> Text(cover.removePrefix("emoji:"), style = MaterialTheme.typography.displayLarge)
                    cover != null -> AsyncImage(cover, "Cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (desc.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(4.dp)); Text("$cnt треков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(4.dp)); Text(dur, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f), enabled = enabled, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 14.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Воспроизвести") }
            OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f), enabled = enabled, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 14.dp)) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Перемешать") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PDTrackItem(track: Track, idx: Int, playing: Boolean, current: Boolean, onClick: () -> Unit, onMenu: () -> Unit, mod: Modifier = Modifier) {
    Surface(mod.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMenu), color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) else Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(32.dp), Alignment.Center) { if (playing) PDPlayingIndicator() else Text("$idx", style = MaterialTheme.typography.bodyMedium, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(12.dp))
            Surface(Modifier.size(48.dp), RoundedCornerShape(8.dp), MaterialTheme.colorScheme.surfaceVariant) { AsyncImage(track.artworkUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(fmtDur(track.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onMenu, Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Меню", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun PDPlayingIndicator() {
    val tr = rememberInfiniteTransition(label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
        repeat(3) { i -> val h by tr.animateFloat(4f, 16f, infiniteRepeatable(tween(400, i * 100), RepeatMode.Reverse), label = "b$i"); Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDTrackMenu(track: Track, onDismiss: () -> Unit, onPlay: () -> Unit, onRemove: () -> Unit) {
    ModalBottomSheet(onDismiss, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(56.dp), RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceVariant) { AsyncImage(track.artworkUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(modifier = Modifier.clickable(onClick = onPlay), headlineContent = { Text("Воспроизвести") }, leadingContent = { Icon(Icons.Default.PlayArrow, null) })
            ListItem(modifier = Modifier.clickable(onClick = onRemove), headlineContent = { Text("Удалить из плейлиста", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) })
        }
    }
}

@Composable
fun PDMiniPlayer(track: Track, playing: Boolean, progress: Float, onPP: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clickable(onClick = onClick), RoundedCornerShape(20.dp), tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column {
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(2.dp), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), RoundedCornerShape(10.dp), MaterialTheme.colorScheme.surfaceVariant) { AsyncImage(track.artworkUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                IconButton(onPrev, Modifier.size(40.dp)) { Icon(Icons.Default.SkipPrevious, "Пред", Modifier.size(24.dp)) }
                FilledIconButton(onPP, Modifier.size(44.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", Modifier.size(26.dp)) }
                IconButton(onNext, Modifier.size(40.dp)) { Icon(Icons.Default.SkipNext, "След", Modifier.size(24.dp)) }
            }
        }
    }
}

fun fmtDur(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
