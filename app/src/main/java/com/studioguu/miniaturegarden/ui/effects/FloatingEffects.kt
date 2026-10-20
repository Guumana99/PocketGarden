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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.FarmTool
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.objectDisplayName
import com.studioguu.miniaturegarden.ui.toolRank

/** A short-lived piece of feedback. [id] must be unique among the effects on screen. */
sealed interface GardenEffect {
    val id: Long

    data class Harvest(override val id: Long, val coins: Int, val xp: Int) : GardenEffect
    data class Xp(override val id: Long, val amount: Int) : GardenEffect

    /** A page of the encyclopedia became ready to be filled in. */
    data class Discovery(override val id: Long) : GardenEffect

    /** The hoe or the watering can got better with use ([FarmTool]). */
    data class ToolLevelUp(override val id: Long, val tool: FarmTool, val level: Int) : GardenEffect

    /**
     * A building was finished, or taking one apart was. [newPage]: finishing it also made a page
     * of the encyclopedia ready, said on the same plaque rather than on a second one over it.
     * The experience its unveiling pays is not said here: "完成", the new page and "+XP" at once
     * were too much to read, and the level bar shows it filling.
     */
    data class WorkDone(
        override val id: Long,
        val objectId: String,
        val kind: WorkKind,
        val newPage: Boolean = false,
    ) : GardenEffect
}

/**
 * [effect] added to the effects on screen. A building's first completion also makes its page of
 * the book ready, and the two would otherwise float up in the same place at the same moment: the
 * news of the page joins the plaque of the building instead, whichever of the two comes first.
 */
fun List<GardenEffect>.plusEffect(effect: GardenEffect): List<GardenEffect> {
    val built = indexOfLast { it is GardenEffect.WorkDone && it.kind == WorkKind.BUILDING }
    return when {
        effect is GardenEffect.Discovery && built >= 0 ->
            toMutableList().also { it[built] = (it[built] as GardenEffect.WorkDone).copy(newPage = true) }
        effect is GardenEffect.WorkDone && effect.kind == WorkKind.BUILDING && any { it is GardenEffect.Discovery } ->
            filterNot { it is GardenEffect.Discovery } + effect.copy(newPage = true)
        else -> this + effect
    }
}

private const val DURATION_MILLIS = 2_600
private val RISE = 96.dp

/**
 * How long an effect is on screen, and for what share of that it is fully visible before it
 * fades. The sums of a harvest float up and go after about two seconds of being readable; news of
 * a building (finished, taken down, packed away) or of a new page for the book stays a good
 * while, so that it can be read (#49, #52).
 */
data class EffectTiming(val durationMillis: Int, val visibleShare: Float, val riseShare: Float)

fun effectTiming(effect: GardenEffect): EffectTiming = when (effect) {
    is GardenEffect.WorkDone -> EffectTiming(durationMillis = 3_600, visibleShare = 0.8f, riseShare = 0.35f)
    // A tool getting better is news, read like a new page of the book.
    is GardenEffect.ToolLevelUp -> EffectTiming(durationMillis = 3_200, visibleShare = 0.78f, riseShare = 0.45f)
    // A new page for the book is news too: it stays nearly as long as a building's (#52).
    is GardenEffect.Discovery -> EffectTiming(durationMillis = 3_200, visibleShare = 0.78f, riseShare = 0.45f)
    // The sums of a harvest: readable for about two seconds before they fade (#52: one was too quick).
    else -> EffectTiming(durationMillis = DURATION_MILLIS, visibleShare = 0.72f, riseShare = 0.8f)
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
    // Above a harvest's sums and a page's news, which can come from the same tap.
    is GardenEffect.ToolLevelUp -> 2
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
        is GardenEffect.ToolLevelUp -> stringResource(
            when (effect.tool) {
                FarmTool.HOE -> R.string.effect_hoe_level_up
                FarmTool.WATERING_CAN -> R.string.effect_can_level_up
            },
            stringResource(toolRank(effect.level)),
        )
        is GardenEffect.WorkDone -> stringResource(
            when (effect.kind) {
                WorkKind.DEMOLISHING -> R.string.effect_demolished
                WorkKind.PACKING -> R.string.effect_packed
                else -> R.string.effect_built
            },
            objectDisplayName(effect.objectId),
        ) + if (effect.newPage) "\n" + stringResource(R.string.effect_discovery) else ""
    }
    Text(
        text = text,
        style = if (isBig) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
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
