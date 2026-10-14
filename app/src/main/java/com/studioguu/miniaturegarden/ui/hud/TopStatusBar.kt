package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.seasonEmoji
import com.studioguu.miniaturegarden.ui.seasonLabel
import com.studioguu.miniaturegarden.ui.weatherEmoji
import com.studioguu.miniaturegarden.ui.weatherLabel

/**
 * Coins, level with progress toward the next one, and the season with today's weather.
 *
 * Deliberately built without Surface or any clickable: those install pointer-input handlers
 * that swallow touches, and this bar must let drags and taps fall through to the garden below.
 */
@Composable
fun TopStatusBar(
    coins: Long,
    playerLevel: Int,
    currentXp: Long,
    weather: WeatherType,
    modifier: Modifier = Modifier,
    season: Season = Season.SPRING,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip("🪙 $coins")
        StatusChip(
            if (playerLevel >= LevelTable.MAX_LEVEL) {
                stringResource(R.string.status_level_max, playerLevel)
            } else {
                stringResource(R.string.status_level, playerLevel, currentXp, LevelTable.xpForLevel(playerLevel + 1))
            }
        )
        StatusChip("${seasonEmoji(season)}${stringResource(seasonLabel(season))} ${weatherEmoji(weather)} ${stringResource(weatherLabel(weather))}")
    }
}

@Composable
private fun StatusChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
