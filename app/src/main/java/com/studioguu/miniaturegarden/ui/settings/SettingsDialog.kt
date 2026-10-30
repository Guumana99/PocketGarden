package com.studioguu.miniaturegarden.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.ui.components.CarvedButton
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.components.WoodPanel
import com.studioguu.miniaturegarden.ui.components.WoodSlider
import com.studioguu.miniaturegarden.ui.components.WoodTabs
import com.studioguu.miniaturegarden.ui.components.WoodToggle
import com.studioguu.miniaturegarden.ui.notifications.NotificationSettingRow
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import kotlin.math.roundToInt

/** The settings, one tab per kind, so new ones have somewhere to go. */
internal enum class SettingsTab(val label: Int) {
    SOUND(R.string.settings_tab_sound),
    ABOUT(R.string.settings_tab_about),
    OTHER(R.string.settings_tab_other),
}

/** What the settings can change. [SettingsViewModel] does it for real; a test can record it. */
interface SettingsActions {
    fun setBgmEnabled(enabled: Boolean)
    fun setBgmVolume(volume: Float)
    fun setSoundEffectsEnabled(enabled: Boolean)
    fun setSoundEffectVolume(volume: Float)
    fun previewSoundEffect()
    fun setNotificationsEnabled(enabled: Boolean)
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onReplayTutorial: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Nothing is drawn until the saved values are in, so no control starts in the wrong place.
    val current = settings ?: return
    SettingsContent(settings = current, versionName = viewModel.versionName, actions = viewModel, onDismiss = onDismiss, onReplayTutorial = onReplayTutorial)
}

/**
 * The settings on a parchment sheet: tabs along the top, and under them each group of settings
 * on a wooden board of its own.
 *
 * @param notificationRow the notification switch, which asks the device for permission; a test
 *   can put a plain one in its place.
 */
@Composable
internal fun SettingsContent(
    settings: AppSettings,
    versionName: String,
    actions: SettingsActions,
    onDismiss: () -> Unit,
    onReplayTutorial: (() -> Unit)? = null,
    notificationRow: @Composable (enabled: Boolean, onChange: (Boolean) -> Unit) -> Unit = { enabled, onChange ->
        NotificationSettingRow(enabledInGame = enabled, onEnabledChange = onChange)
    },
) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.SOUND) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showDisclaimer by rememberSaveable { mutableStateOf(false) }

    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_title),
        buttons = { CarvedButton(text = stringResource(R.string.action_close), onClick = onDismiss) },
    ) {
        WoodTabs(
            labels = SettingsTab.entries.map { stringResource(it.label) },
            selectedIndex = tab.ordinal,
            onSelect = { tab = SettingsTab.entries[it] },
        )
        Column(
            modifier = Modifier.heightIn(min = TAB_MIN_HEIGHT, max = TAB_MAX_HEIGHT).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                SettingsTab.SOUND -> SoundTab(settings, actions)
                SettingsTab.ABOUT -> AboutTab(
                    versionName = versionName,
                    onShowLicenses = { showLicenses = true },
                    onShowDisclaimer = { showDisclaimer = true },
                    onReplayTutorial = onReplayTutorial,
                )
                SettingsTab.OTHER -> WoodPanel {
                    notificationRow(settings.notificationsEnabled, actions::setNotificationsEnabled)
                }
            }
        }
    }

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
private fun SoundTab(current: AppSettings, actions: SettingsActions) {
    VolumeControl(
        label = stringResource(R.string.settings_bgm),
        enabled = current.bgmEnabled,
        volume = current.bgmVolume,
        defaultVolume = AppSettings.DEFAULT_BGM_VOLUME,
        onEnabledChange = actions::setBgmEnabled,
        onVolumeChange = actions::setBgmVolume,
    )
    VolumeControl(
        label = stringResource(R.string.settings_se),
        enabled = current.soundEffectsEnabled,
        volume = current.soundEffectVolume,
        defaultVolume = AppSettings.DEFAULT_SOUND_EFFECT_VOLUME,
        onEnabledChange = actions::setSoundEffectsEnabled,
        onVolumeChange = actions::setSoundEffectVolume,
        onVolumeChosen = actions::previewSoundEffect,
    )
}

@Composable
private fun AboutTab(versionName: String, onShowLicenses: () -> Unit, onShowDisclaimer: () -> Unit, onReplayTutorial: (() -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    WoodPanel {
        Text(
            text = stringResource(R.string.settings_app_version, stringResource(R.string.app_name), versionName),
            style = MaterialTheme.typography.bodyLarge,
            color = GardenColors.Ink,
        )
    }
    WoodPanel {
        if (onReplayTutorial != null) {
            CarvedButton(text = stringResource(R.string.settings_replay_tutorial), onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth())
        }
        CarvedButton(
            text = stringResource(R.string.settings_contact),
            onClick = { openContactMail(context, versionName) },
            modifier = Modifier.fillMaxWidth(),
        )
        CarvedButton(text = stringResource(R.string.settings_licenses), onClick = onShowLicenses, modifier = Modifier.fillMaxWidth())
        CarvedButton(
            text = stringResource(R.string.settings_privacy),
            // Opens in the browser. A device with no browser at all simply does nothing.
            onClick = { runCatching { uriHandler.openUri(privacyPolicyUrl) } },
            modifier = Modifier.fillMaxWidth(),
        )
        CarvedButton(text = stringResource(R.string.settings_disclaimer), onClick = onShowDisclaimer, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * One sound on a board of its own: its name and mute switch, a volume slider under them, and a
 * button that puts the volume back to [defaultVolume]. Muting greys the slider out but keeps
 * its position.
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
    WoodPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = GardenColors.Ink, modifier = Modifier.weight(1f))
            WoodToggle(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        // The slider speaks for itself: no number beside it.
        WoodSlider(
            value = volume,
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChosen,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = volumeDescription },
        )
        CarvedButton(
            text = stringResource(R.string.settings_reset_volume),
            onClick = {
                onVolumeChange(defaultVolume)
                onVolumeChosen()
            },
            enabled = !atDefault,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** A page of text on parchment, scrolling when long, with a close button. */
@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { CarvedButton(text = stringResource(R.string.action_close), onClick = onDismiss) },
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = GardenColors.Ink,
            modifier = Modifier.heightIn(max = INFO_MAX_HEIGHT).verticalScroll(rememberScrollState()),
        )
    }
}

private val TAB_MIN_HEIGHT = 240.dp
private val TAB_MAX_HEIGHT = 420.dp
private val INFO_MAX_HEIGHT = 420.dp
