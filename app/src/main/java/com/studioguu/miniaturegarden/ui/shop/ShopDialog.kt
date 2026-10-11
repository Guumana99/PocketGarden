package com.studioguu.miniaturegarden.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.ui.actionResultMessage
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji

/** Buy seeds and decorations with coins. Purchases go straight into the inventory. */
@Composable
fun ShopDialog(
    onDismiss: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    // Start every visit without the previous visit's message.
    DisposableEffect(Unit) { onDispose { viewModel.clearFeedback() } }

    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shop_coins, coins), style = MaterialTheme.typography.titleSmall)
                for (item in viewModel.items) {
                    ShopRow(
                        item = item,
                        locked = !item.isUnlockedFor(playerStats),
                        affordable = coins >= item.price,
                        onBuy = { viewModel.buy(item) },
                    )
                }
                feedback?.let { (item, result) ->
                    val failure = actionResultMessage(result)
                    Text(
                        text = if (failure == null) {
                            stringResource(R.string.shop_bought, itemDisplayName(item))
                        } else {
                            stringResource(failure)
                        },
                        color = if (result == ActionResult.SUCCESS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ShopRow(item: GardenItem, locked: Boolean, affordable: Boolean, onBuy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A locked item stays listed, so the player can see what is coming, but its icon and name
        // are faded. The unlock level itself is left at full strength so it can be read.
        val fade = Modifier.alpha(if (locked) LOCKED_ALPHA else 1f)
        Text(itemEmoji(item), fontSize = 26.sp, modifier = fade)
        Text(itemDisplayName(item), style = MaterialTheme.typography.bodyLarge, modifier = fade.weight(1f))
        if (locked) {
            Text(
                text = stringResource(R.string.shop_locked, item.unlockLevel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(onClick = onBuy, enabled = affordable) {
                Text(stringResource(R.string.shop_buy, item.price))
            }
        }
    }
}

private const val LOCKED_ALPHA = 0.5f
