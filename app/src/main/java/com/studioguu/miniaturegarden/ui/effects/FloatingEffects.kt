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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.objectDisplayName
import kotlin.math.roundToInt

/** A short-lived piece of feedback. [id] must be unique among the effects on screen. */
sealed interface GardenEffect {
    val id: Long

    /** What the effect is about, as it was; it floats up from there (#fb1126 3). Null: the middle of the screen. */
    val source: GridObject?

    /**
     * The coins a harvest brought. Experience is not floated up, from a harvest or anything else:
     * it fills the gauge in the top bar instead (TopStatusBar). [bounty]: it was a good harvest.
     * [source]: the plant harvested, as it was, which the sum floats up from (#fb1126 3).
     */
    data class Harvest(override val id: Long, val coins: Int, val bounty: Boolean = false, override val source: GridObject? = null) : GardenEffect

    /**
     * A building was finished, or taking one apart was. The experience its unveiling pays is not
     * said here: the level bar shows it filling. A page of the encyclopedia made ready is not said
     * either: the red dot on the book's button tells of that.
     */
    data class WorkDone(
        override val id: Long,
        val objectId: String,
        val kind: WorkKind,
        override val source: GridObject? = null,
    ) : GardenEffect
}

/**
 * [effect] added to the effects on screen. Effects of one kind float up from the same spot, so a
 * second harvest while the first is still up takes its place rather than being laid over it (#59:
 * a row of crops harvested in a run stacked into an unreadable pile). It shows what that harvest
 * paid and no more: a running total read as each harvest paying more than the last (+22, +44,
 * +66). The top bar's coins are the total. It has its own id, so it starts afresh.
 */
fun List<GardenEffect>.plusEffect(effect: GardenEffect): List<GardenEffect> =
    if (effect is GardenEffect.Harvest && any { it is GardenEffect.Harvest }) {
        filterNot { it is GardenEffect.Harvest } + effect
    } else {
        this + effect
    }

private const val DURATION_MILLIS = 2_600
private val RISE = 96.dp

/**
 * How long an effect is on screen, and for what share of that it is fully visible before it
 * fades. The sums of a harvest float up and go after about two seconds of being readable; news of
 * a building (finished, taken down, packed away) stays a good while, so that it can be read (#49).
 */
data class EffectTiming(val durationMillis: Int, val visibleShare: Float, val riseShare: Float)

fun effectTiming(effect: GardenEffect): EffectTiming = when (effect) {
    is GardenEffect.WorkDone -> EffectTiming(durationMillis = 3_600, visibleShare = 0.8f, riseShare = 0.35f)
    // The sums of a harvest: readable for about two seconds before they fade (#52: one was too quick).
    else -> EffectTiming(durationMillis = DURATION_MILLIS, visibleShare = 0.72f, riseShare = 0.8f)
}

/** A building's news starts one step above a harvest's, so that the two stack instead of overlap. */
private val LIFT_STEP = 60.dp

private fun liftSteps(effect: GardenEffect): Int = when (effect) {
    is GardenEffect.Harvest -> 0
    is GardenEffect.WorkDone -> 1
}

/**
 * Where a label [width] x [height] goes that stands over [anchor], risen [rise] from there, all in
 * pixels: centred over it, and moved inside [area] where the point is near an edge of the screen
 * or behind a panel. The rise goes on from wherever it was moved to.
 */
fun anchoredTopLeft(anchor: Offset, width: Float, height: Float, area: Rect, rise: Float): Offset {
    val x = (anchor.x - width / 2f).coerceIn(area.left, maxOf(area.left, area.right - width))
    val lowest = maxOf(area.top, area.bottom - height)
    val y = (anchor.y - height).coerceIn(area.top, lowest)
    return Offset(x, (y - rise).coerceAtLeast(area.top))
}

/** How far a label over a cell floats up: less than one in the middle of the screen, to stay by its cell. */
private val ANCHORED_RISE = 40.dp

/**
 * Plays each effect once: the text rises, then fades out, and [onFinished] is called with its
 * id so the caller can drop it. Has no pointer input, so touches pass through to what is below.
 *
 * [anchor]: the point on screen an effect stands over, in this host's own coordinates, or null for
 * the middle of the screen. Read at every frame, so that the label goes with the garden as the
 * camera moves. [area]: where such a label may be: clear of the bars and panels.
 */
@Composable
fun FloatingEffectsHost(
    effects: List<GardenEffect>,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    anchor: (GardenEffect) -> Offset? = { null },
    area: () -> Rect = { Rect.Zero },
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        for (effect in effects) {
            key(effect.id) { FloatingEffectText(effect, onFinished, anchor, area) }
        }
    }
}

@Composable
private fun FloatingEffectText(effect: GardenEffect, onFinished: (Long) -> Unit, anchor: (GardenEffect) -> Offset?, area: () -> Rect) {
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
        is GardenEffect.Harvest -> stringResource(if (effect.bounty) R.string.effect_harvest_bounty else R.string.effect_harvest, effect.coins)
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
        textAlign = TextAlign.Center,
        color = if (isBig) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            // Over its cell where it has one; otherwise where the host puts it, in the middle.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val point = anchor(effect)
                if (point == null) {
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                } else {
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val rise = ANCHORED_RISE.toPx() * progress.value
                        val at = anchoredTopLeft(point, placeable.width.toFloat(), placeable.height.toFloat(), area(), rise)
                        placeable.place(at.x.roundToInt(), at.y.roundToInt())
                    }
                }
            }
            .graphicsLayer {
                val t = progress.value
                // An anchored label's rise is in its placing, above.
                translationY = if (anchor(effect) != null) 0f else -(RISE.toPx() * timing.riseShare * t) - lift.toPx()
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
