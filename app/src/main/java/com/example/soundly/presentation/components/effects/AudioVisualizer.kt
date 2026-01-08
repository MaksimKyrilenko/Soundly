package com.example.soundly.presentation.components.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.soundly.player.audio.dsp.AudioEffect
import com.example.soundly.presentation.theme.LocalColorPalette
import kotlin.math.*

/**
 * Анимированная волна, реагирующая на бас, скорость и pitch
 */
@Composable
fun AudioWaveVisualizer(
    bassLevel: Float,
    speed: Float,
    pitch: Float,
    effect: AudioEffect,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    // Анимация фазы волны
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2000 / speed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    // Пульсация от баса
    val bassPulse by animateFloatAsState(
        targetValue = if (isPlaying) 1f + bassLevel * 0.3f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 200f),
        label = "bassPulse"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // Количество волн зависит от pitch
        val waveCount = (3 * pitch).toInt().coerceIn(2, 6)
        val amplitude = (height / 4) * bassPulse
        
        // Рисуем несколько слоёв волн
        for (layer in 0 until waveCount) {
            val layerAlpha = 1f - (layer * 0.2f)
            val layerPhase = phase + (layer * PI.toFloat() / waveCount)
            val layerAmplitude = amplitude * (1f - layer * 0.15f)
            
            val path = Path()
            var firstPoint = true
            
            for (x in 0..width.toInt() step 4) {
                val normalizedX = x / width
                val y = centerY + sin(normalizedX * 4 * PI.toFloat() + layerPhase) * layerAmplitude
                
                if (firstPoint) {
                    path.moveTo(x.toFloat(), y)
                    firstPoint = false
                } else {
                    path.lineTo(x.toFloat(), y)
                }
            }
            
            drawPath(
                path = path,
                color = effectColor.copy(alpha = layerAlpha * 0.6f),
                style = Stroke(width = 2f + (waveCount - layer))
            )
        }
    }
}

/**
 * FFT спектр визуализатор
 */
@Composable
fun FFTSpectrumVisualizer(
    fftData: FloatArray,
    effect: AudioEffect,
    modifier: Modifier = Modifier
) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    val barCount = 32
    val animatedBars = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }
    
    // Обновляем бары с анимацией
    LaunchedEffect(fftData) {
        val step = fftData.size / barCount
        for (i in 0 until barCount) {
            val startIdx = i * step
            val endIdx = minOf(startIdx + step, fftData.size)
            val avg = if (endIdx > startIdx) {
                fftData.slice(startIdx until endIdx).map { abs(it) }.average().toFloat()
            } else 0f
            animatedBars[i] = avg
        }
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val barWidth = size.width / barCount - 2
        val maxHeight = size.height
        
        animatedBars.forEachIndexed { index, value ->
            val barHeight = (value * maxHeight * 2).coerceIn(4f, maxHeight)
            val x = index * (barWidth + 2)
            val y = maxHeight - barHeight
            
            // Градиент от низа к верху
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    effectColor.copy(alpha = 0.3f),
                    effectColor
                ),
                startY = maxHeight,
                endY = y
            )
            
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

/**
 * Бас-пульс индикатор (ring/glow)
 */
@Composable
fun BassPulseIndicator(
    bassLevel: Float,
    effect: AudioEffect,
    modifier: Modifier = Modifier
) {
    val effectColor = Color(effect.color)
    
    val pulseScale by animateFloatAsState(
        targetValue = 1f + bassLevel * 0.5f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 300f),
        label = "pulse"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = 0.2f + bassLevel * 0.4f,
        animationSpec = tween(100),
        label = "glow"
    )
    
    Box(
        modifier = modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow
        Box(
            modifier = Modifier
                .size((60 * pulseScale).dp)
                .blur(16.dp)
                .clip(CircleShape)
                .background(effectColor.copy(alpha = glowAlpha))
        )
        
        // Ring
        Canvas(modifier = Modifier.size(50.dp)) {
            drawCircle(
                color = effectColor,
                radius = size.minDimension / 2 * pulseScale,
                style = Stroke(width = 3f)
            )
        }
        
        // Center dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(effectColor)
        )
    }
}

/**
 * Индикатор громкости (LUFS-подобный)
 */
@Composable
fun LoudnessIndicator(
    level: Float, // 0-1
    effect: AudioEffect,
    modifier: Modifier = Modifier
) {
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "level"
    )
    
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.cardDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Green,
                                Color.Yellow,
                                Color.Red
                            ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-60", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text("-14", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text("0 dB", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}
