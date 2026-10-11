package com.studioguu.miniaturegarden.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.popIn
import kotlin.math.roundToInt

/** Volume and mute for music and effects, the notifications switch, and information about the app. */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            // Nothing is drawn until the saved values are in, so no control starts in the wrong place.
            val current = settings ?: return@AlertDialog
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_section_sound))
                VolumeControl(
                    label = stringResource(R.string.settings_bgm),
                    enabled = current.bgmEnabled,
                    volume = current.bgmVolume,
                    onEnabledChange = viewModel::setBgmEnabled,
                    onVolumeChange = viewModel::setBgmVolume,
                )
                VolumeControl(
                    label = stringResource(R.string.settings_se),
                    enabled = current.soundEffectsEnabled,
                    volume = current.soundEffectVolume,
                    onEnabledChange = viewModel::setSoundEffectsEnabled,
                    onVolumeChange = viewModel::setSoundEffectVolume,
                    onVolumeChosen = viewModel::previewSoundEffect,
                )

                HorizontalDivider()
                SwitchRow(
                    label = stringResource(R.string.settings_notifications),
                    caption = stringResource(R.string.settings_notifications_caption),
                    checked = current.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )

                HorizontalDivider()
                SectionTitle(stringResource(R.string.settings_section_about))
                Text(
                    text = stringResource(R.string.settings_app_version, stringResource(R.string.app_name), viewModel.versionName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { showLicenses = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_licenses))
                }
                OutlinedButton(onClick = { showPolicy = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_policy))
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
    if (showPolicy) {
        InfoDialog(
            title = stringResource(R.string.settings_policy),
            body = stringResource(R.string.policy_body),
            onDismiss = { showPolicy = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/**
 * A mute switch with a volume slider under it. Muting greys the slider out but keeps its
 * position, so unmuting returns to the same volume.
 *
 * @param onVolumeChosen called when the finger leaves the slider.
 */
@Composable
private fun VolumeControl(
    label: String,
    enabled: Boolean,
    volume: Float,
    onEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChosen: () -> Unit = {},
) {
    val percent = (volume * 100).roundToInt()
    val volumeDescription = stringResource(R.string.settings_volume_description, label)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                onValueChangeFinished = onVolumeChosen,
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { contentDescription = volumeDescription },
            )
            Text(
                text = stringResource(R.string.label_percent, percent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(48.dp),
            )
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
