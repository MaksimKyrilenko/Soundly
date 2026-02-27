package com.example.soundly.presentation.screens.equalizer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.soundly.domain.model.*
import com.example.soundly.domain.model.AudioExportSettings
import com.example.soundly.presentation.theme.backgroundGradient
import com.example.soundly.presentation.theme.LocalColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(navController: NavController, viewModel: EqualizerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    // Диалог сохранения пресета
    if (uiState.showSavePresetDialog) {
        SavePresetDialog(
            presetName = uiState.newPresetName,
            onNameChange = viewModel::setNewPresetName,
            onConfirm = { viewModel.saveUserPreset(uiState.newPresetName) },
            onDismiss = viewModel::hideSavePresetDialog
        )
    }
    
    // Диалог экспорта
    if (uiState.showExportDialog) {
        ExportDialog(
            customName = uiState.exportCustomName,
            onNameChange = viewModel::setExportCustomName,
            onConfirm = { viewModel.exportCurrentTrack(uiState.exportCustomName) },
            onDismiss = viewModel::hideExportDialog
        )
    }
    
    // Диалог прогресса экспорта
    when (val exportState = uiState.exportState) {
        is ExportState.Exporting -> {
            ExportProgressDialog(
                progress = exportState.progress,
                message = exportState.message
            )
        }
        is ExportState.Success -> {
            ExportSuccessDialog(
                trackTitle = exportState.track.title,
                message = exportState.message,
                onDismiss = viewModel::resetExportState
            )
        }
        is ExportState.Error -> {
            ExportErrorDialog(
                message = exportState.message,
                onDismiss = viewModel::resetExportState
            )
        }
        else -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Эквалайзер") },
                navigationIcon = { 
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") 
                    } 
                },
                actions = {
                    // Кнопка сброса всех настроек
                    IconButton(onClick = { viewModel.resetAllSettings() }) {
                        Icon(Icons.Default.Refresh, "Сбросить всё", tint = LocalColorPalette.current.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(backgroundGradient())
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Скорость и тон
            SpeedCard(
                speed = uiState.playbackSpeed,
                pitch = uiState.pitch,
                playbackMode = uiState.playbackMode,
                isPro = true, // Всегда показываем полный функционал
                preservePitch = uiState.preservePitch,
                onModeChange = viewModel::setPlaybackMode,
                onSpeedChange = viewModel::setSpeed,
                onPitchChange = viewModel::setPitch,
                onPreservePitchChange = viewModel::setPreservePitch,
                onReset = viewModel::resetPlayback
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Готовые пресеты
            PresetsRow(
                presets = uiState.presets, 
                selectedId = uiState.currentPresetId, 
                enabled = uiState.isEnabled, 
                onSelect = viewModel::selectPreset
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Эквалайзер (10 полос)
            BandsCard(
                bands = uiState.bands, 
                enabled = uiState.isEnabled, 
                isPro = true, // Всегда показываем все полосы
                onChange = viewModel::setBandValue, 
                onReset = viewModel::resetBand, 
                haptic = haptic
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Усиление баса
            BassCard(
                amount = uiState.bassEnhancerAmount, 
                freq = uiState.bassEnhancerFrequency, 
                mode = uiState.bassEnhancerMode, 
                enabled = uiState.isEnabled, 
                isPro = true,
                onAmountChange = viewModel::setBassEnhancerAmount, 
                onFreqChange = viewModel::setBassEnhancerFrequency, 
                onModeChange = viewModel::setBassEnhancerMode
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Пользовательские пресеты
            UserPresetsCard(
                userPresets = uiState.userPresets,
                onLoadPreset = viewModel::loadUserPreset,
                onDeletePreset = viewModel::deleteUserPreset,
                onSavePreset = viewModel::showSavePresetDialog
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Кнопка экспорта
            ExportCard(
                canExport = viewModel.canExport(),
                currentTrack = viewModel.getCurrentTrack(),
                settings = viewModel.getCurrentExportSettings(),
                onExportClick = viewModel::showExportDialog
            )
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SpeedCard(
    speed: Float, 
    pitch: Float,
    playbackMode: PlaybackMode,
    isPro: Boolean,
    preservePitch: Boolean,
    onModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPreservePitchChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val palette = LocalColorPalette.current
    
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(Modifier.padding(16.dp)) {
            // Заголовок с текущими значениями
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Скорость и тон", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${String.format("%.2f", speed)}x", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("pitch: ${String.format("%.2f", pitch)}x", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset() }) {
                        Icon(Icons.Default.Refresh, "Сбросить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Быстрые пресеты
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Быстрые пресеты", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            }
            Spacer(Modifier.height(8.dp))
            
            // Первый ряд - базовые
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    playbackMode == PlaybackMode.DAYCORE,
                    { onModeChange(PlaybackMode.DAYCORE) },
                    { Text("Daycore", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    playbackMode == PlaybackMode.NORMAL,
                    { onModeChange(PlaybackMode.NORMAL) },
                    { Text("Normal", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    playbackMode == PlaybackMode.NIGHTCORE,
                    { onModeChange(PlaybackMode.NIGHTCORE) },
                    { Text("Nightcore", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Второй ряд - time-stretch
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    playbackMode == PlaybackMode.HALF_TIME,
                    { onModeChange(PlaybackMode.HALF_TIME) },
                    { Text("Half Time", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    playbackMode == PlaybackMode.DOUBLE_TIME,
                    { onModeChange(PlaybackMode.DOUBLE_TIME) },
                    { Text("Double Time", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    playbackMode == PlaybackMode.SPEED_ONLY,
                    { onModeChange(PlaybackMode.SPEED_ONLY) },
                    { Text("Custom", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Эффекты - сетка карточек
            Text("Эффекты", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Spacer(Modifier.height(12.dp))
            
            // Данные эффектов с иконками Material
            val effects = listOf(
                EffectData(PlaybackMode.CHILLCORE, "chill", "Chill", "0.85x", 0xFF8B5CF6),
                EffectData(PlaybackMode.SLOWED_REVERB, "slowed", "Slowed", "0.75x", 0xFF06B6D4),
                EffectData(PlaybackMode.PHONK, "phonk", "Phonk", "0.9x", 0xFFEF4444),
                EffectData(PlaybackMode.HYPERCORE, "hyper", "Hyper", "1.3x", 0xFFF59E0B),
                EffectData(PlaybackMode.HARDSTYLE, "hard", "Hard", "1.0x", 0xFFEC4899)
            )
            
            // Первый ряд - 3 карточки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                effects.take(3).forEach { effect ->
                    AnimatedEffectCard(
                        effect = effect,
                        isSelected = playbackMode == effect.mode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onModeChange(effect.mode)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Второй ряд - 2 карточки + пустое место
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                effects.drop(3).forEach { effect ->
                    AnimatedEffectCard(
                        effect = effect,
                        isSelected = playbackMode == effect.mode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onModeChange(effect.mode)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Пустое место для выравнивания
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = palette.textSecondary.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))
            
            // Ползунок скорости
            Text("Скорость воспроизведения", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Spacer(Modifier.height(4.dp))
            
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("0.25x", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.25f..3.0f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("3.0x", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            
            // Быстрые кнопки скорости
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                    TextButton(
                        onClick = { onSpeedChange(s) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${s}x",
                            fontSize = 11.sp,
                            fontWeight = if (kotlin.math.abs(speed - s) < 0.01f) FontWeight.Bold else FontWeight.Normal,
                            color = if (kotlin.math.abs(speed - s) < 0.01f) MaterialTheme.colorScheme.primary else palette.textSecondary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Ползунок питча
            Text("Высота тона (Pitch)", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Spacer(Modifier.height(4.dp))
            
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("0.25x", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Slider(
                    value = pitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.25f..3.0f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )
                Text("3.0x", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            
            // Быстрые кнопки питча
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { p ->
                    TextButton(
                        onClick = { onPitchChange(p) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${p}x",
                            fontSize = 11.sp,
                            fontWeight = if (kotlin.math.abs(pitch - p) < 0.01f) FontWeight.Bold else FontWeight.Normal,
                            color = if (kotlin.math.abs(pitch - p) < 0.01f) MaterialTheme.colorScheme.secondary else palette.textSecondary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Переключатель связи pitch и speed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Связать pitch со скоростью", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                    Text(
                        if (preservePitch) "Pitch сохраняется (time-stretch)" else "Pitch меняется вместе со скоростью",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
                Switch(
                    checked = !preservePitch,
                    onCheckedChange = { onPreservePitchChange(!it) }
                )
            }
            
            // PRO режим - дополнительные настройки
            if (isPro) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = palette.textSecondary.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                
                Text("PRO настройки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                
                // Семитоны для pitch
                val semitones = ((kotlin.math.ln(pitch.toDouble()) / kotlin.math.ln(2.0)) * 12).toInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Полутона: ${if (semitones >= 0) "+" else ""}$semitones", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                    Text("BPM множитель: ${String.format("%.0f", speed * 100)}%", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Точная настройка в центах
                val cents = ((kotlin.math.ln(pitch.toDouble()) / kotlin.math.ln(2.0)) * 1200).toInt() % 100
                Text("Центы: ${if (cents >= 0) "+" else ""}$cents", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        }
    }
}

@Composable
fun PresetsRow(presets: List<EqualizerPresetV2>, selectedId: String, enabled: Boolean, onSelect: (EqualizerPresetV2) -> Unit) {
    val palette = LocalColorPalette.current
    Text("Пресеты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp), color = palette.textPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(presets) { preset -> FilterChip(selectedId == preset.id, { onSelect(preset) }, { Text(preset.name) }, enabled = enabled) }
    }
}

@Composable
fun BandsCard(bands: List<Float>, enabled: Boolean, isPro: Boolean, onChange: (Int, Float) -> Unit, onReset: (Int) -> Unit, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(Modifier.padding(16.dp)) {
            Text("10-полосный эквалайзер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            if (isPro) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("Sub", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Mud", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Presence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("Clarity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                bands.forEachIndexed { i, v ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset(i) }) }) {
                        Text(if (v >= 0) "+${v.toInt()}" else "${v.toInt()}", fontSize = 8.sp,
                            color = if (v > 0) MaterialTheme.colorScheme.primary else if (v < 0) MaterialTheme.colorScheme.error else palette.textSecondary)
                        Slider(v, { onChange(i, it) }, valueRange = -12f..12f, enabled = enabled, modifier = Modifier.weight(1f))
                        Text(EQ_FREQUENCY_LABELS[i], fontSize = 7.sp, color = palette.textSecondary)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun PreampCard(preamp: Float, autoGain: Boolean, enabled: Boolean, onChange: (Float) -> Unit, onAutoGainToggle: () -> Unit) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Preamp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Text("${if (preamp >= 0) "+" else ""}${String.format("%.1f", preamp)} dB", color = MaterialTheme.colorScheme.primary)
            }
            Slider(preamp, onChange, valueRange = -6f..6f, enabled = enabled)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto Gain", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                Switch(autoGain, { onAutoGainToggle() }, enabled = enabled)
            }
        }
        }
    }
}

@Composable
fun BassCard(amount: Float, freq: Int, mode: BassEnhancerMode, enabled: Boolean, isPro: Boolean, onAmountChange: (Float) -> Unit, onFreqChange: (Int) -> Unit, onModeChange: (BassEnhancerMode) -> Unit) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(Modifier.padding(16.dp)) {
            Text("Усиление басов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Slider(amount, onAmountChange, valueRange = 0f..100f, enabled = enabled, modifier = Modifier.weight(1f))
                Text("${amount.toInt()}%", modifier = Modifier.width(40.dp), color = palette.textPrimary)
            }
            if (isPro) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Частота", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 80, 100).forEach { f -> FilterChip(freq == f, { onFreqChange(f) }, { Text("${f}Hz") }, enabled = enabled) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Режим", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(mode == BassEnhancerMode.SOFT, { onModeChange(BassEnhancerMode.SOFT) }, { Text("Soft") }, enabled = enabled)
                        FilterChip(mode == BassEnhancerMode.HARD, { onModeChange(BassEnhancerMode.HARD) }, { Text("Hard") }, enabled = enabled)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun EffectsCard(stereoWidth: Float, isMono: Boolean, loudness: Boolean, balance: Float, enabled: Boolean, onStereoChange: (Float) -> Unit, onMonoToggle: () -> Unit, onLoudnessToggle: () -> Unit, onBalanceChange: (Float) -> Unit) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(Modifier.padding(16.dp)) {
            Text("Дополнительные эффекты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SurroundSound, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Ширина стерео", color = palette.textPrimary); Slider(stereoWidth, onStereoChange, valueRange = 0f..150f, enabled = enabled && !isMono) }
                Text("${stereoWidth.toInt()}%", modifier = Modifier.width(45.dp), color = palette.textPrimary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Моно", color = palette.textPrimary); Switch(isMono, { onMonoToggle() }, enabled = enabled) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Loudness", color = palette.textPrimary); Switch(loudness, { onLoudnessToggle() }, enabled = enabled) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("L", color = palette.textPrimary); Slider(balance, onBalanceChange, valueRange = -100f..100f, enabled = enabled, modifier = Modifier.weight(1f)); Text("R", color = palette.textPrimary) }
        }
        }
    }
}

@Composable
fun ActionButtons(isComparing: Boolean, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onStartCompare: () -> Unit, onEndCompare: () -> Unit, onCalibrate: () -> Unit, onReset: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton({ }, Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onStartCompare(); tryAwaitRelease(); onEndCompare() }) },
            colors = if (isComparing) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()) {
            Icon(Icons.Default.Compare, null); Spacer(Modifier.width(8.dp)); Text(if (isComparing) "A/B" else "Compare")
        }
        OutlinedButton(onCalibrate, Modifier.weight(1f)) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("Калибровка") }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onReset, Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Сбросить") }
        Button(onSave, Modifier.weight(1f)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Сохранить") }
    }
}

@Composable
fun CalibrationDialog(step: Int, onHeadphone: (HeadphoneType) -> Unit, onBass: (Int) -> Unit, onHighs: (Int) -> Unit, onVolume: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Автокалибровка") }, text = {
        Column {
            when (step) {
                0 -> { Text("Выберите тип наушников:"); Spacer(Modifier.height(16.dp))
                    OutlinedButton({ onHeadphone(HeadphoneType.TWS) }, Modifier.fillMaxWidth()) { Text("TWS (беспроводные)") }
                    OutlinedButton({ onHeadphone(HeadphoneType.WIRED) }, Modifier.fillMaxWidth()) { Text("Проводные") }
                    OutlinedButton({ onHeadphone(HeadphoneType.OVER_EAR) }, Modifier.fillMaxWidth()) { Text("Накладные") }
                }
                1 -> { Text("Как вы оцениваете бас?"); Spacer(Modifier.height(16.dp))
                    OutlinedButton({ onBass(-1) }, Modifier.fillMaxWidth()) { Text("Мало баса") }
                    OutlinedButton({ onBass(0) }, Modifier.fillMaxWidth()) { Text("Нормально") }
                    OutlinedButton({ onBass(1) }, Modifier.fillMaxWidth()) { Text("Много баса") }
                }
                2 -> { Text("Как вы оцениваете высокие?"); Spacer(Modifier.height(16.dp))
                    OutlinedButton({ onHighs(-1) }, Modifier.fillMaxWidth()) { Text("Режут уши") }
                    OutlinedButton({ onHighs(0) }, Modifier.fillMaxWidth()) { Text("Нормально") }
                    OutlinedButton({ onHighs(1) }, Modifier.fillMaxWidth()) { Text("Глухо") }
                }
                3 -> { Text("Как вы оцениваете громкость?"); Spacer(Modifier.height(16.dp))
                    OutlinedButton({ onVolume(-1) }, Modifier.fillMaxWidth()) { Text("Тихо") }
                    OutlinedButton({ onVolume(0) }, Modifier.fillMaxWidth()) { Text("Нормально") }
                }
            }
        }
    }, confirmButton = { }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } })
}

// ==================== EXPORT COMPONENTS ====================

@Composable
fun ExportCard(
    canExport: Boolean,
    currentTrack: Track?,
    settings: AudioExportSettings,
    onExportClick: () -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            palette.cardLight,
                            palette.cardDark
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Экспорт с эффектами",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    if (currentTrack != null && settings.hasChanges()) {
                        Text(
                            "Сохранить \"${currentTrack.title}\" с текущими настройками",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Эффекты: ${settings.generateNameSuffix()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (currentTrack == null) {
                        Text(
                            "Начните воспроизведение трека",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    } else {
                        Text(
                            "Измените настройки эквалайзера",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
                
                Spacer(Modifier.width(12.dp))
                
                Button(
                    onClick = onExportClick,
                    enabled = canExport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = palette.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Экспорт", color = palette.textPrimary)
                }
            }
        }
        }
    }
}

@Composable
fun ExportDialog(
    customName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт трека") },
        text = {
            Column {
                Text(
                    "Трек будет сохранён с применёнными эффектами эквалайзера.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = onNameChange,
                    label = { Text("Название трека") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Файл будет сохранён в Music/Soundly/Exports",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Экспортировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ExportProgressDialog(
    progress: Int,
    message: String
) {
    Dialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                Modifier
                    .background(com.example.soundly.presentation.theme.dialogGradient())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Экспорт трека",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ExportSuccessDialog(
    trackTitle: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { 
            Text(
                "Готово!",
                textAlign = TextAlign.Center
            ) 
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Трек добавлен в вашу библиотеку",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Отлично!")
            }
        }
    )
}

@Composable
fun ExportErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text("Ошибка экспорта") },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}


// ==================== EFFECT CARDS ====================

/**
 * Данные для карточки эффекта
 */
data class EffectData(
    val mode: PlaybackMode,
    val iconType: String, // "chill", "slowed", "phonk", "hyper", "hard"
    val name: String,
    val description: String,
    val color: Long
)

/**
 * Анимированная карточка эффекта с glow и Material иконками
 */
@Composable
fun AnimatedEffectCard(
    effect: EffectData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    // Пружинная анимация масштаба
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "scale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) effectColor else Color.Transparent,
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) effectColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "background"
    )
    
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) effectColor else palette.textSecondary,
        animationSpec = tween(200),
        label = "iconTint"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Material иконка
            Icon(
                imageVector = when (effect.iconType) {
                    "chill" -> Icons.Default.Headphones
                    "slowed" -> Icons.Default.WaterDrop
                    "phonk" -> Icons.Default.Nightlife
                    "hyper" -> Icons.Default.Bolt
                    "hard" -> Icons.Default.RocketLaunch
                    else -> Icons.Default.MusicNote
                },
                contentDescription = effect.name,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Название
            Text(
                text = effect.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) effectColor else palette.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
            
            // Описание
            Text(
                text = effect.description,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}


// ==================== ADVANCED EFFECTS CARDS ====================

@Composable
fun ReverbCard(
    settings: ReverbSettings,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRoomSizeChange: (Float) -> Unit,
    onDecayChange: (Float) -> Unit,
    onWetDryChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Waves, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Реверберация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    Switch(checked = settings.enabled, onCheckedChange = { onToggle() }, enabled = enabled)
                }
                
                AnimatedVisibility(visible = settings.enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        
                        // Room Size
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Размер комнаты", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(120.dp))
                            Slider(
                                value = settings.roomSize,
                                onValueChange = onRoomSizeChange,
                                valueRange = 0f..1f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(settings.roomSize * 100).toInt()}%", modifier = Modifier.width(45.dp), color = palette.textSecondary)
                        }
                        
                        // Decay
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Затухание", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(120.dp))
                            Slider(
                                value = settings.decay,
                                onValueChange = onDecayChange,
                                valueRange = 0f..1f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(settings.decay * 100).toInt()}%", modifier = Modifier.width(45.dp), color = palette.textSecondary)
                        }
                        
                        // Wet/Dry Mix
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Wet/Dry", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(120.dp))
                            Slider(
                                value = settings.wetDryMix,
                                onValueChange = onWetDryChange,
                                valueRange = 0f..1f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(settings.wetDryMix * 100).toInt()}%", modifier = Modifier.width(45.dp), color = palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompressorCard(
    settings: CompressorSettings,
    enabled: Boolean,
    onToggle: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onRatioChange: (Float) -> Unit,
    onAttackChange: (Float) -> Unit,
    onReleaseChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Compress, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text("Компрессор", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    Switch(checked = settings.enabled, onCheckedChange = { onToggle() }, enabled = enabled)
                }
                
                AnimatedVisibility(visible = settings.enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        
                        // Threshold
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Порог", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.threshold,
                                onValueChange = onThresholdChange,
                                valueRange = -60f..0f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.threshold.toInt()} dB", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Ratio
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ratio", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.ratio,
                                onValueChange = onRatioChange,
                                valueRange = 1f..20f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${String.format("%.1f", settings.ratio)}:1", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Attack
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Attack", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.attack,
                                onValueChange = onAttackChange,
                                valueRange = 0.1f..100f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.attack.toInt()} ms", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Release
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Release", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.release,
                                onValueChange = onReleaseChange,
                                valueRange = 10f..1000f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.release.toInt()} ms", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoiseGateCard(
    settings: NoiseGateSettings,
    enabled: Boolean,
    onToggle: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onAttackChange: (Float) -> Unit,
    onReleaseChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeOff, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Text("Noise Gate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    Switch(checked = settings.enabled, onCheckedChange = { onToggle() }, enabled = enabled)
                }
                
                AnimatedVisibility(visible = settings.enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        
                        // Threshold
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Порог", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.threshold,
                                onValueChange = onThresholdChange,
                                valueRange = -80f..0f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.threshold.toInt()} dB", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Attack
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Attack", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.attack,
                                onValueChange = onAttackChange,
                                valueRange = 0.1f..50f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${String.format("%.1f", settings.attack)} ms", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Release
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Release", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.release,
                                onValueChange = onReleaseChange,
                                valueRange = 10f..500f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.release.toInt()} ms", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeEsserCard(
    settings: DeEsserSettings,
    enabled: Boolean,
    onToggle: () -> Unit,
    onFrequencyChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onReductionChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("De-Esser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                            Text("Убирает резкие С и Ш", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                    Switch(checked = settings.enabled, onCheckedChange = { onToggle() }, enabled = enabled)
                }
                
                AnimatedVisibility(visible = settings.enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        
                        // Frequency
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Частота", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.frequency,
                                onValueChange = onFrequencyChange,
                                valueRange = 4000f..10000f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(settings.frequency / 1000).toInt()}k Hz", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Threshold
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Порог", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.threshold,
                                onValueChange = onThresholdChange,
                                valueRange = -40f..0f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.threshold.toInt()} dB", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                        
                        // Reduction
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Снижение", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(80.dp))
                            Slider(
                                value = settings.reduction,
                                onValueChange = onReductionChange,
                                valueRange = 0f..12f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${String.format("%.1f", settings.reduction)} dB", modifier = Modifier.width(55.dp), color = palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubBassCard(
    settings: SubBassSettings,
    enabled: Boolean,
    onToggle: () -> Unit,
    onAmountChange: (Float) -> Unit,
    onFrequencyChange: (Int) -> Unit,
    onSubHarmonicsToggle: () -> Unit,
    onSubAmountChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speaker, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Sub-Bass Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                            Text("Генерация октавы ниже", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                    Switch(checked = settings.enabled, onCheckedChange = { onToggle() }, enabled = enabled)
                }
                
                AnimatedVisibility(visible = settings.enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        
                        // Amount
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Уровень", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(100.dp))
                            Slider(
                                value = settings.amount,
                                onValueChange = onAmountChange,
                                valueRange = 0f..100f,
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.amount.toInt()}%", modifier = Modifier.width(45.dp), color = palette.textSecondary)
                        }
                        
                        // Frequency
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Частота среза", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(40, 60, 80, 100, 120).forEach { f ->
                                    FilterChip(
                                        selected = settings.frequency == f,
                                        onClick = { onFrequencyChange(f) },
                                        label = { Text("${f}Hz", fontSize = 10.sp) },
                                        enabled = enabled
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Sub-harmonics toggle
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Сабгармоники", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                            Switch(checked = settings.subHarmonics, onCheckedChange = { onSubHarmonicsToggle() }, enabled = enabled)
                        }
                        
                        // Sub-harmonics amount
                        AnimatedVisibility(visible = settings.subHarmonics) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Уровень саб", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.width(100.dp))
                                Slider(
                                    value = settings.subAmount,
                                    onValueChange = onSubAmountChange,
                                    valueRange = 0f..100f,
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${settings.subAmount.toInt()}%", modifier = Modifier.width(45.dp), color = palette.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpectrumAnalyzerCard(
    enabled: Boolean,
    data: FloatArray,
    onToggle: () -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Анализатор спектра", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    Switch(checked = enabled, onCheckedChange = { onToggle() })
                }
                
                AnimatedVisibility(visible = enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        
                        // Spectrum bars visualization
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.cardDark.copy(alpha = 0.5f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            data.forEachIndexed { index, value ->
                                val animatedHeight by animateFloatAsState(
                                    targetValue = value.coerceIn(0f, 1f),
                                    animationSpec = tween(50),
                                    label = "bar_$index"
                                )
                                
                                val barColor = when {
                                    index < 8 -> MaterialTheme.colorScheme.primary
                                    index < 16 -> MaterialTheme.colorScheme.secondary
                                    index < 24 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .fillMaxHeight(animatedHeight.coerceAtLeast(0.05f))
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                        .background(barColor.copy(alpha = 0.8f))
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Frequency labels
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("20Hz", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            Text("200Hz", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            Text("2kHz", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            Text("20kHz", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserPresetsCard(
    userPresets: List<UserPreset>,
    onLoadPreset: (UserPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSavePreset: () -> Unit
) {
    val palette = LocalColorPalette.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(palette.cardLight, palette.cardDark)),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Мои пресеты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    IconButton(onClick = onSavePreset) {
                        Icon(Icons.Default.Add, "Сохранить пресет", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                if (userPresets.isEmpty()) {
                    Text(
                        "Нет сохранённых пресетов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    userPresets.forEach { preset ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLoadPreset(preset) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                                Text(
                                    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(preset.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary
                                )
                            }
                            IconButton(onClick = { onDeletePreset(preset.id) }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = palette.textSecondary.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
fun SavePresetDialog(
    presetName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalColorPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardMid,
        title = { Text("Сохранить пресет", color = palette.textPrimary) },
        text = {
            Column {
                Text(
                    "Текущие настройки эквалайзера будут сохранены",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = onNameChange,
                    label = { Text("Название пресета") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = presetName.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
