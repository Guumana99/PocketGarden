package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn

/**
 * Asked before the garden grows (#55): growing cannot be undone, and everything in the garden
 * moves one cell along to stay in the middle, so it is not done on a single tap of the symbol
 * building's panel. [size] is the size it grows to ([com.studioguu.miniaturegarden.domain.model.GardenExpansion]).
 * Cancelling, or tapping outside, leaves the garden as it is.
 */
@Composable
fun ExpandConfirmDialog(size: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expand_confirm_title)) },
        text = { Text(stringResource(R.string.expand_confirm_text, size)) },
        // Side by side and centred, cancel first, like the other wooden dialogs.
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WoodButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                WoodButton(text = stringResource(R.string.expand_confirm_ok), onClick = onConfirm)
            }
        },
    )
}
