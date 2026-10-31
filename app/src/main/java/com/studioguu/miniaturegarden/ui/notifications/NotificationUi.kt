package com.studioguu.miniaturegarden.ui.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.NotificationAccess
import com.studioguu.miniaturegarden.domain.model.TurnOnStep
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.WoodToggle
import com.studioguu.miniaturegarden.ui.components.findActivity
import com.studioguu.miniaturegarden.ui.onboarding.WoodBoard
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/** Whether POST_NOTIFICATIONS is granted; always true below Android 13, where it is not asked for. */
fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < NotificationAccess.PERMISSION_SDK ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Whether the device's own settings let this app post notifications. */
fun Context.deviceAllowsNotifications(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/** Opens this app's page of notification settings on the device. */
fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * A way to ask for the notification permission. Calling the returned function shows the system's
 * question (Android 13+) and reports the answer; below Android 13 it reports "granted" at once.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= NotificationAccess.PERMISSION_SDK) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(true)
            }
        }
    }
}

/**
 * The switch in the settings. It shows notifications as on only when they will really be posted:
 * switched on in the game and allowed by the device, so it can never be on while the device
 * says no. Turning it on on Android 13+ without the permission does not turn it on yet: a board
 * says why the permission is needed, and its OK brings up the system's question; the switch goes
 * on once the answer is yes ([NotificationAccess.afterPermissionAnswer]). When only the device's
 * own settings stand in the way, the player is taken there instead; when they come back having
 * allowed it, the switch goes on by itself. Looked at again whenever the screen comes back, in
 * case the player changed something there.
 */
@Composable
fun NotificationSettingRow(enabledInGame: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var looks by remember { mutableIntStateOf(0) }
    // Set while the player is away in the device's settings to allow what the switch needs.
    var awaitingDevice by rememberSaveable { mutableStateOf(false) }
    fun deviceReady() = NotificationAccess.deviceReady(Build.VERSION.SDK_INT, context.hasNotificationPermission(), context.deviceAllowsNotifications())
    LifecycleResumeEffect(Unit) {
        looks++
        if (awaitingDevice) {
            awaitingDevice = false
            if (deviceReady()) onEnabledChange(true)
        }
        onPauseOrDispose { }
    }
    val access = remember(enabledInGame, looks) {
        NotificationAccess.of(Build.VERSION.SDK_INT, context.hasNotificationPermission(), context.deviceAllowsNotifications(), enabledInGame)
    }
    val deviceBlocks = remember(looks) { !deviceReady() }
    var explainPermission by rememberSaveable { mutableStateOf(false) }

    fun follow(step: TurnOnStep) {
        when (step) {
            TurnOnStep.NONE -> onEnabledChange(true)
            TurnOnStep.EXPLAIN_PERMISSION -> explainPermission = true
            TurnOnStep.OPEN_DEVICE_SETTINGS -> {
                awaitingDevice = true
                context.openNotificationSettings()
            }
            TurnOnStep.STAY_OFF -> Unit
        }
    }

    fun rationale(): Boolean = context.findActivity()?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
    } ?: false
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        looks++
        follow(NotificationAccess.afterPermissionAnswer(granted, context.deviceAllowsNotifications(), rationaleBefore, rationale()))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_notifications_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WoodToggle(
            checked = access == NotificationAccess.ON,
            onCheckedChange = { on ->
                if (!on) {
                    onEnabledChange(false)
                    return@WoodToggle
                }
                follow(NotificationAccess.whenTurnedOn(Build.VERSION.SDK_INT, context.hasNotificationPermission(), context.deviceAllowsNotifications()))
            },
        )
    }
    if (explainPermission) {
        NotificationPermissionDialog(
            onOk = {
                explainPermission = false
                rationaleBefore = rationale()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { explainPermission = false },
        )
    }
    // Said only when the device stands in the way, never as a reproach.
    if (deviceBlocks) {
        Text(
            stringResource(R.string.settings_notifications_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Says why the switch needs the device's permission before the system asks for it. OK brings
 * up the system's question; closing it leaves the switch off.
 */
@Composable
fun NotificationPermissionDialog(onOk: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        WoodBoard(
            title = "🔔 " + stringResource(R.string.notification_permission_title),
            actions = listOf(
                DialogAction(stringResource(R.string.notification_permission_cancel), onDismiss),
                DialogAction(stringResource(R.string.notification_permission_ok), onOk),
            ),
        ) {
            Text("✉️ 🍂", fontSize = 26.sp)
            Text(
                text = stringResource(R.string.notification_permission_text),
                color = GardenColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Asked once, after the first steps are done (Android 13+ only, where the device needs the
 * permission): would the player like to hear about the garden? A wooden board like the starter
 * crate's popup; "not now" is as easy as yes, and the settings can change it later.
 */
@Composable
fun NotificationInviteDialog(onAccept: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        WoodBoard(
            title = "🔔 " + stringResource(R.string.notification_invite_title),
            actions = listOf(
                DialogAction(stringResource(R.string.notification_invite_later), onLater),
                DialogAction(stringResource(R.string.notification_invite_ok), onAccept),
            ),
        ) {
            Text("🌷 💧 🏡", fontSize = 26.sp)
            Text(
                text = stringResource(R.string.notification_invite_text),
                color = GardenColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
