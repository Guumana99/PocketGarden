package com.studioguu.miniaturegarden.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import java.util.Locale

private const val HOUR_MILLIS = 60L * 60 * 1000
private const val DAY_MILLIS = 24 * HOUR_MILLIS

/**
 * Developer tools, reachable in debug builds only. The dialog stays open after a button is
 * pressed so the buttons can be pressed repeatedly.
 *
 * @param onCycleWeather switches to the next kind of weather, overriding the day's own.
 * @param onBoost adds coins and a level, to reach what opens late (the symbol building) quickly.
 * @param onNextSeason lets the rest of the season go by, so that the next one comes now.
 * @param zoomScale the garden's zoom as it is now, shown at the top.
 */
@Composable
fun DebugMenuDialog(
    onSkipTime: (millis: Long) -> Unit,
    onCycleWeather: () -> Unit,
    onDismiss: () -> Unit,
    onBoost: () -> Unit = {},
    onNextSeason: () -> Unit = {},
    zoomScale: Float? = null,
) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_menu_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (zoomScale != null) Text(zoomLabel(zoomScale))
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

/** Test tag of the zoom read-out laid over the garden. */
const val DEBUG_ZOOM_TAG = "debug_zoom"

/** A zoom as the read-out shows it: "×0.38", two places, whatever the language. */
fun zoomText(scale: Float): String = String.format(Locale.US, "×%.2f", scale)

@Composable
private fun zoomLabel(scale: Float): String =
    stringResource(R.string.debug_zoom, zoomText(scale), zoomText(GardenCamera.START_SCALE))

/**
 * The garden's zoom, kept up to date as it is pinched: a small dark label laid over the garden
 * in debug builds only.
 */
@Composable
fun DebugZoomReadout(scale: Float, modifier: Modifier = Modifier) {
    Text(
        text = zoomText(scale),
        color = Color.White,
        fontSize = 11.sp,
        modifier = modifier
            .testTag(DEBUG_ZOOM_TAG)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
