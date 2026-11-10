package com.studioguu.miniaturegarden.ui.debug

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.material3.TextButton
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.data.audio.SoundEffect
import com.studioguu.miniaturegarden.data.audio.BgmTrack
import com.studioguu.miniaturegarden.data.audio.AudioManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureCall
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.WeatherOutlook
import com.studioguu.miniaturegarden.ui.creatureName
import com.studioguu.miniaturegarden.ui.timeOfDayLabel
import com.studioguu.miniaturegarden.ui.weatherEmoji
import com.studioguu.miniaturegarden.ui.weatherLabel
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import java.util.Locale

private const val HOUR_MILLIS = 60L * 60 * 1000
private const val DAY_MILLIS = 24 * HOUR_MILLIS

/**
 * Developer tools, reachable in debug builds only. The dialog stays open after a button is
 * pressed so the buttons can be pressed repeatedly.
 *
 * @param onCycleWeather switches to the next kind of weather, overriding the day's own.
 * @param onSnowLying snow, lying on the roofs and the fences as thick as it gets.
 * @param onBoost adds coins and a level, to reach what opens late (the symbol building) quickly.
 * @param onNextSeason lets the rest of the season go by, so that the next one comes now.
 * @param onCycleTimeOfDay shows the garden at the next time of day, for a look at its light.
 * @param timeOfDayShown the time of day shown in place of the clock's; null for the clock's own.
 * @param outlook today's and tomorrow's weather, until the weathervane shows them in the garden.
 * @param zoomScale the garden's zoom as it is now, shown at the top.
 * @param creatureCall the creatures called to the garden, to look at how they move.
 * @param bookAllRegistered whether the book shows every page as registered ([BookPreview]).
 * @param onAskForReview calls the store's review flow now, whatever the rules say.
 * @param perchesShown whether the garden shows every point a visitor may settle on ([PerchPreview]).
 */
@Composable
fun DebugMenuDialog(
    onSkipTime: (millis: Long) -> Unit,
    onCycleWeather: () -> Unit,
    onDismiss: () -> Unit,
    onBoost: () -> Unit = {},
    onNextSeason: () -> Unit = {},
    onCycleTimeOfDay: () -> Unit = {},
    timeOfDayShown: TimeOfDay? = null,
    outlook: WeatherOutlook? = null,
    zoomScale: Float? = null,
    creatureCall: CreatureCall = CreatureCall(),
    onCreatureCall: (CreatureCall) -> Unit = {},
    bookAllRegistered: Boolean = false,
    onBookAllRegistered: (Boolean) -> Unit = {},
    onAskForReview: () -> Unit = {},
    onSnowLying: () -> Unit = {},
    perchesShown: Boolean = false,
    onPerchesShown: (Boolean) -> Unit = {},
    audio: AudioManager? = null,
) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.debug_menu_title),
        actions = listOf(DialogAction(stringResource(R.string.action_close), onDismiss)),
    ) {
        CompositionLocalProvider(LocalContentColor provides GardenColors.Ink) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (zoomScale != null) Text(zoomLabel(zoomScale))
                if (audio != null) AudioTuningSection(audio)
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
                FilledTonalButton(onClick = onSnowLying) {
                    Text(stringResource(R.string.debug_snow_lying))
                }
                FilledTonalButton(onClick = onCycleTimeOfDay) {
                    val shown = timeOfDayShown?.let { stringResource(timeOfDayLabel(it)) } ?: stringResource(R.string.debug_time_clock)
                    Text(stringResource(R.string.debug_cycle_time, shown))
                }
                if (outlook != null) {
                    Text(
                        stringResource(
                            R.string.debug_forecast,
                            "${weatherEmoji(outlook.today)}${stringResource(weatherLabel(outlook.today))}",
                            "${weatherEmoji(outlook.tomorrow)}${stringResource(weatherLabel(outlook.tomorrow))}",
                        )
                    )
                }
                FilledTonalButton(onClick = onBoost) {
                    Text(stringResource(R.string.debug_boost))
                }
                FilledTonalButton(onClick = onNextSeason) {
                    Text(stringResource(R.string.debug_next_season))
                }
                CreatureCallSection(creatureCall, onCreatureCall)
                SwitchRow(R.string.debug_book_all, bookAllRegistered, onBookAllRegistered, BOOK_ALL_SWITCH_TAG)
                Text(stringResource(R.string.debug_book_all_hint), fontSize = 12.sp)
                SwitchRow(R.string.debug_perches, perchesShown, onPerchesShown, PERCHES_SWITCH_TAG)
                Text(stringResource(R.string.debug_perches_hint), fontSize = 12.sp)
                FilledTonalButton(onClick = onAskForReview) {
                    Text(stringResource(R.string.debug_ask_review))
                }
                Text(stringResource(R.string.debug_ask_review_hint), fontSize = 12.sp)
            }
        }
    }
}

