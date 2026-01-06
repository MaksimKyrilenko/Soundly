package com.example.soundly.presentation.screens.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.soundly.domain.model.*
import com.example.soundly.domain.model.AudioExportSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(navController: NavController, viewModel: EqualizerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Диалог калибровки
    if (uiState.isCalibrating) {
        CalibrationDialog(uiState.calibrationStep, viewModel::setCalibrationHeadphoneType, viewModel::setCalibrationBassLevel, 
            viewModel::setCalibrationHighsLevel, viewModel::setCalibrationVolumeLevel, viewModel::cancelCalibration)
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("Эквалайзер") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = {
                TextButton(onClick = { viewModel.toggleMode() }) { Text(if (uiState.mode == EqualizerMode.SIMPLE) "PRO" else "SIMPLE") }
                Switch(checked = uiState.isEnabled, onCheckedChange = { viewModel.toggleEnabled() })
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SpeedCard(
                speed = uiState.playbackSpeed,
                pitch = uiState.pitch,
                playbackMode = uiState.playbackMode,
                isPro = uiState.mode == EqualizerMode.PRO,
                preservePitch = uiState.preservePitch,
                onModeChange = viewModel::setPlaybackMode,
                onSpeedChange = viewModel::setSpeed,
                onPitchChange = viewModel::setPitch,
                onPreservePitchChange = viewModel::setPreservePitch,
                onReset = viewModel::resetPlayback
            )
            Spacer(Modifier.height(16.dp))
            PresetsRow(uiState.presets, uiState.currentPresetId, uiState.isEnabled, viewModel::selectPreset)
            Spacer(Modifier.height(16.dp))
            BandsCard(uiState.bands, uiState.isEnabled, uiState.mode == EqualizerMode.PRO, viewModel::setBandValue, viewModel::resetBand, haptic)
            
            if (uiState.mode == EqualizerMode.PRO) {
                Spacer(Modifier.height(16.dp))
                PreampCard(uiState.preamp, uiState.autoGainEnabled, uiState.isEnabled, viewModel::setPreamp, viewModel::toggleAutoGain)
            }
            
            Spacer(Modifier.height(16.dp))
            BassCard(uiState.bassEnhancerAmount, uiState.bassEnhancerFrequency, uiState.bassEnhancerMode, uiState.isEnabled, uiState.mode == EqualizerMode.PRO,
                viewModel::setBassEnhancerAmount, viewModel::setBassEnhancerFrequency, viewModel::setBassEnhancerMode)
            
            if (uiState.mode == EqualizerMode.PRO) {
                Spacer(Modifier.height(16.dp))
                EffectsCard(uiState.stereoWidth, uiState.isMono, uiState.loudnessEnabled, uiState.balanceL, uiState.isEnabled,
                    viewModel::setStereoWidth, viewModel::toggleMono, viewModel::toggleLoudness, viewModel::setBalance)
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Кнопка экспорта
            ExportCard(
                canExport = viewModel.canExport(),
                currentTrack = viewModel.getCurrentTrack(),
                settings = viewModel.getCurrentExportSettings(),
                onExportClick = viewModel::showExportDialog
            )
            
            Spacer(Modifier.height(16.dp))
            ActionButtons(uiState.isComparing, haptic, viewModel::startCompare, viewModel::endCompare, viewModel::startCalibration, viewModel::resetToFlat) { viewModel.saveSettings(); navController.popBackStack() }
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
    
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp)) {
            // Заголовок с текущими значениями
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Скорость и тон", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${String.format("%.2f", speed)}x", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("pitch: ${String.format("%.2f", pitch)}x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset() }) {
                        Icon(Icons.Default.Refresh, "Сбросить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Быстрые пресеты (osu! style)
            Text("⚡ Быстрые пресеты", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            
            // Первый ряд - с изменением тона
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
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            
            // Ползунок скорости
            Text("🎚️ Скорость воспроизведения", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("0.25x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.25f..3.0f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("3.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                            color = if (kotlin.math.abs(speed - s) < 0.01f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Ползунок питча
            Text("🎵 Высота тона (Pitch)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("0.25x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                Text("3.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                            color = if (kotlin.math.abs(pitch - p) < 0.01f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("🔗 Связать pitch со скоростью", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (preservePitch) "Pitch сохраняется (time-stretch)" else "Pitch меняется вместе со скоростью",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                
                Text("🎛️ PRO настройки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                
                // Семитоны для pitch
                val semitones = ((kotlin.math.ln(pitch.toDouble()) / kotlin.math.ln(2.0)) * 12).toInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Полутона: ${if (semitones >= 0) "+" else ""}$semitones", style = MaterialTheme.typography.bodySmall)
                    Text("BPM множитель: ${String.format("%.0f", speed * 100)}%", style = MaterialTheme.typography.bodySmall)
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Точная настройка в центах
                val cents = ((kotlin.math.ln(pitch.toDouble()) / kotlin.math.ln(2.0)) * 1200).toInt() % 100
                Text("Центы: ${if (cents >= 0) "+" else ""}$cents", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PresetsRow(presets: List<EqualizerPresetV2>, selectedId: String, enabled: Boolean, onSelect: (EqualizerPresetV2) -> Unit) {
    Text("Пресеты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(presets) { preset -> FilterChip(selectedId == preset.id, { onSelect(preset) }, { Text(preset.name) }, enabled = enabled) }
    }
}

@Composable
fun BandsCard(bands: List<Float>, enabled: Boolean, isPro: Boolean, onChange: (Int, Float) -> Unit, onReset: (Int) -> Unit, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("10-полосный эквалайзер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                            color = if (v > 0) MaterialTheme.colorScheme.primary else if (v < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(v, { onChange(i, it) }, valueRange = -12f..12f, enabled = enabled, modifier = Modifier.weight(1f))
                        Text(EQ_FREQUENCY_LABELS[i], fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PreampCard(preamp: Float, autoGain: Boolean, enabled: Boolean, onChange: (Float) -> Unit, onAutoGainToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Preamp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${if (preamp >= 0) "+" else ""}${String.format("%.1f", preamp)} dB", color = MaterialTheme.colorScheme.primary)
            }
            Slider(preamp, onChange, valueRange = -6f..6f, enabled = enabled)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto Gain", style = MaterialTheme.typography.bodyMedium)
                Switch(autoGain, { onAutoGainToggle() }, enabled = enabled)
            }
        }
    }
}

@Composable
fun BassCard(amount: Float, freq: Int, mode: BassEnhancerMode, enabled: Boolean, isPro: Boolean, onAmountChange: (Float) -> Unit, onFreqChange: (Int) -> Unit, onModeChange: (BassEnhancerMode) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Усиление басов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Slider(amount, onAmountChange, valueRange = 0f..100f, enabled = enabled, modifier = Modifier.weight(1f))
                Text("${amount.toInt()}%", modifier = Modifier.width(40.dp))
            }
            if (isPro) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Частота", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 80, 100).forEach { f -> FilterChip(freq == f, { onFreqChange(f) }, { Text("${f}Hz") }, enabled = enabled) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Режим", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(mode == BassEnhancerMode.SOFT, { onModeChange(BassEnhancerMode.SOFT) }, { Text("Soft") }, enabled = enabled)
                        FilterChip(mode == BassEnhancerMode.HARD, { onModeChange(BassEnhancerMode.HARD) }, { Text("Hard") }, enabled = enabled)
                    }
                }
            }
        }
    }
}

@Composable
fun EffectsCard(stereoWidth: Float, isMono: Boolean, loudness: Boolean, balance: Float, enabled: Boolean, onStereoChange: (Float) -> Unit, onMonoToggle: () -> Unit, onLoudnessToggle: () -> Unit, onBalanceChange: (Float) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Дополнительные эффекты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SurroundSound, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Ширина стерео"); Slider(stereoWidth, onStereoChange, valueRange = 0f..150f, enabled = enabled && !isMono) }
                Text("${stereoWidth.toInt()}%", modifier = Modifier.width(45.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Моно"); Switch(isMono, { onMonoToggle() }, enabled = enabled) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Loudness"); Switch(loudness, { onLoudnessToggle() }, enabled = enabled) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("L"); Slider(balance, onBalanceChange, valueRange = -100f..100f, enabled = enabled, modifier = Modifier.weight(1f)); Text("R") }
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
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
                        "💾 Экспорт с эффектами",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    if (currentTrack != null && settings.hasChanges()) {
                        Text(
                            "Сохранить \"${currentTrack.title}\" с текущими настройками",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Измените настройки эквалайзера",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Экспорт")
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
                Modifier.padding(24.dp),
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
