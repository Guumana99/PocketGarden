package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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

/** How long the level gauge takes to move to a new amount of experience. */
private const val GAUGE_FILL_MILLIS = 450

/**
 * Coins, the level with a gauge of the way to the next one, and the season (with its phase,
 * "春のはじめ") and today's weather.
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip("🪙 $coins")
        LevelChip(playerLevel, currentXp)
        StatusChip(
            text = "${seasonEmoji(season)}${stringResource(seasonPhaseLabel(season, phase))} ${weatherEmoji(weather)} ${stringResource(weatherLabel(weather))}",
            onClick = onWeatherTap,
        )
    }
}

/**
 * "Lv.5" over a slim gauge of the way to Lv.6. A level-up first runs the gauge to full, then
 * empties it and fills it with what is left over. The gauge is read in the draw phase only, so
 * its animation redraws the bar without recomposing it.
 */
@Composable
private fun LevelChip(playerLevel: Int, currentXp: Long) {
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
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
    ) {
        Text(
            text = if (playerLevel >= LevelTable.MAX_LEVEL) {
                stringResource(R.string.status_level_max, playerLevel)
            } else {
                stringResource(R.string.status_level, playerLevel)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            Modifier
                .padding(top = 2.dp)
                .width(LEVEL_GAUGE_WIDTH)
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
private fun StatusChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
