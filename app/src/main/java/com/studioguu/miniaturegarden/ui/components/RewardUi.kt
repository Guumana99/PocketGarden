package com.studioguu.miniaturegarden.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.studioguu.miniaturegarden.R
import java.time.LocalDate

/** Today as a day number in the device's time zone; the day's reward claims are counted against it. */
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

/** The activity behind a context, looking through any wrappers (dialogs wrap theirs). */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Asks before a reward is claimed: cancel on the left, the claim on the right. */
@Composable
fun RewardConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        confirmButton = {
            WoodButton(text = confirmLabel, onClick = onConfirm)
        },
    )
}
