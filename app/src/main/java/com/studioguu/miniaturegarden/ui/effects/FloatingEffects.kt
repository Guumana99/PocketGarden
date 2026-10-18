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
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.objectDisplayName

/** A short-lived piece of feedback. [id] must be unique among the effects on screen. */
sealed interface GardenEffect {
    val id: Long

    data class Harvest(override val id: Long, val coins: Int, val xp: Int) : GardenEffect
    data class Xp(override val id: Long, val amount: Int) : GardenEffect

    /** A page of the encyclopedia became ready to be filled in. */
    data class Discovery(override val id: Long) : GardenEffect

    /** A building was finished, or taking one apart was. */
    data class WorkDone(override val id: Long, val objectId: String, val kind: WorkKind) : GardenEffect
}

private const val DURATION_MILLIS = 1600
private val RISE = 96.dp

/**
 * How long an effect is on screen, and for what share of that it is fully visible before it
 * fades. The quick sums of a harvest float up and go; news of a building (finished, taken down,
 * packed away) stays a good while, so that it can be read (#49).
 */
data class EffectTiming(val durationMillis: Int, val visibleShare: Float, val riseShare: Float)

fun effectTiming(effect: GardenEffect): EffectTiming = when (effect) {
    is GardenEffect.WorkDone -> EffectTiming(durationMillis = 3_600, visibleShare = 0.8f, riseShare = 0.35f)
    else -> EffectTiming(durationMillis = DURATION_MILLIS, visibleShare = 0.6f, riseShare = 1f)
}

/**
 * One action can set off several effects at once (harvest, first discovery). Each kind
 * starts one step higher than the last so they stack instead of overlap.
 */
private val LIFT_STEP = 60.dp

private fun liftSteps(effect: GardenEffect): Int = when (effect) {
    is GardenEffect.Harvest, is GardenEffect.Xp -> 0
    is GardenEffect.Discovery -> 1
    is GardenEffect.WorkDone -> 1
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
    val timing = effectTiming(effect)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(timing.durationMillis, easing = LinearOutSlowInEasing))
        currentOnFinished(effect.id)
    }

    // The big moments get the larger, filled style. A level-up has a board of its own
    // (LevelUpDialog), so that it cannot float past unseen.
    val isBig = effect is GardenEffect.WorkDone && effect.kind == WorkKind.BUILDING
    val lift = LIFT_STEP * liftSteps(effect)
    val text = when (effect) {
        is GardenEffect.Harvest -> stringResource(R.string.effect_harvest, effect.coins, effect.xp)
        is GardenEffect.Xp -> stringResource(R.string.effect_xp, effect.amount)
        is GardenEffect.Discovery -> stringResource(R.string.effect_discovery)
        is GardenEffect.WorkDone -> stringResource(
            when (effect.kind) {
                WorkKind.DEMOLISHING -> R.string.effect_demolished
                WorkKind.PACKING -> R.string.effect_packed
                else -> R.string.effect_built
            },
            objectDisplayName(effect.objectId),
        )
    }
    Text(
        text = text,
        style = if (isBig) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (isBig) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .graphicsLayer {
                val t = progress.value
                translationY = -(RISE.toPx() * timing.riseShare * t) - lift.toPx()
                // Fully visible for the first part, then fade.
                val visible = timing.visibleShare
                alpha = 1f - ((t - visible) / (1f - visible)).coerceIn(0f, 1f)
                // The big ones pop in from slightly small.
                val pop = if (isBig) 0.8f + 0.2f * (t / 0.2f).coerceAtMost(1f) else 1f
                scaleX = pop
                scaleY = pop
            }
            .background(
                color = if (isBig) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
