package com.example.soundly.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundly.domain.model.Track

@Composable
fun TrackContextMenu(
    track: Track,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onShare: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Воспроизвести следующим") },
            onClick = {
                onPlayNext()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) }
        )

        DropdownMenuItem(
            text = { Text("Добавить в очередь") },
            onClick = {
                onAddToQueue()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null) }
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Добавить в плейлист") },
            onClick = {
                onAddToPlaylist()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
        )

        DropdownMenuItem(
            text = { Text(if (track.isFavorite) "Удалить из избранного" else "Добавить в избранное") },
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
            }
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Перейти к исполнителю") },
            onClick = {
                onGoToArtist()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        DropdownMenuItem(
            text = { Text("Перейти к альбому") },
            onClick = {
                onGoToAlbum()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) }
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Поделиться") },
            onClick = {
                onShare()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
        )
    }
}
