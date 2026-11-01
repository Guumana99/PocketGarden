package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.times
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SeasonPhase
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.seasonEmoji
import com.studioguu.miniaturegarden.ui.seasonPhaseLabel
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import com.studioguu.miniaturegarden.ui.weatherEmoji
import com.studioguu.miniaturegarden.ui.weatherLabel
import kotlin.math.min

/** How long the level gauge takes to move to a new amount of experience. */
private const val GAUGE_FILL_MILLIS = 450

/**
 * How large the device's type setting makes the bar's words. They stay on one line and the bar keeps
 * its height, so what sits under it (the rain, "skip") keeps its distance at any setting.
 */
const val TOP_BAR_MAX_FONT_SCALE = 1.15f

/**
 * How the bar's tags fit one line.
 *
 * @property textScale how much smaller than usual the words and the gauge are drawn, 1 for not at all.
 * @property padding room inside a tag, on either side of what it says.
 * @property gap room between two tags.
 */
data class TopBarFit(val textScale: Float, val padding: Float, val gap: Float) {
    /** The room round what the tags say. */
    data class Spacing(val padding: Float, val gap: Float)

    companion object {
        /**
         * How [bodies], the widths of what each tag says, go into [room]: with [roomy] spacing,
         * or else with [tight], and only then in smaller words.
         */
        fun of(room: Float, bodies: List<Float>, roomy: Spacing, tight: Spacing): TopBarFit {
            fun rest(spacing: Spacing) = bodies.size * 2 * spacing.padding + (bodies.size - 1) * spacing.gap
            val wanted = bodies.sum()
            return when {
                wanted + rest(roomy) <= room -> TopBarFit(1f, roomy.padding, roomy.gap)
                wanted + rest(tight) <= room -> TopBarFit(1f, tight.padding, tight.gap)
                else -> TopBarFit(((room - rest(tight)) / wanted).coerceAtLeast(0f), tight.padding, tight.gap)
            }
        }
    }
}

/**
 * Coins, the level with a gauge of the way to the next one, and the season (with its phase,
 * "春のはじめ") and today's weather, always on one line ([TopBarFit], [TOP_BAR_MAX_FONT_SCALE]).
 * The experience itself is never written out as numbers: the gauge fills as it grows, and empties
 * again when the level goes up.
 *
 * Deliberately built without Surface, and with only the season and weather taking taps (they open
 * the weather, [onWeatherTap]): a pointer-input handler swallows touches, and the rest of this
 * bar must let drags and taps fall through to the garden below.
 */
@Composable
fun TopStatusBar(
    coins: Long,
    playerLevel: Int,
    currentXp: Long,
    weather: WeatherType,
    modifier: Modifier = Modifier,
    season: Season = Season.SPRING,
    phase: SeasonPhase = SeasonPhase.EARLY,
    onWeatherTap: (() -> Unit)? = null,
) {
    val coinsText = "🪙 $coins"
    val levelText = if (playerLevel >= LevelTable.MAX_LEVEL) {
        stringResource(R.string.status_level_max, playerLevel)
    } else {
        stringResource(R.string.status_level, playerLevel)
    }
    val seasonText = "${seasonEmoji(season)}${stringResource(seasonPhaseLabel(season, phase))} ${weatherEmoji(weather)} ${stringResource(weatherLabel(weather))}"
    val device = LocalDensity.current
    val capped = remember(device) { Density(device.density, min(device.fontScale, TOP_BAR_MAX_FONT_SCALE)) }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalDensity provides capped) {
            val room = constraints.maxWidth - 2 * with(capped) { TOP_BAR_MARGIN.toPx() }
            fun width(text: String) = measurer.measure(text, style, softWrap = false, maxLines = 1, density = capped).size.width.toFloat()
            val fit = TopBarFit.of(
                room,
                listOf(width(coinsText), maxOf(width(levelText), with(capped) { LEVEL_GAUGE_WIDTH.toPx() }), width(seasonText)),
                with(capped) { TopBarFit.Spacing(TAG_PADDING.toPx(), TAG_GAP.toPx()) },
                with(capped) { TopBarFit.Spacing(TAG_PADDING_TIGHT.toPx(), TAG_GAP_TIGHT.toPx()) },
            )
            val scaled = style.scaledBy(fit.textScale)
            val padding = with(capped) { fit.padding.toDp() }
            Row(
                horizontalArrangement = Arrangement.spacedBy(with(capped) { fit.gap.toDp() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(coinsText, scaled, padding)
                LevelChip(playerLevel, currentXp, levelText, scaled, padding, fit.textScale)
                StatusChip(seasonText, scaled, padding, onClick = onWeatherTap)
            }
        }
    }
}

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale >= 1f) this else copy(fontSize = fontSize * scale, lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight)

private val TOP_BAR_MARGIN = 8.dp
private val TAG_PADDING = 14.dp
private val TAG_PADDING_TIGHT = 9.dp
private val TAG_GAP = 8.dp
private val TAG_GAP_TIGHT = 5.dp

/**
 * "Lv.5" over a slim gauge of the way to Lv.6. A level-up first runs the gauge to full, then
 * empties it and fills it with what is left over. The gauge is read in the draw phase only, so
 * its animation redraws the bar without recomposing it.
 */
@Composable
private fun LevelChip(playerLevel: Int, currentXp: Long, text: String, style: TextStyle, padding: Dp, scale: Float) {
    val target = LevelTable.progressInLevel(currentXp, playerLevel)
    val gauge = remember { Animatable(target) }
    var shownLevel by remember { mutableIntStateOf(playerLevel) }
    LaunchedEffect(playerLevel, target) {
        if (playerLevel > shownLevel) {
            gauge.animateTo(1f, tween(GAUGE_FILL_MILLIS))
            gauge.snapTo(0f)
        }
        shownLevel = playerLevel
        gauge.animateTo(target, tween(GAUGE_FILL_MILLIS))
    }
    val track = GardenColors.WoodDark.copy(alpha = 0.18f)
    val fill = GardenColors.Leaf
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            .padding(start = padding, end = padding, top = 4.dp, bottom = 6.dp),
    ) {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Row(
            Modifier
                .padding(top = 2.dp)
                .width(scale.coerceAtMost(1f) * LEVEL_GAUGE_WIDTH)
                .height(LEVEL_GAUGE_HEIGHT)
                .clip(CircleShape)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(track, cornerRadius = radius)
                    val filled = size.width * gauge.value
                    if (filled > 0f) drawRoundRect(fill, size = Size(filled, size.height), cornerRadius = radius)
                },
        ) {}
    }
}

private val LEVEL_GAUGE_WIDTH = 56.dp
private val LEVEL_GAUGE_HEIGHT = 5.dp

@Composable
private fun StatusChip(text: String, style: TextStyle, padding: Dp, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            .padding(horizontal = padding, vertical = 8.dp),
    )
}
