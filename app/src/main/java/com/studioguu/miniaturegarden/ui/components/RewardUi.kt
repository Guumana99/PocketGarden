package com.studioguu.miniaturegarden.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.theme.GardenColors
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
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = null,
        actions = listOf(DialogAction(stringResource(R.string.action_cancel), onDismiss), DialogAction(confirmLabel, onConfirm)),
    ) {
        Text(message, color = GardenColors.Ink)
    }
}
