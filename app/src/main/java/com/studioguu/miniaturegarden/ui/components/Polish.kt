package com.studioguu.miniaturegarden.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp

// Small touches of motion and feedback shared across the UI.

private const val PRESSED_SCALE = 0.9f
private const val POP_IN_START_SCALE = 0.9f
private const val POP_IN_MILLIS = 160

/**
 * Makes a button sink a little while it is held and spring back when let go. Pass the same
 * [interactionSource] to the button itself.
 */
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Grows and fades in once, when first shown. For dialogs, which otherwise just appear. */
fun Modifier.popIn(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(POP_IN_MILLIS)) }
    graphicsLayer {
        val scale = POP_IN_START_SCALE + (1f - POP_IN_START_SCALE) * progress.value
        scaleX = scale
        scaleY = scale
        alpha = progress.value
    }
}

/** Wraps a click handler so that it also gives a light tick of haptic feedback. */
@Composable
fun withTick(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

/**
 * A sprout nodding gently: shown where the garden will appear while its artwork is still being
 * read, so the first seconds after starting are never an empty sky. Its motion is a layer
 * transform only, so it costs no recomposition while it plays.
 */
@Composable
fun LoadingSprout(modifier: Modifier = Modifier) {
    val nod by rememberInfiniteTransition(label = "sprout").animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(LOADING_NOD_MILLIS), RepeatMode.Reverse),
        label = "nod",
    )
    Text(
        text = "🌱",
        fontSize = 40.sp,
        modifier = modifier.graphicsLayer {
            rotationZ = nod
            alpha = 0.85f
        },
    )
}

private const val LOADING_NOD_MILLIS = 700
