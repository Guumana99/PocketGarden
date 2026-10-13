package com.studioguu.miniaturegarden.ui.shop

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.ui.actionResultMessage
import com.studioguu.miniaturegarden.ui.components.RewardConfirmDialog
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.findActivity
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji

/**
 * Buy seeds, structures and decorations with coins; purchases go straight into the inventory.
 * Also the place to claim the day's coin bonuses.
 */
@Composable
fun ShopDialog(
    onDismiss: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    val bonusRemaining by viewModel.bonusRemaining.collectAsStateWithLifecycle()
    val adReady by viewModel.adReady.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(ItemCategory.SEED) }
    var confirmBonus by rememberSaveable { mutableStateOf(false) }
    /** Price of the item the player could not afford, while that dialog is open. */
    var shortFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val activity = LocalContext.current.findActivity()
    val bonusAvailable = bonusRemaining > 0 && adReady && activity != null
    // Start every visit without the previous visit's message.
    DisposableEffect(Unit) { onDispose { viewModel.clearFeedback() } }

    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shop_coins, coins), style = MaterialTheme.typography.titleSmall)
                TabRow(selectedTabIndex = category.ordinal, containerColor = Color.Transparent) {
                    for (tab in ItemCategory.entries) {
                        Tab(
                            selected = tab == category,
                            onClick = { category = tab },
                            text = { Text(stringResource(categoryLabel(tab)), maxLines = 1) },
                        )
                    }
                }
                // A fixed minimum keeps the dialog from jumping when a shelf has fewer items.
                Column(
                    // A long shelf scrolls inside the dialog rather than pushing its buttons off screen.
                    modifier = Modifier.heightIn(min = SHELF_MIN_HEIGHT, max = SHELF_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (item in GardenItem.shelf(category)) {
                        ShopRow(
                            item = item,
                            locked = !item.isUnlockedFor(playerStats),
                            affordable = coins >= item.price,
                            onBuy = { if (coins >= item.price) viewModel.buy(item) else shortFor = item.price },
                        )
                    }
                }
                val purchase = feedback
                val noticeText = notice
                if (noticeText != null) {
                    Text(
                        text = noticeText.text(),
                        color = if (noticeText.good) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (purchase != null) {
                    val failure = actionResultMessage(purchase.result)
                    Text(
                        text = if (failure == null) {
                            stringResource(R.string.shop_bought, itemDisplayName(purchase.item))
                        } else {
                            stringResource(failure)
                        },
                        color = if (purchase.result == ActionResult.SUCCESS) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Greyed out when today's bonuses are used up or none is ready to be shown.
                WoodButton(
                    text = stringResource(
                        R.string.reward_bonus_button,
                        RewardType.COIN_BONUS_AMOUNT,
                        bonusRemaining,
                        RewardType.COIN_BONUS.dailyLimit,
                    ),
                    onClick = { confirmBonus = true },
                    enabled = bonusAvailable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )

    shortFor?.let { price ->
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { shortFor = null },
            title = { Text(stringResource(R.string.msg_not_enough_coins)) },
            text = { Text(stringResource(R.string.shop_short_by, price - coins)) },
            dismissButton = {
                TextButton(onClick = { shortFor = null }) { Text(stringResource(R.string.action_close)) }
            },
            confirmButton = {
                WoodButton(
                    text = stringResource(R.string.reward_bonus_short, RewardType.COIN_BONUS_AMOUNT),
                    onClick = {
                        shortFor = null
                        confirmBonus = true
                    },
                    enabled = bonusAvailable,
                )
            },
        )
    }

    if (confirmBonus) {
        RewardConfirmDialog(
            message = stringResource(R.string.reward_bonus_confirm, RewardType.COIN_BONUS_AMOUNT),
            confirmLabel = stringResource(R.string.reward_bonus_confirm_ok),
            onConfirm = {
                confirmBonus = false
                activity?.let(viewModel::claimBonus)
            },
            onDismiss = { confirmBonus = false },
        )
    }
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
                text = when (val condition = item.unlock) {
                    is UnlockCondition.CollectionPages -> stringResource(R.string.shop_locked_pages, condition.pages)
                    else -> stringResource(R.string.shop_locked, item.unlockLevel)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Stays tappable when the player is short: the tap then explains, and offers the bonus.
            WoodButton(
                text = stringResource(R.string.shop_buy, item.price),
                onClick = onBuy,
                modifier = Modifier.alpha(if (affordable) 1f else UNAFFORDABLE_ALPHA),
            )
        }
    }
}

@StringRes
private fun categoryLabel(category: ItemCategory): Int = when (category) {
    ItemCategory.SEED -> R.string.shop_tab_seeds
    ItemCategory.STRUCTURE -> R.string.shop_tab_structures
    ItemCategory.DECORATION -> R.string.shop_tab_decorations
}

private const val LOCKED_ALPHA = 0.5f
private const val UNAFFORDABLE_ALPHA = 0.55f
private val SHELF_MIN_HEIGHT = 200.dp
private val SHELF_MAX_HEIGHT = 330.dp
