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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.NotificationAccess
import com.studioguu.miniaturegarden.domain.model.TurnOnStep
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
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
 * switched on in the game and allowed by the device. Turning it on asks for whatever is missing;
 * when the device says no, a line explains that and offers the way to its settings. Looked at
 * again whenever the screen comes back, in case the player changed something there.
 */
@Composable
fun NotificationSettingRow(enabledInGame: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var looks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        looks++
        onPauseOrDispose { }
    }
    var refused by rememberSaveable { mutableStateOf(false) }
    val access = remember(enabledInGame, looks, refused) {
        NotificationAccess.of(Build.VERSION.SDK_INT, context.hasNotificationPermission(), context.deviceAllowsNotifications(), enabledInGame)
    }
    val requestPermission = rememberNotificationPermissionRequest { granted ->
        refused = !granted
        looks++
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
        Switch(
            checked = access == NotificationAccess.ON,
            onCheckedChange = { on ->
                if (!on) {
                    onEnabledChange(false)
                    return@Switch
                }
                onEnabledChange(true)
                when (NotificationAccess.whenTurnedOn(Build.VERSION.SDK_INT, context.hasNotificationPermission(), context.deviceAllowsNotifications())) {
                    TurnOnStep.REQUEST_PERMISSION -> requestPermission()
                    TurnOnStep.OPEN_DEVICE_SETTINGS -> context.openNotificationSettings()
                    TurnOnStep.NONE -> Unit
                }
            },
        )
    }
    // Said only when the device stands in the way, never as a reproach.
    if (access == NotificationAccess.BLOCKED_BY_DEVICE || (access == NotificationAccess.NEEDS_PERMISSION && refused)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.settings_notifications_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { context.openNotificationSettings() }) {
                Text(stringResource(R.string.settings_notifications_open_device))
            }
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
        Surface(
            modifier = Modifier.popIn(),
            shape = RoundedCornerShape(14.dp),
            color = GardenColors.Wood,
            border = BorderStroke(3.dp, GardenColors.WoodDark),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔔 " + stringResource(R.string.notification_invite_title),
                    color = GardenColors.Cream,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GardenColors.Parchment,
                    border = BorderStroke(1.dp, GardenColors.WoodLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌷 💧 🏡", fontSize = 26.sp)
                        Text(
                            text = stringResource(R.string.notification_invite_text),
                            color = GardenColors.Ink,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onLater) { Text(stringResource(R.string.notification_invite_later), color = GardenColors.Cream) }
                    WoodButton(text = stringResource(R.string.notification_invite_ok), onClick = onAccept)
                }
            }
        }
    }
}
