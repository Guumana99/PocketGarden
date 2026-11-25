package com.studioguu.miniaturegarden.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.components.WoodPanel
import com.studioguu.miniaturegarden.ui.components.WoodSlider
import com.studioguu.miniaturegarden.ui.components.WoodToggle
import com.studioguu.miniaturegarden.ui.notifications.NotificationSettingRow
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import kotlin.math.roundToInt

/** The pages of the settings, each a button on the first page, so new ones have somewhere to go. */
internal enum class SettingsPage(val label: Int) {
    SOUND(R.string.settings_tab_sound),
    CONTROLS(R.string.settings_tab_controls),
    NOTIFICATIONS(R.string.settings_tab_notifications),
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
    fun setClosePanelOnHarvest(close: Boolean)
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
 * The settings on a parchment sheet, two levels deep: the first page is a button for each
 * [SettingsPage], and each page has its settings on wooden boards and a way back. The device's
 * back button (or a tap outside) on a page goes back to the first one.
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
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var showVersion by rememberSaveable { mutableStateOf(false) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val close = DialogAction(stringResource(R.string.action_close), onDismiss)

    ParchmentDialog(
        onDismissRequest = { if (page != null) page = null else onDismiss() },
        title = stringResource(page?.label ?: R.string.settings_title),
        actions = if (page == null) listOf(close) else listOf(DialogAction(stringResource(R.string.settings_back), { page = null }), close),
    ) {
        Column(
            // One height for every page, so the sheet does not change size from page to page (FB1125).
            modifier = Modifier.height(SETTINGS_PAGE_HEIGHT).testTag(SETTINGS_PAGE_TAG).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (page) {
                null -> WoodPanel {
                    SettingsPage.entries.forEach { CarvedButton(text = stringResource(it.label), onClick = { page = it }, modifier = Modifier.fillMaxWidth()) }
                }
                SettingsPage.SOUND -> SoundPage(settings, actions)
                SettingsPage.CONTROLS -> ControlsPage(settings, actions)
                SettingsPage.NOTIFICATIONS -> WoodPanel {
                    notificationRow(settings.notificationsEnabled, actions::setNotificationsEnabled)
                }
                SettingsPage.ABOUT -> AboutPage(
                    onShowVersion = { showVersion = true },
                    onShowCredits = { showCredits = true },
                    onShowLicenses = { showLicenses = true },
                )
                SettingsPage.OTHER -> OtherPage(versionName = versionName, onReplayTutorial = onReplayTutorial)
            }
        }
    }

    if (showVersion) {
        InfoDialog(
            title = stringResource(R.string.settings_version),
            body = stringResource(R.string.settings_app_version, stringResource(R.string.app_name), versionName),
            onDismiss = { showVersion = false },
        )
    }
    if (showCredits) CreditsDialog(onDismiss = { showCredits = false })
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}

/** The controls: whether a harvest closes the tile's action window. */
@Composable
private fun ControlsPage(current: AppSettings, actions: SettingsActions) {
    val label = stringResource(R.string.settings_close_on_harvest)
    WoodPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = GardenColors.Ink)
                Text(
                    stringResource(R.string.settings_close_on_harvest_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WoodToggle(
                checked = current.closePanelOnHarvest,
                onCheckedChange = actions::setClosePanelOnHarvest,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun SoundPage(current: AppSettings, actions: SettingsActions) {
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

/**
 * The version and the credits, each on a sheet of its own, and the open-source licences (which
 * have to be shown): the list the oss-licenses plugin writes at build time, shown on a sheet of our own
 * ([LicensesDialog]; FB1122, the library's screen crashed with the game's theme). The disclaimer that
 * used to be here is part of the privacy policy now.
 */
@Composable
private fun AboutPage(onShowVersion: () -> Unit, onShowCredits: () -> Unit, onShowLicenses: () -> Unit) {
    WoodPanel {
        CarvedButton(text = stringResource(R.string.settings_version), onClick = onShowVersion, modifier = Modifier.fillMaxWidth())
        CarvedButton(text = stringResource(R.string.settings_credits), onClick = onShowCredits, modifier = Modifier.fillMaxWidth())
        CarvedButton(text = stringResource(R.string.settings_licenses), onClick = onShowLicenses, modifier = Modifier.fillMaxWidth())
    }
}

/** The open-source libraries by name; one tapped shows its licence, with a way back to the list. */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember { OssLicenses.load(context) }
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    val library = libraries.firstOrNull { it.name == shown }
    ParchmentDialog(
        onDismissRequest = { if (library != null) shown = null else onDismiss() },
        title = library?.name ?: stringResource(R.string.settings_licenses),
        actions = listOfNotNull(
            library?.let { DialogAction(stringResource(R.string.settings_back), { shown = null }) },
            DialogAction(stringResource(R.string.action_close), onDismiss),
        ),
    ) {
        Column(
            modifier = Modifier.heightIn(max = PAGE_MAX_HEIGHT).verticalScroll(rememberScrollState()).testTag(LICENSES_LIST_TAG),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (library != null) {
                Text(library.text, style = MaterialTheme.typography.bodySmall, color = GardenColors.Ink)
            } else {
                for (item in libraries) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GardenColors.Ink,
                        modifier = Modifier.fillMaxWidth().clickable { shown = item.name }.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Test tag of the licences' list (and of a licence's text, in its place). */
internal const val LICENSES_LIST_TAG = "licenses_list"

/** Where the sounds may come from, by the sites' names; which file is whose is not known for each (ASSETS.md). */
internal val SOUND_CREDITS = listOf("ポケットサウンド", "タダノオト", "効果音ラボ")

/** The credits: under the heading of the sounds, the sites' names, one to a line. */
@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_credits),
        actions = listOf(DialogAction(stringResource(R.string.action_close), onDismiss)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.credits_sounds), style = MaterialTheme.typography.titleSmall, color = GardenColors.Ink)
            for (name in SOUND_CREDITS) {
                Text(stringResource(R.string.credits_item, name), style = MaterialTheme.typography.bodyMedium, color = GardenColors.Ink)
            }
        }
    }
}

/** The first steps again, a review on the store, a mail to us, and the privacy policy. */
@Composable
private fun OtherPage(versionName: String, onReplayTutorial: (() -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    WoodPanel {
        if (onReplayTutorial != null) {
            CarvedButton(text = stringResource(R.string.settings_replay_tutorial), onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth())
        }
        CarvedButton(text = stringResource(R.string.settings_review), onClick = { openStorePage(context) }, modifier = Modifier.fillMaxWidth())
        CarvedButton(
            text = stringResource(R.string.settings_contact),
            onClick = { openContactMail(context, versionName) },
            modifier = Modifier.fillMaxWidth(),
        )
        CarvedButton(
            text = stringResource(R.string.settings_privacy),
            // Opens in the browser. A device with no browser at all simply does nothing.
            onClick = { runCatching { uriHandler.openUri(privacyPolicyUrl) } },
            modifier = Modifier.fillMaxWidth(),
        )
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
        // The slider speaks for itself: no number beside it. The reset button shares its row, so a
        // board is two rows tall and both fit on a page of the common height (FB1125).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WoodSlider(
                value = volume,
                onValueChange = onVolumeChange,
                onValueChangeFinished = onVolumeChosen,
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { contentDescription = volumeDescription },
            )
            CarvedButton(
                text = stringResource(R.string.settings_reset_volume),
                onClick = {
                    onVolumeChange(defaultVolume)
                    onVolumeChosen()
                },
                enabled = !atDefault,
            )
        }
    }
}

/** A page of text on parchment, scrolling when long, with a close button. */
@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = title,
        actions = listOf(DialogAction(stringResource(R.string.action_close), onDismiss)),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = GardenColors.Ink,
            modifier = Modifier.heightIn(max = INFO_MAX_HEIGHT).verticalScroll(rememberScrollState()),
        )
    }
}

/** The height of every page of the settings (the one place it is set); a longer page scrolls inside it. */
internal val SETTINGS_PAGE_HEIGHT = 300.dp
internal const val SETTINGS_PAGE_TAG = "settings_page"
private val PAGE_MAX_HEIGHT = 420.dp
private val INFO_MAX_HEIGHT = 420.dp
