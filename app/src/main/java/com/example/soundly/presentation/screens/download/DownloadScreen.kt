package com.example.soundly.presentation.screens.download

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.soundly.presentation.theme.backgroundGradient
import com.example.soundly.presentation.theme.LocalColorPalette
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun DownloadScreen(
    navController: NavController,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val palette = LocalColorPalette.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient())
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Скачать",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        }

        // Download from YouTube card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.cardLight,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Скачать с YouTube",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Вставьте ссылку на видео",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // URL Input
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = viewModel::onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://youtube.com/watch?v=...", color = palette.textSecondary) },
                        leadingIcon = { 
                            Icon(
                                Icons.Outlined.Link, 
                                contentDescription = null,
                                tint = palette.textSecondary
                            ) 
                        },
                        trailingIcon = {
                            if (uiState.url.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearVideoInfo) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Очистить",
                                        tint = palette.textSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isDownloading && !uiState.isFetching,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = palette.textSecondary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = palette.textPrimary,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                    
                    // Loading indicator for fetching
                    AnimatedVisibility(visible = uiState.isFetching) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.textPrimary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Получение информации...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                    
                    // Video Info Preview
                    AnimatedVisibility(
                        visible = uiState.videoInfo != null && !uiState.isFetching,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        uiState.videoInfo?.let { info ->
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Thumbnail and info
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.cardLight.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    AsyncImage(
                                        model = info.thumbnail,
                                        contentDescription = "Обложка",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = info.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = palette.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = info.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Editable fields
                                OutlinedTextField(
                                    value = uiState.customTitle,
                                    onValueChange = viewModel::onTitleChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Название трека", color = palette.textSecondary) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.MusicNote,
                                            contentDescription = null,
                                            tint = palette.textSecondary
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !uiState.isDownloading,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = palette.textSecondary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        cursorColor = palette.textPrimary,
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textPrimary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = palette.textSecondary
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedTextField(
                                    value = uiState.customArtist,
                                    onValueChange = viewModel::onArtistChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Исполнитель", color = palette.textSecondary) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = palette.textSecondary
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !uiState.isDownloading,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = palette.textSecondary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        cursorColor = palette.textPrimary,
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textPrimary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = palette.textSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download progress or button
                    if (uiState.isDownloading) {
                        Column {
                            LinearProgressIndicator(
                                progress = { uiState.downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = palette.textSecondary.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.statusMessage.ifEmpty { "Загрузка..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    } else {
                        Button(
                            onClick = viewModel::downloadFromYoutube,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.videoInfo != null && uiState.customTitle.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Download, 
                                contentDescription = null, 
                                modifier = Modifier.size(20.dp),
                                tint = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Скачать MP3", color = palette.textPrimary)
                        }
                    }

                    // Error message
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    
                    // Browser fallback button
                    AnimatedVisibility(
                        visible = uiState.showBrowserFallback && uiState.browserFallbackUrl != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Автоматическое скачивание недоступно",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Вы можете скачать трек через браузер и затем переместить файл в папку Music/Soundly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            uiState.browserFallbackUrl?.let { url ->
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.OpenInBrowser,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Открыть в браузере")
                                    }
                                }
                            }
                        }
                    }

                    // Success message
                    uiState.successMessage?.let { message ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textPrimary
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        // How it works card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Как это работает", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HowItWorksStep(1, "Скопируйте ссылку на YouTube видео")
                    HowItWorksStep(2, "Вставьте её в поле выше")
                    HowItWorksStep(3, "Отредактируйте название и исполнителя")
                    HowItWorksStep(4, "Нажмите \"Скачать MP3\"")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Файлы сохраняются в папку Music/Soundly",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
                }
            }
        }

        // Recent downloads
        if (uiState.recentDownloads.isNotEmpty()) {
            item {
                Text(
                    text = "Недавние загрузки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
            }
            items(uiState.recentDownloads) { download ->
                DownloadItemCard(download)
            }
        }

        item { Spacer(modifier = Modifier.height(140.dp)) }
    }
}

@Composable
fun HowItWorksStep(number: Int, text: String) {
    val palette = LocalColorPalette.current
    Row(
        modifier = Modifier.padding(vertical = 6.dp), 
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = palette.cardLight,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text, 
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
            color = palette.textPrimary
        )
    }
}

@Composable
fun DownloadItemCard(item: DownloadItem) {
    val palette = LocalColorPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = item.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Status icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (item.status) {
                    DownloadStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    DownloadStatus.FAILED -> Color(0xFFEF5350).copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (item.status) {
                            DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                            DownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                            DownloadStatus.FAILED -> Icons.Default.Error
                            DownloadStatus.PENDING -> Icons.Default.Schedule
                        },
                        contentDescription = null,
                        tint = when (item.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                            DownloadStatus.FAILED -> Color(0xFFEF5350)
                            else -> Color.White.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        }
    }
}
