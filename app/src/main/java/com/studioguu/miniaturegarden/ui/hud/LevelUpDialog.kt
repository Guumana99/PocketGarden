package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.LevelUpNotice
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji
import com.studioguu.miniaturegarden.ui.onboarding.WoodBoard
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/** How many of the newly opened items the board names; the shop shows the rest. */
private const val MAX_ITEMS_SHOWN = 6

/**
 * The level-up board, in the middle of the screen. It stays until the player taps OK: tapping
 * beside it or pressing back does not close it, so a level-up is never missed the way a
 * floating message could be. Speaks of new items only when the level opened some.
 */
@Composable
fun LevelUpDialog(notice: LevelUpNotice, onOk: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        WoodBoard(title = stringResource(R.string.level_up_title)) {
            Text(
                text = stringResource(R.string.level_up_level, notice.toLevel),
                color = GardenColors.Ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            val items = notice.unlockedItems
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.level_up_no_unlock),
                    color = GardenColors.Ink,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.level_up_unlocked),
                    color = GardenColors.Ink,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items.take(MAX_ITEMS_SHOWN).forEach { item ->
                        Text(
                            text = "${itemEmoji(item)} ${itemDisplayName(item)}",
                            color = GardenColors.InkSoft,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            WoodButton(text = stringResource(R.string.level_up_ok), onClick = onOk, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
