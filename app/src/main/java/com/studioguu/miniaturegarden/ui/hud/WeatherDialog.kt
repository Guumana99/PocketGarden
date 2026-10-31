package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.WeatherOutlook
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import com.studioguu.miniaturegarden.ui.weatherEmoji
import com.studioguu.miniaturegarden.ui.weatherLabel

/**
 * The weather, opened from the weather on the status bar. Today's is always there; tomorrow's
 * only while a weathervane stands in the garden ([readable], [WeatherOutlook.isReadable]).
 */
@Composable
fun WeatherDialog(outlook: WeatherOutlook, readable: Boolean, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.weather_title),
        actions = listOf(DialogAction(stringResource(R.string.action_close), onDismiss)),
    ) {
        WeatherLine(stringResource(R.string.weather_today), outlook.today)
        if (readable) {
            WeatherLine(stringResource(R.string.weather_tomorrow), outlook.tomorrow, Modifier.testTag(TOMORROW_TAG))
        }
    }
}

@Composable
private fun WeatherLine(day: String, weather: WeatherType, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(day, style = MaterialTheme.typography.titleSmall, color = GardenColors.InkSoft)
        Text(weatherEmoji(weather), fontSize = 24.sp)
        Text(stringResource(weatherLabel(weather)), style = MaterialTheme.typography.titleMedium, color = GardenColors.Ink)
    }
}

/** Test tag of tomorrow's line. */
const val TOMORROW_TAG = "weather_tomorrow"
