package com.example.soundly.presentation.components.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.soundly.player.audio.dsp.AudioEffect
import com.example.soundly.presentation.theme.LocalColorPalette
import kotlin.math.roundToInt

/**
 * Пружинный слайдер интенсивности с анимацией
 */
@Composable
fun IntensitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    effect: AudioEffect,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val palette = LocalColorPalette.current
    val effectColor = Color(effect.color)
    
    var isDragging by remember { mutableStateOf(false) }
    
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.3f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "thumbScale"
    )
    
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "value"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Интенсивность",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = effectColor
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val trackWidth = maxWidth - 24.dp
            
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.cardDark)
            )
            
            // Active track
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedValue)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                effectColor.copy(alpha = 0.5f),
                                effectColor
                            )
                        )
                    )
            )
            
            // Thumb
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * animatedValue)
                    .size(24.dp)
                    .align(Alignment.CenterStart)
                    .shadow(
                        elevation = if (isDragging) 8.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = effectColor,
                        spotColor = effectColor
                    )
                    .clip(CircleShape)
                    .background(effectColor)
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newValue = (value + dragAmount.x / size.width).coerceIn(0f, 1f)
                                onValueChange(newValue)
                                if (newValue <= 0.01f || newValue >= 0.99f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Мягко", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text("Сильно", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}
