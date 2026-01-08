package com.example.soundly.presentation.components.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundly.player.audio.dsp.AudioEffect
import com.example.soundly.presentation.theme.LocalColorPalette

/**
 * Получить Material иконку по типу эффекта
 */
private fun getEffectIcon(iconType: String): ImageVector {
    return when (iconType) {
        "headphones" -> Icons.Default.Headphones
        "water_drop" -> Icons.Default.WaterDrop
        "bolt" -> Icons.Default.Bolt
        "nightlife" -> Icons.Default.Nightlife
        "rocket_launch" -> Icons.Default.RocketLaunch
        "tune" -> Icons.Default.Tune
        "music_note" -> Icons.Default.MusicNote
        else -> Icons.Default.MusicNote
    }
}

/**
 * Карточка эффекта с анимацией и glow
 */
@Composable
fun EffectCard(
    effect: AudioEffect,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val palette = LocalColorPalette.current
    
    val effectColor = Color(effect.color)
    
    // Анимации
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.4f else 0f,
        animationSpec = tween(250),
        label = "glow"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) effectColor else Color.Transparent,
        animationSpec = tween(250),
        label = "border"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) effectColor.copy(alpha = 0.15f) else palette.cardDark,
        animationSpec = tween(250),
        label = "background"
    )
    
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) effectColor else palette.textSecondary,
        animationSpec = tween(250),
        label = "iconTint"
    )

    Box(
        modifier = modifier
            .width(90.dp)
            .scale(scale)
    ) {
        // Glow эффект
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(12.dp)
                    .background(
                        effectColor.copy(alpha = glowAlpha),
                        RoundedCornerShape(14.dp)
                    )
            )
        }
        
        // Основная карточка
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (isSelected) Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp)
                    ) else Modifier
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            shape = RoundedCornerShape(14.dp),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Material иконка
                Icon(
                    imageVector = getEffectIcon(effect.icon),
                    contentDescription = effect.name,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Название
                Text(
                    text = effect.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) effectColor else palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
                
                // Описание скорости
                if (effect != AudioEffect.None) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${effect.speedRange.start}x-${effect.speedRange.endInclusive}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary.copy(alpha = 0.7f),
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

/**
 * Горизонтальный список эффектов
 */
@Composable
fun EffectsRow(
    effects: List<AudioEffect>,
    selectedEffect: AudioEffect,
    onEffectSelected: (AudioEffect) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalColorPalette.current
    
    Column(modifier = modifier) {
        Text(
            text = "Эффекты",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            effects.forEach { effect ->
                EffectCard(
                    effect = effect,
                    isSelected = effect.id == selectedEffect.id,
                    onClick = { onEffectSelected(effect) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
