package com.studioguu.miniaturegarden.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.ui.components.popIn
import kotlin.math.roundToInt

/** The settings, one tab per kind, so new ones have somewhere to go. */
private enum class SettingsTab(val label: Int) {
    SOUND(R.string.settings_tab_sound),
    ABOUT(R.string.settings_tab_about),
    OTHER(R.string.settings_tab_other),
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(SettingsTab.SOUND) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showDisclaimer by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            // Nothing is drawn until the saved values are in, so no control starts in the wrong place.
            val current = settings ?: return@AlertDialog
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                    for (entry in SettingsTab.entries) {
                        Tab(
                            selected = entry == tab,
                            onClick = { tab = entry },
                            text = { Text(stringResource(entry.label), maxLines = 1) },
                        )
                    }
                }
                Column(
                    modifier = Modifier.heightIn(min = TAB_MIN_HEIGHT).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (tab) {
                        SettingsTab.SOUND -> SoundTab(current, viewModel)
                        SettingsTab.ABOUT -> AboutTab(
                            versionName = viewModel.versionName,
                            onShowLicenses = { showLicenses = true },
                            onShowDisclaimer = { showDisclaimer = true },
                        )
                        SettingsTab.OTHER -> SwitchRow(
                            label = stringResource(R.string.settings_notifications),
                            caption = stringResource(R.string.settings_notifications_caption),
                            checked = current.notificationsEnabled,
                            onCheckedChange = viewModel::setNotificationsEnabled,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )

    if (showLicenses) {
        InfoDialog(
            title = stringResource(R.string.settings_licenses),
            body = stringResource(R.string.licenses_body),
            onDismiss = { showLicenses = false },
        )
    }
    if (showDisclaimer) {
        InfoDialog(
            title = stringResource(R.string.settings_disclaimer),
            body = stringResource(R.string.disclaimer_body),
            onDismiss = { showDisclaimer = false },
        )
    }
}

@Composable
private fun SoundTab(current: AppSettings, viewModel: SettingsViewModel) {
    VolumeControl(
        label = stringResource(R.string.settings_bgm),
        enabled = current.bgmEnabled,
        volume = current.bgmVolume,
        defaultVolume = AppSettings.DEFAULT_BGM_VOLUME,
        onEnabledChange = viewModel::setBgmEnabled,
        onVolumeChange = viewModel::setBgmVolume,
    )
    HorizontalDivider()
    VolumeControl(
        label = stringResource(R.string.settings_se),
        enabled = current.soundEffectsEnabled,
        volume = current.soundEffectVolume,
        defaultVolume = AppSettings.DEFAULT_SOUND_EFFECT_VOLUME,
        onEnabledChange = viewModel::setSoundEffectsEnabled,
        onVolumeChange = viewModel::setSoundEffectVolume,
        onVolumeChosen = viewModel::previewSoundEffect,
    )
}

@Composable
private fun AboutTab(versionName: String, onShowLicenses: () -> Unit, onShowDisclaimer: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    Text(
        text = stringResource(R.string.settings_app_version, stringResource(R.string.app_name), versionName),
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedButton(onClick = onShowLicenses, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_licenses))
    }
    OutlinedButton(
        // Opens in the browser. A device with no browser at all simply does nothing.
        onClick = { runCatching { uriHandler.openUri(privacyPolicyUrl) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_privacy))
    }
    OutlinedButton(onClick = onShowDisclaimer, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_disclaimer))
    }
}

/**
 * A mute switch with a volume slider under it, and a button that puts the volume back to
 * [defaultVolume]. Muting greys the slider out but keeps its position.
 *
 * @param onVolumeChosen called when the finger leaves the slider, or the volume is reset.
 */
@Composable
private fun VolumeControl(
    label: String,
    enabled: Boolean,
    volume: Float,
    defaultVolume: Float,
    onEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChosen: () -> Unit = {},
) {
    // Compared in whole steps of the slider, so a value a hair off the default still counts as it.
    val atDefault = (volume * 100).roundToInt() == (defaultVolume * 100).roundToInt()
    val volumeDescription = stringResource(R.string.settings_volume_description, label)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        // The slider speaks for itself: no number beside it.
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChosen,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = volumeDescription },
        )
        TextButton(
            onClick = {
                onVolumeChange(defaultVolume)
                onVolumeChosen()
            },
            enabled = !atDefault,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.settings_reset_volume))
        }
    }
}

@Composable
private fun SwitchRow(label: String, caption: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A scrollable page of text with a close button. */
@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

private val TAB_MIN_HEIGHT = 240.dp
