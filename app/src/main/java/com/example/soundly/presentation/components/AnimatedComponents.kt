package com.example.soundly.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * Animated item that fades and slides in with staggered delay
 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerItem: Long = 50L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(index * delayPerItem)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(300, easing = EaseOutCubic)
        ) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(300, easing = EaseOutCubic)
        ),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Animated item with horizontal slide
 */
@Composable
fun AnimatedHorizontalItem(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerItem: Long = 80L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(index * delayPerItem)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(250)
        ) + slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(350, easing = EaseOutCubic)
        ),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Pressable item with scale animation
 */
@Composable
fun PressableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        content()
    }
}

/**
 * Animated counter with number transition
 */
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { -it } + fadeIn() togetherWith
                        slideOutVertically { it } + fadeOut()
            } else {
                slideInVertically { it } + fadeIn() togetherWith
                        slideOutVertically { -it } + fadeOut()
            }.using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "counter"
    ) { targetCount ->
        content(targetCount)
    }
}

/**
 * Pulsing animation for playing indicator
 */
@Composable
fun PulsingIndicator(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(modifier = modifier.scale(scale)) {
        content()
    }
}

/**
 * Animated expand/collapse content
 */
@Composable
fun ExpandableContent(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(
            animationSpec = tween(200, easing = EaseInCubic)
        ) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Shake animation for error states
 */
@Composable
fun ShakeableContent(
    shake: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shakeOffset by animateFloatAsState(
        targetValue = if (shake) 1f else 0f,
        animationSpec = if (shake) {
            keyframes {
                durationMillis = 400
                0f at 0
                -10f at 50
                10f at 100
                -10f at 150
                10f at 200
                -5f at 250
                5f at 300
                0f at 400
            }
        } else {
            tween(0)
        },
        label = "shake"
    )
    
    Box(
        modifier = modifier.offset(x = shakeOffset.dp)
    ) {
        content()
    }
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

private val Float.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this)
