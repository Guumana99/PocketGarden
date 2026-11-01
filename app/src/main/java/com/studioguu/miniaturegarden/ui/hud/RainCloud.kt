package com.studioguu.miniaturegarden.ui.hud

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.pressScale
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.effects.drawCloud
import kotlin.math.PI
import kotlin.math.sin

/**
 * What the cloud at the top of the screen shows. It says by its look whether a rain can be called:
 * no words and no numbers, nothing to hurry the player.
 *
 * @property calling a rain can be called: a tap asks whether to, the rest answer with [restMessage].
 * @property rain how much of a rain cloud it is, 0 a plain white cloud and 1 the darkest.
 */
enum class RainCloudState(val calling: Boolean, val rain: Float, @param:StringRes val restMessage: Int?) {
    READY(true, 0.75f, null),

    /** Something in the garden is thirsty: a little darker, with a little more rain. */
    PARCHED(true, 1f, null),

    USED_TODAY(false, 0f, R.string.msg_rain_used),
    NOTHING_GROWING(false, 0f, R.string.msg_no_growing_plants),
    NO_AD(false, 0f, R.string.msg_reward_unavailable);

    /** How it looks: while it is being [introducing]d it is the rain cloud the words speak of, whatever it can do. */
    fun look(introducing: Boolean): RainCloudState = if (introducing && !calling) READY else this

    companion object {
        fun of(rainsLeft: Int, growing: Boolean, thirsty: Boolean, adReady: Boolean): RainCloudState = when {
            rainsLeft <= 0 -> USED_TODAY
            !growing -> NOTHING_GROWING
            !adReady -> NO_AD
            thirsty -> PARCHED
            else -> READY
        }
    }
}

/** How the cloud moves and rains. Pure, so that it can be looked at without a screen. */
object RainCloudLook {
    /** How far it drifts up and down, in dp, and how long that takes. */
    const val BOB_DP = 2.5f
    const val BOB_MILLIS = 5_600L

    /** How long each drop's round is, and the share of it it spends falling: the rest it is not there. */
    const val DROP_MILLIS = 3_400L
    const val DROP_SHARE = 0.34f

    /** How far the cloud has drifted at [millis], -1 to 1. */
    fun bobAt(millis: Long): Float = sin(2.0 * PI * millis / BOB_MILLIS).toFloat()

    /** A drop falling at [fall] of the way down, at [across] of the cloud's width, [alpha] solid. */
    data class Drop(val across: Float, val fall: Float, val alpha: Float)

    /** Where the drops start across the cloud, and when, as shares of a round. */
    private val DROPS = listOf(0.34f to 0.0f, 0.62f to 0.37f, 0.48f to 0.71f, 0.27f to 0.52f, 0.72f to 0.18f)

    /** The drops in the air at [millis]: three from a cloud of [rain] up to 0.75, five from a darker one, none from a white one. */
    fun dropsAt(millis: Long, rain: Float): List<Drop> {
        if (rain <= 0.05f) return emptyList()
        val count = if (rain > 0.9f) DROPS.size else 3
        return DROPS.take(count).mapNotNull { (across, start) ->
            val round = (millis.toFloat() / DROP_MILLIS + start) % 1f
            if (round > DROP_SHARE) return@mapNotNull null
            val fall = round / DROP_SHARE
            Drop(across, fall, (1f - fall) * rain.coerceAtMost(1f))
        }
    }

    /** The thin streaks under the cloud: where across it, and how long, as shares of the cloud's height. */
    val STREAKS = listOf(0.32f to 0.16f, 0.45f to 0.22f, 0.58f to 0.18f, 0.7f to 0.14f)

    private val WHITE = Color(0xFFF4F5F6)
    private val GREY_BLUE = Color(0xFF7D93A8)
    private val STREAK = Color(0xFF9CC3DE)

    /** The cloud's colour at [rain], under the light of the time of day ([tint]). */
    fun colourOf(rain: Float, tint: Color): Color =
        lit(lerp(WHITE, GREY_BLUE, rain), tint, WHITE_LIGHT + (1f - WHITE_LIGHT) * rain.coerceAtMost(1f))

    /** The rain's colour, under the same light. */
    fun rainColour(tint: Color): Color = lit(STREAK, tint)

    /** How much of the scene's light a white cloud takes: not all, or at night it is lost in the sky. A rain cloud takes all of it, and stands out dark. */
    private const val WHITE_LIGHT = 0.7f

    /** The screen's buttons are not under the scene's light: the cloud, being in the sky, takes it itself. */
    private fun lit(colour: Color, tint: Color, share: Float = WHITE_LIGHT): Color = lerp(colour, tint.copy(alpha = 1f), tint.alpha * share)
}

private val CLOUD_WIDTH = 96.dp
private val CLOUD_HEIGHT = 72.dp

/** Test tag of the rain cloud. */
const val RAIN_CLOUD_TAG = "rain_cloud"

/**
 * The cloud that calls the rain, fixed to the screen at the top of the sky. It changes colour
 * slowly, as a cloud would, and its whole box takes the tap, a finger's width and more.
 *
 * @param tint the light of the time of day over the scene.
 */
@Composable
fun RainCloud(state: RainCloudState, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rain by animateFloatAsState(state.rain, tween(CLOUD_CHANGE_MILLIS), label = "rain")
    val time by rememberInfiniteTransition(label = "cloud").animateFloat(
        initialValue = 0f,
        targetValue = CLOCK_ROUND_MILLIS.toFloat(),
        animationSpec = infiniteRepeatable(tween(CLOCK_ROUND_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "clock",
    )
    val press = remember { MutableInteractionSource() }
    val name = stringResource(R.string.reward_rain_button)
    Box(
        modifier
            .size(CLOUD_WIDTH, CLOUD_HEIGHT)
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = withTick(onClick))
            .semantics { contentDescription = name }
            .testTag(RAIN_CLOUD_TAG),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val millis = time.toLong()
            val centre = Offset(size.width / 2f, size.height * 0.42f + RainCloudLook.bobAt(millis) * RainCloudLook.BOB_DP.dp.toPx())
            val rainColour = RainCloudLook.rainColour(tint)
            val foot = centre.y + size.height * 0.2f
            val thin = 1.2.dp.toPx()
            for ((across, length) in RainCloudLook.STREAKS) {
                val x = size.width * across
                drawLine(rainColour.copy(alpha = 0.6f * rain), Offset(x, foot), Offset(x - 2f, foot + size.height * length), thin, StrokeCap.Round)
            }
            for (drop in RainCloudLook.dropsAt(millis, rain)) {
                val y = foot + size.height * (0.1f + 0.3f * drop.fall)
                val x = size.width * drop.across
                drawLine(rainColour.copy(alpha = drop.alpha), Offset(x, y), Offset(x - 1f, y + 4.dp.toPx()), thin * 1.3f, StrokeCap.Round)
            }
            drawCloud(centre, size.width * 0.235f, RainCloudLook.colourOf(rain, tint).copy(alpha = 0.96f))
        }
    }
}

private const val CLOUD_CHANGE_MILLIS = 900

/** A whole number of bobs and of drops' rounds, for the clock to come round without a jump. */
private val CLOCK_ROUND_MILLIS = (RainCloudLook.BOB_MILLIS * RainCloudLook.DROP_MILLIS / 200L).toInt()
