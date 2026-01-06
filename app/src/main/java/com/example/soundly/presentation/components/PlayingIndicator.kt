package com.example.soundly.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated sound bars indicator for playing tracks
 */
@Composable
fun PlayingIndicator(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    barCount: Int = 4,
    barWidth: Dp = 3.dp,
    maxBarHeight: Dp = 16.dp,
    minBarHeight: Dp = 4.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    spacing: Dp = 2.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            AnimatedSoundBar(
                isPlaying = isPlaying,
                barWidth = barWidth,
                maxBarHeight = maxBarHeight,
                minBarHeight = minBarHeight,
                barColor = barColor,
                delayMillis = index * 100
            )
        }
    }
}

@Composable
private fun AnimatedSoundBar(
    isPlaying: Boolean,
    barWidth: Dp,
    maxBarHeight: Dp,
    minBarHeight: Dp,
    barColor: Color,
    delayMillis: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundBar")
    
    val animatedHeight by infiniteTransition.animateFloat(
        initialValue = minBarHeight.value,
        targetValue = maxBarHeight.value,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 300 + delayMillis,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barHeight"
    )
    
    val displayHeight = if (isPlaying) animatedHeight else minBarHeight.value
    
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(displayHeight.dp)
            .clip(RoundedCornerShape(barWidth / 2))
            .background(barColor)
    )
}

private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
