package com.studioguu.miniaturegarden.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.objectDisplayName

/** A short-lived piece of feedback. [id] must be unique among the effects on screen. */
sealed interface GardenEffect {
    val id: Long

    data class Harvest(override val id: Long, val coins: Int, val xp: Int) : GardenEffect
    data class Xp(override val id: Long, val amount: Int) : GardenEffect
    data class LevelUp(override val id: Long, val level: Int) : GardenEffect

    /** A page of the encyclopedia was filled in for the first time. */
    data class Discovery(override val id: Long, val objectId: String, val coins: Int) : GardenEffect

    /** The last page of the encyclopedia was filled in. */
    data class CollectionComplete(override val id: Long, val coins: Int, val xp: Int) : GardenEffect
}

private const val DURATION_MILLIS = 1600
private val RISE = 96.dp

/**
 * One action can set off several effects at once (harvest, first discovery, level-up, completed
 * encyclopedia). Each kind starts one step higher than the last so they stack instead of overlap.
 */
private val LIFT_STEP = 60.dp

private fun liftSteps(effect: GardenEffect): Int = when (effect) {
    is GardenEffect.Harvest, is GardenEffect.Xp -> 0
    is GardenEffect.Discovery -> 1
    is GardenEffect.LevelUp -> 2
    is GardenEffect.CollectionComplete -> 3
}

/**
 * Plays each effect once: the text rises, then fades out, and [onFinished] is called with its
 * id so the caller can drop it. Has no pointer input, so touches pass through to what is below.
 */
@Composable
fun FloatingEffectsHost(
    effects: List<GardenEffect>,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        for (effect in effects) {
            key(effect.id) { FloatingEffectText(effect, onFinished) }
        }
    }
}

@Composable
private fun FloatingEffectText(effect: GardenEffect, onFinished: (Long) -> Unit) {
    val progress = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(DURATION_MILLIS, easing = LinearOutSlowInEasing))
        currentOnFinished(effect.id)
    }

    // The big moments get the larger, filled style.
    val isLevelUp = effect is GardenEffect.LevelUp || effect is GardenEffect.CollectionComplete
    val lift = LIFT_STEP * liftSteps(effect)
    val text = when (effect) {
        is GardenEffect.Harvest -> stringResource(R.string.effect_harvest, effect.coins, effect.xp)
        is GardenEffect.Xp -> stringResource(R.string.effect_xp, effect.amount)
        is GardenEffect.LevelUp -> stringResource(R.string.effect_level_up, effect.level)
        is GardenEffect.Discovery ->
            stringResource(R.string.effect_discovery, objectDisplayName(effect.objectId), effect.coins)
        is GardenEffect.CollectionComplete ->
            stringResource(R.string.effect_collection_complete, effect.coins, effect.xp)
    }
    Text(
        text = text,
        style = if (isLevelUp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (isLevelUp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .graphicsLayer {
                val t = progress.value
                translationY = -(RISE.toPx() * t) - lift.toPx()
                // Fully visible for the first 60%, then fade.
                alpha = 1f - ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
                // The big ones pop in from slightly small.
                val pop = if (isLevelUp) 0.8f + 0.2f * (t / 0.2f).coerceAtMost(1f) else 1f
                scaleX = pop
                scaleY = pop
            }
            .background(
                color = if (isLevelUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
