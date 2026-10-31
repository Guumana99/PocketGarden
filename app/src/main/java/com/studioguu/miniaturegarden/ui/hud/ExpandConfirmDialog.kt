package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * Asked before the garden grows (#55): growing costs [price] coins and cannot be undone, so it is
 * not done on a single tap of the workshop's board. The wording speaks of the garden growing "one
 * ring larger" rather than of a grid size: the player never sees cell counts. Cancelling, or
 * tapping outside, leaves the garden as it is.
 */
@Composable
fun ExpandConfirmDialog(price: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.expand_confirm_title),
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel), onDismiss),
            DialogAction(stringResource(R.string.expand_confirm_ok), onConfirm),
        ),
    ) {
        Text(stringResource(R.string.expand_confirm_text, price), color = GardenColors.Ink)
    }
}