/** The creatures to call, one or two, and the switch that calls them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatureCallSection(call: CreatureCall, onChange: (CreatureCall) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchRow(R.string.debug_call_title, call.on, { onChange(call.copy(on = it)) }, CREATURE_CALL_SWITCH_TAG)
        Text(stringResource(R.string.debug_call_hint), fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (creature in Creature.entries) {
                FilterChip(
                    selected = creature in call.creatures,
                    onClick = { onChange(call.toggled(creature)) },
                    label = { Text(stringResource(creatureName(creature))) },
                    modifier = Modifier.testTag(creatureCallTag(creature)),
                )
            }
        }
    }
}

/**
 * The music's and the effects' levels as numbers (#fb-1110): the sliders' starting values, each
 * tune's gain and each effect's. A tune's gain is heard at once if it is the one playing; an
 * effect has a button to hear it. Kept until the app closes; the defaults live in [BgmTrack.gain]
 * and [SoundEffect.gain].
 */
@Composable
fun AudioTuningSection(audio: AudioManager) {
    var version by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag(AUDIO_TUNING_TAG)) {
        Text("音量（数字）", fontWeight = FontWeight.Bold)
        Text(
            "スライドバーの初期値：BGM ${volumeText(AppSettings.DEFAULT_BGM_VOLUME)}・効果音 ${volumeText(AppSettings.DEFAULT_SOUND_EFFECT_VOLUME)}" +
                "　今：BGM ${volumeText(audio.bgmVolume)}・効果音 ${volumeText(audio.soundEffectVolume)}",
        )
        key(version) {
            for (track in BgmTrack.entries) {
                StepRow("曲 ${track.resourceName}", audio.trackGain(track)) { audio.setTrackGain(track, it); version++ }
            }
            for (effect in SoundEffect.entries) {
                StepRow("効果音 ${effect.resourceName}", audio.effectGain(effect), onPlay = { audio.play(effect) }) {
                    audio.setEffectGain(effect, it); version++
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, value: Float, onPlay: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label ×${volumeText(value)}", fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(value - GAIN_STEP) }) { Text("−") }
        TextButton(onClick = { onChange(value + GAIN_STEP) }) { Text("＋") }
        if (onPlay != null) TextButton(onClick = onPlay) { Text("▶") }
    }
}

private fun volumeText(value: Float): String = String.format(Locale.US, "%.2f", value)

private const val GAIN_STEP = 0.05f
const val AUDIO_TUNING_TAG = "debug_audio_tuning"

/** A heading with its switch. */
@Composable
private fun SwitchRow(@StringRes title: Int, on: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

/** Test tags of the switches and of each creature's chip. */
const val CREATURE_CALL_SWITCH_TAG = "debug_call_switch"
const val BOOK_ALL_SWITCH_TAG = "debug_book_all_switch"
const val PERCHES_SWITCH_TAG = "debug_perches_switch"
fun creatureCallTag(creature: Creature): String = "debug_call_${creature.id}"

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
