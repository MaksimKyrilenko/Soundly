package com.example.soundly.presentation.screens.effects

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.soundly.player.audio.dsp.AudioEffect
import com.example.soundly.presentation.components.effects.*
import com.example.soundly.presentation.theme.LocalColorPalette
import com.example.soundly.presentation.theme.backgroundGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsScreen(
    navController: NavController,
    viewModel: EffectsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val palette = LocalColorPalette.current
    
    val effectColor = Color(uiState.currentEffect.color)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Эффекты", color = palette.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = palette.textPrimary)
                    }
                },
                actions = {
                    // Кнопка сброса
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.resetEffect()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Сбросить", tint = palette.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient())
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Визуализация
            VisualizerCard(
                effect = uiState.currentEffect,
                bassLevel = uiState.bassLevel,
                speed = uiState.speed,
                pitch = uiState.pitch,
                level = uiState.currentLevel,
                isPlaying = uiState.isPlaying
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Выбор эффекта
            EffectSelectionCard(
                effects = uiState.availableEffects,
                selectedEffect = uiState.currentEffect,
                onEffectSelected = { effect ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setEffect(effect)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Интенсивность (если эффект выбран)
            AnimatedVisibility(
                visible = uiState.currentEffect != AudioEffect.None,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                IntensityCard(
                    intensity = uiState.intensity,
                    effect = uiState.currentEffect,
                    onIntensityChange = viewModel::setIntensity
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Параметры скорости и питча
            SpeedPitchCard(
                speed = uiState.speed,
                pitch = uiState.pitch,
                effect = uiState.currentEffect,
                onSpeedChange = viewModel::setSpeed,
                onPitchChange = viewModel::setPitch
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Информация об эффекте
            AnimatedVisibility(
                visible = uiState.currentEffect != AudioEffect.None,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                EffectInfoCard(effect = uiState.currentEffect)
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // Для MiniPlayer
        }
    }
}


@Composable
private fun VisualizerCard(
    effect: AudioEffect,
    bassLevel: Float,
    speed: Float,
    pitch: Float,
    level: Float,
    isPlaying: Boolean
) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Волна
                AudioWaveVisualizer(
                    bassLevel = bassLevel,
                    speed = speed,
                    pitch = pitch,
                    effect = effect,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Индикаторы
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Бас пульс
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BassPulseIndicator(
                            bassLevel = bassLevel,
                            effect = effect
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Bass",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                    
                    // Громкость
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoudnessIndicator(
                            level = level,
                            effect = effect,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectSelectionCard(
    effects: List<AudioEffect>,
    selectedEffect: AudioEffect?,
    onEffectSelected: (AudioEffect) -> Unit
) {
    val palette = LocalColorPalette.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Выберите эффект",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Горизонтальный скролл эффектов
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    effects.forEach { effect ->
                        EffectCard(
                            effect = effect,
                            isSelected = effect.id == selectedEffect?.id,
                            onClick = { onEffectSelected(effect) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntensityCard(
    intensity: Float,
    effect: AudioEffect,
    onIntensityChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                IntensitySlider(
                    value = intensity,
                    onValueChange = onIntensityChange,
                    effect = effect
                )
            }
        }
    }
}

@Composable
private fun SpeedPitchCard(
    speed: Float,
    pitch: Float,
    effect: AudioEffect,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Скорость и тон",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Скорость
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Скорость", color = palette.textPrimary)
                    Text(
                        "${String.format("%.2f", speed)}x",
                        fontWeight = FontWeight.Bold,
                        color = effectColor
                    )
                }
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = effectColor,
                        activeTrackColor = effectColor
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Питч
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Тон (Pitch)", color = palette.textPrimary)
                    Text(
                        "${String.format("%.2f", pitch)}x",
                        fontWeight = FontWeight.Bold,
                        color = effectColor
                    )
                }
                Slider(
                    value = pitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = effectColor,
                        activeTrackColor = effectColor
                    )
                )
                
                // Информация о связи
                if (effect.pitchLinked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pitch связан со скоростью для этого эффекта",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectInfoCard(effect: AudioEffect) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            effectColor.copy(alpha = 0.1f),
                            effectColor.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = effect.icon,
                    style = MaterialTheme.typography.headlineMedium
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = effect.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = effectColor
                    )
                    Text(
                        text = effect.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}
