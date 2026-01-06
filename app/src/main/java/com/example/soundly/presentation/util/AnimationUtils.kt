package com.example.soundly.presentation.util

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

// Easing curves - defined first to be used below
val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// Smooth fade + slide animations for screen transitions
val fadeSlideIn: EnterTransition = fadeIn(
    animationSpec = tween(300, easing = EaseOutCubic)
) + slideInVertically(
    initialOffsetY = { it / 10 },
    animationSpec = tween(300, easing = EaseOutCubic)
)

val fadeSlideOut: ExitTransition = fadeOut(
    animationSpec = tween(200, easing = EaseInCubic)
) + slideOutVertically(
    targetOffsetY = { -it / 10 },
    animationSpec = tween(200, easing = EaseInCubic)
)

// Horizontal slide animations
val slideInFromRight: EnterTransition = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(350, easing = EaseOutCubic)
) + fadeIn(animationSpec = tween(350))

val slideOutToLeft: ExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 3 },
    animationSpec = tween(350, easing = EaseInCubic)
) + fadeOut(animationSpec = tween(200))

val slideInFromLeft: EnterTransition = slideInHorizontally(
    initialOffsetX = { -it },
    animationSpec = tween(350, easing = EaseOutCubic)
) + fadeIn(animationSpec = tween(350))

val slideOutToRight: ExitTransition = slideOutHorizontally(
    targetOffsetX = { it / 3 },
    animationSpec = tween(350, easing = EaseInCubic)
) + fadeOut(animationSpec = tween(200))

// Scale animations for items
val scaleIn: EnterTransition = scaleIn(
    initialScale = 0.85f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeIn(animationSpec = tween(200))

val scaleOut: ExitTransition = scaleOut(
    targetScale = 0.85f,
    animationSpec = tween(150)
) + fadeOut(animationSpec = tween(150))

// Bottom sheet / dialog animations
val expandVerticallyAnim: EnterTransition = expandVertically(
    expandFrom = androidx.compose.ui.Alignment.Bottom,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) + fadeIn(animationSpec = tween(200))

val shrinkVerticallyAnim: ExitTransition = shrinkVertically(
    shrinkTowards = androidx.compose.ui.Alignment.Bottom,
    animationSpec = tween(200, easing = EaseInCubic)
) + fadeOut(animationSpec = tween(150))

// Modifier extensions for common animations
fun Modifier.pressAnimation(pressed: Boolean): Modifier = composed {
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    this.scale(scale.value)
}

fun Modifier.shimmerEffect(
    isLoading: Boolean,
    shimmerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
): Modifier = composed {
    if (!isLoading) return@composed this
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX = transition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    this.graphicsLayer {
        translationX = translateX.value
    }
}

// Staggered animation helper
@Composable
fun <T> animateItemsIndexed(
    items: List<T>,
    delayPerItem: Int = 50,
    content: @Composable (index: Int, item: T, visible: Boolean) -> Unit
) {
    items.forEachIndexed { index, item ->
        val visible = androidx.compose.runtime.remember { 
            androidx.compose.runtime.mutableStateOf(false) 
        }
        
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(index * delayPerItem.toLong())
            visible.value = true
        }
        
        content(index, item, visible.value)
    }
}

// Animation specs for common use cases
object SoundlyAnimationSpecs {
    val quickFade = tween<Float>(150, easing = LinearEasing)
    val standardFade = tween<Float>(300, easing = EaseOutCubic)
    val slowFade = tween<Float>(500, easing = EaseOutCubic)
    
    val quickSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    val bouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    val gentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
}
