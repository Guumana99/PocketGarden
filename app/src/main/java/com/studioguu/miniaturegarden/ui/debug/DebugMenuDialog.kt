package com.studioguu.miniaturegarden.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.popIn

private const val HOUR_MILLIS = 60L * 60 * 1000
private const val DAY_MILLIS = 24 * HOUR_MILLIS

/**
 * Developer tools, reachable in debug builds only. The dialog stays open after a button is
 * pressed so the buttons can be pressed repeatedly.
 *
 * @param onCycleWeather switches to the next kind of weather, overriding the day's own.
 * @param onBoost adds coins and a level, to reach what opens late (the symbol building) quickly.
 * @param onNextSeason lets the rest of the season go by, so that the next one comes now.
 */
@Composable
fun DebugMenuDialog(
    onSkipTime: (millis: Long) -> Unit,
    onCycleWeather: () -> Unit,
    onDismiss: () -> Unit,
    onBoost: () -> Unit = {},
    onNextSeason: () -> Unit = {},
) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_menu_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.debug_menu_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { onSkipTime(HOUR_MILLIS) }) {
                        Text(stringResource(R.string.debug_skip_hour))
                    }
                    FilledTonalButton(onClick = { onSkipTime(DAY_MILLIS) }) {
                        Text(stringResource(R.string.debug_skip_day))
                    }
                }
                FilledTonalButton(onClick = onCycleWeather) {
                    Text(stringResource(R.string.debug_cycle_weather))
                }
                FilledTonalButton(onClick = onBoost) {
                    Text(stringResource(R.string.debug_boost))
                }
                FilledTonalButton(onClick = onNextSeason) {
                    Text(stringResource(R.string.debug_next_season))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
