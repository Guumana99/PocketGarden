package com.studioguu.miniaturegarden.ui.shop

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureEffect
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.ui.actionResultMessage
import com.studioguu.miniaturegarden.ui.collection.flavorText
import com.studioguu.miniaturegarden.ui.components.CarvedButton
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.components.RewardConfirmDialog
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.findActivity
import com.studioguu.miniaturegarden.ui.components.pressScale
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji
import com.studioguu.miniaturegarden.ui.seasonEmoji
import com.studioguu.miniaturegarden.ui.seasonLabel
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * The shops, as a place rather than a list: the player walks in from the garden and stands in
 * the shop. Its things stand on wooden shelves; tapping one takes it off the shelf, and its card
 * comes up on the counter with what it is, what it costs, and "buy". The three shops down the
 * lane ([ShopStage]) are reached by the signboards along the top; "back to the garden" leaves.
 *
 * Also the place to claim the day's coin bonus, at the counter.
 */
@Composable
fun ShopStageScreen(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val inspecting by viewModel.inspecting.collectAsStateWithLifecycle()
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    val season by viewModel.season.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val bonusRemaining by viewModel.bonusRemaining.collectAsStateWithLifecycle()
    val adReady by viewModel.adReady.collectAsStateWithLifecycle()
    var confirmBonus by rememberSaveable { mutableStateOf(false) }
    /** Price of the item the player could not afford, while that dialog is open. */
    var shortFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val activity = LocalContext.current.findActivity()
    val bonusAvailable = bonusRemaining > 0 && adReady && activity != null
    val leave = {
        viewModel.leave()
        onLeave()
    }
    // Back first puts down what is in hand, then walks out of the shop.
    BackHandler { if (inspecting != null) viewModel.putBack() else leave() }

    val message = notice?.let { ShopMessage(it.text(), it.good) } ?: feedback?.let { purchase ->
        val failure = actionResultMessage(purchase.result)
        ShopMessage(
            text = if (failure == null) stringResource(R.string.shop_bought, itemDisplayName(purchase.item)) else stringResource(failure),
            good = purchase.result == ActionResult.SUCCESS,
        )
    }
    val inHand = inspecting?.let { item -> shelves.flatten().firstOrNull { it.item == item } }

    ShopStageContent(
        stage = stage,
        shelves = shelves,
        coins = coins,
        season = season,
        inHand = inHand,
        message = message,
        bonusLabel = stringResource(R.string.reward_bonus_button, RewardType.COIN_BONUS_AMOUNT, bonusRemaining, RewardType.COIN_BONUS.dailyLimit),
        bonusEnabled = bonusAvailable,
        onGoTo = viewModel::goTo,
        onInspect = viewModel::inspect,
        onPutBack = viewModel::putBack,
        onBuy = { item -> if (coins >= item.price) viewModel.buy(item) else shortFor = item.price },
        onBonus = { confirmBonus = true },
        onLeave = leave,
        modifier = modifier,
    )

    shortFor?.let { price ->
        ParchmentDialog(
            onDismissRequest = { shortFor = null },
            title = stringResource(R.string.msg_not_enough_coins),
            buttons = {
                CarvedButton(text = stringResource(R.string.action_close), onClick = { shortFor = null })
                WoodButton(
                    text = stringResource(R.string.reward_bonus_short, RewardType.COIN_BONUS_AMOUNT),
                    onClick = {
                        shortFor = null
                        confirmBonus = true
                    },
                    enabled = bonusAvailable,
                )
            },
        ) {
            Text(stringResource(R.string.shop_short_by, price - coins), color = GardenColors.Ink)
        }
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

/** A line said at the counter: what was bought, or why not. [good] decides its colour. */
data class ShopMessage(val text: String, val good: Boolean)

/**
 * The shop stage itself, drawn from its state alone, so that it can be shown and tested
 * without the view model.
 *
 * @param inHand the thing taken off the shelf, with its card up; null while browsing.
 */
@Composable
fun ShopStageContent(
    stage: ShopStage,
    shelves: List<List<ShelfItem>>,
    coins: Long,
    season: Season,
    inHand: ShelfItem?,
    message: ShopMessage?,
    bonusLabel: String,
    bonusEnabled: Boolean,
    onGoTo: (ShopStage) -> Unit,
    onInspect: (GardenItem) -> Unit,
    onPutBack: () -> Unit,
    onBuy: (GardenItem) -> Unit,
    onBonus: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().testTag(SHOP_STAGE_TAG)) {
        // The room: each shop has its own walls. The new room fades in quickly over the old one,
        // which stays as it is until it is covered, so nothing behind the shop shows through.
        AnimatedContent(targetState = stage, transitionSpec = { ShopFades.room() }, label = "room") { shown ->
            ShopRoom(shown, Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CarvedButton(text = stringResource(R.string.shop_leave), onClick = onLeave)
                Spacer(Modifier.weight(1f))
                CoinPill(coins)
            }
            Signboards(stage, onGoTo, Modifier.fillMaxWidth().padding(top = 10.dp))
            AnimatedContent(
                targetState = stage,
                // The old shelves go first, quickly, then the new ones come: never both at half.
                transitionSpec = { ShopFades.shelves() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "shelves",
            ) { shown ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (shown == ShopStage.SEEDS) {
                        ChalkNote(stringResource(R.string.shop_season_caption))
                    }
                    // Shelves belong to the shop on show; while one fades out, the other's are not drawn twice.
                    if (shown == stage) {
                        for (shelf in shelves) Shelf(shelf, inHand?.item, onInspect)
                    }
                }
            }
            Counter(message.takeIf { inHand == null }, bonusLabel, bonusEnabled, onBonus, Modifier.padding(bottom = 8.dp))
        }
        // The card of the thing in hand rises onto the counter, over everything below the shelves.
        var lastInHand by remember { mutableStateOf<ShelfItem?>(null) }
        if (inHand != null) lastInHand = inHand
        AnimatedVisibility(
            visible = inHand != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp),
        ) {
            lastInHand?.let { ItemCard(it, message, onBuy, onPutBack) }
        }
    }
}

/** Test tag of the stage's outermost box. */
const val SHOP_STAGE_TAG = "shop_stage"

/** What each shop is called on its signboard. */
@StringRes
fun shopName(stage: ShopStage): Int = when (stage) {
    ShopStage.SEEDS -> R.string.shop_stage_seeds
    ShopStage.WORKSHOP -> R.string.shop_stage_workshop
    ShopStage.SUNDRIES -> R.string.shop_stage_sundries
}

/** The picture on each shop's signboard. */
fun shopEmoji(stage: ShopStage): String = when (stage) {
    ShopStage.SEEDS -> "🌱"
    ShopStage.WORKSHOP -> "🔨"
    ShopStage.SUNDRIES -> "🪴"
}

/** The colours of a shop's room: wall from top to bottom, and the panelling along its foot. */
private data class RoomColors(val wallTop: Color, val wallBottom: Color, val panel: Color)

private fun roomColors(stage: ShopStage): RoomColors = when (stage) {
    ShopStage.SEEDS -> RoomColors(Color(0xFFEFF3DC), Color(0xFFD9E6BC), Color(0xFF9DB872))
    ShopStage.WORKSHOP -> RoomColors(Color(0xFFF2E2C4), Color(0xFFDDBF93), Color(0xFFB0835A))
    ShopStage.SUNDRIES -> RoomColors(Color(0xFFEDEBF3), Color(0xFFD7D6E6), Color(0xFF9C9BB8))
}

/** The room drawn behind the shelves: wall, a band of panelling, and a plank floor. */
@Composable
private fun ShopRoom(stage: ShopStage, modifier: Modifier) {
    val colors = roomColors(stage)
    Box(
        modifier = modifier.drawBehind {
            drawRect(Brush.verticalGradient(listOf(colors.wallTop, colors.wallBottom)))
            // Faint wallpaper stripes.
            val stripe = size.width / 14f
            var x = stripe / 2f
            while (x < size.width) {
                drawRect(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Size(stripe * 0.35f, size.height * 0.7f))
                x += stripe
            }
            // Panelling, and the floor below it.
            val panelTop = size.height * 0.7f
            drawRect(colors.panel, Offset(0f, panelTop), Size(size.width, size.height * 0.12f))
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, panelTop), Size(size.width, 4f))
            val floorTop = size.height * 0.82f
            drawRect(Brush.verticalGradient(listOf(GardenColors.WoodGrain, GardenColors.Wood)), Offset(0f, floorTop), Size(size.width, size.height - floorTop))
            var plank = floorTop + (size.height - floorTop) / 4f
            while (plank < size.height) {
                drawRect(GardenColors.WoodDark.copy(alpha = 0.25f), Offset(0f, plank), Size(size.width, 2f))
                plank += (size.height - floorTop) / 4f
            }
        },
    )
}

/** The three shops' signboards; the one the player stands in hangs brighter. */
@Composable
private fun Signboards(current: ShopStage, onGoTo: (ShopStage) -> Unit, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (stage in ShopStage.entries) {
            val here = stage == current
            val interaction = remember { MutableInteractionSource() }
            val name = stringResource(shopName(stage))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction)
                    .background(if (here) GardenColors.WoodLight else GardenColors.Wood.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = withTick { onGoTo(stage) })
                    .semantics { selected = here }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(shopEmoji(stage), fontSize = 20.sp)
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                    color = if (here) GardenColors.Ink else GardenColors.Cream,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CoinPill(coins: Long) {
    Surface(shape = RoundedCornerShape(50), color = GardenColors.Cream, shadowElevation = 2.dp) {
        Text(
            text = stringResource(R.string.shop_coins, coins),
            style = MaterialTheme.typography.titleSmall,
            color = GardenColors.Ink,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** A small note pinned on the wall above the seed shelves. */
@Composable
private fun ChalkNote(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = GardenColors.Parchment.copy(alpha = 0.92f), shadowElevation = 1.dp) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = GardenColors.InkSoft,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** One wooden shelf with up to [ShopShelves.PER_SHELF] things standing on it. */
@Composable
private fun Shelf(things: List<ShelfItem>, inHand: GardenItem?, onInspect: (GardenItem) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // The plank, with its shadow on the wall below.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(14.dp)
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, size.height), Size(size.width, 8f))
                }
                .background(Brush.verticalGradient(listOf(GardenColors.WoodLight, GardenColors.Wood)), RoundedCornerShape(3.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (thing in things) ShelfThing(thing, picked = thing.item == inHand, onInspect = onInspect)
            // A short last shelf keeps its things where they would stand on a full one.
            repeat(ShopShelves.PER_SHELF - things.size) { Spacer(Modifier.width(ITEM_WIDTH)) }
        }
    }
}

/** A thing on the shelf, with its tag hanging in front: the price, or why it is not for sale. */
@Composable
private fun ShelfThing(thing: ShelfItem, picked: Boolean, onInspect: (GardenItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val name = itemDisplayName(thing.item)
    Column(
        modifier = Modifier
            .width(ITEM_WIDTH)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = withTick { onInspect(thing.item) })
            .semantics { contentDescription = name }
            .testTag(shelfTag(thing.item)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Lifted off the shelf while its card is up; only an outline of where it stood is left.
        Text(
            text = itemEmoji(thing.item),
            fontSize = 36.sp,
            modifier = Modifier.alpha(
                when {
                    picked -> 0.25f
                    thing.tag == ShelfTag.LOCKED -> LOCKED_ALPHA
                    else -> 1f
                }
            ),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = GardenColors.Ink,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (thing.tag == ShelfTag.LOCKED) LOCKED_ALPHA else 1f),
        )
        PriceTag(thing)
    }
}

/** The paper tag in front of a thing on the shelf: its price (or why it is not for sale), and how many are in the bag. */
@Composable
private fun PriceTag(thing: ShelfItem) {
    val text = when (thing.tag) {
        ShelfTag.FOR_SALE, ShelfTag.SHORT_OF_COINS -> "🪙 ${thing.item.price}"
        ShelfTag.LOCKED -> lockedText(thing.item)
        ShelfTag.OWNED -> stringResource(R.string.shop_owned)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = GardenColors.Cream,
        shadowElevation = 1.dp,
        modifier = Modifier.padding(top = 2.dp).alpha(if (thing.tag == ShelfTag.SHORT_OF_COINS) UNAFFORDABLE_ALPHA else 1f),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = GardenColors.InkSoft, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
    Text(
        text = stringResource(R.string.shop_held, thing.held),
        style = MaterialTheme.typography.labelSmall,
        color = GardenColors.InkSoft,
        maxLines = 1,
        modifier = Modifier.padding(top = 1.dp).testTag(heldTag(thing.item)),
    )
}

@Composable
private fun lockedText(item: GardenItem): String = when (item.unlock) {
    // Short enough for the shelf's tag; the card says how many pages (shop_card_pages).
    is UnlockCondition.CollectionPages -> stringResource(R.string.shop_locked_pages)
    else -> stringResource(R.string.shop_locked, item.unlockLevel)
}

/** The counter along the bottom: the day's coin bonus, and what was last said at it. */
@Composable
private fun Counter(message: ShopMessage?, bonusLabel: String, bonusEnabled: Boolean, onBonus: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MessageLine(message)
        WoodButton(text = bonusLabel, onClick = onBonus, enabled = bonusEnabled, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * What was said at the counter, in a line whose room is always kept, said or not: a message
 * coming or going never moves the card or the buttons round it.
 */
@Composable
private fun MessageLine(message: ShopMessage?) {
    Box(modifier = Modifier.fillMaxWidth().height(MESSAGE_LINE_HEIGHT).testTag(MESSAGE_LINE_TAG), contentAlignment = Alignment.CenterStart) {
        if (message != null) {
            Text(
                text = message.text,
                color = if (message.good) GardenColors.Leaf else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The height kept for the line of what was said, one line of body text. */
private val MESSAGE_LINE_HEIGHT = 24.dp

/** Test tag of the line of what was said. */
const val MESSAGE_LINE_TAG = "shop_message_line"

/** Test tag of how many of a thing are in the bag, on the shelf. */
fun heldTag(item: GardenItem): String = "held_${item.itemId}"

/**
 * The card of the thing taken off the shelf: what it is, in the encyclopedia's words, when it
 * grows or what it does, and what it costs, with "buy" or why it cannot be bought yet.
 */
@Composable
private fun ItemCard(thing: ShelfItem, message: ShopMessage?, onBuy: (GardenItem) -> Unit, onPutBack: () -> Unit) {
    val item = thing.item
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = GardenColors.Parchment,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().testTag(ITEM_CARD_TAG),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(56.dp).background(GardenColors.Cream, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(itemEmoji(item), fontSize = 32.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(itemDisplayName(item), style = MaterialTheme.typography.titleMedium, color = GardenColors.Ink)
                    Text(
                        text = stringResource(R.string.shop_held, thing.held),
                        style = MaterialTheme.typography.labelMedium,
                        color = GardenColors.InkSoft,
                        modifier = Modifier.testTag(CARD_HELD_TAG),
                    )
                    if (item.isSymbol) {
                        Text(stringResource(R.string.shop_only_one), style = MaterialTheme.typography.labelSmall, color = GardenColors.Leaf)
                    }
                }
            }
            CollectionEntry.fromObjectId(item.objectId)?.let { entry ->
                Text(stringResource(flavorText(entry)), style = MaterialTheme.typography.bodyMedium, color = GardenColors.InkSoft)
            }
            ItemFacts(item)
            MessageLine(message)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CarvedButton(text = stringResource(R.string.shop_put_back), onClick = onPutBack)
                Spacer(Modifier.weight(1f))
                when (thing.tag) {
                    ShelfTag.LOCKED -> Text(lockedText(item), style = MaterialTheme.typography.labelLarge, color = GardenColors.InkSoft)
                    ShelfTag.OWNED -> Text(stringResource(R.string.shop_owned), style = MaterialTheme.typography.labelLarge, color = GardenColors.InkSoft)
                    // Stays tappable when the player is short: the tap then explains, and offers the bonus.
                    ShelfTag.FOR_SALE, ShelfTag.SHORT_OF_COINS -> WoodButton(
                        text = stringResource(R.string.shop_buy, item.price),
                        onClick = { onBuy(item) },
                        modifier = Modifier.alpha(if (thing.tag == ShelfTag.SHORT_OF_COINS) UNAFFORDABLE_ALPHA else 1f),
                    )
                }
            }
        }
    }
}

/**
 * A line or two of what matters when choosing: what a building does, and how a thing that opens
 * with the encyclopedia is opened. (A seed's season is not repeated here: the shelf only holds
 * what is in season, #48.)
 */
@Composable
private fun ItemFacts(item: GardenItem) {
    val facts = buildList {
        (item.unlock as? UnlockCondition.CollectionPages)?.let { add(stringResource(R.string.shop_card_pages, it.pages)) }
        // What the symbol building is for: growing the garden.
        if (item.isSymbol) add(stringResource(R.string.shop_card_symbol))
        when (StructureEffect.forObjectId(item.objectId)) {
            StructureEffect.WELL -> add(stringResource(R.string.hint_well_effect))
            StructureEffect.HUT -> add(stringResource(R.string.hint_hut_effect))
            StructureEffect.WINDMILL -> add(stringResource(R.string.hint_windmill_effect))
            null -> Unit
        }
    }
    for (fact in facts) Text(fact, style = MaterialTheme.typography.bodySmall, color = GardenColors.Leaf)
}

/** Test tag of a thing on the shelf. */
fun shelfTag(item: GardenItem): String = "shelf_${item.itemId}"

/** Test tag of the card of the thing in hand. */
const val ITEM_CARD_TAG = "shop_item_card"

/** Test tag of how many of the thing in hand are in the bag, on its card. */
const val CARD_HELD_TAG = "shop_card_held"

private val ITEM_WIDTH = 96.dp
private const val LOCKED_ALPHA = 0.45f
private const val UNAFFORDABLE_ALPHA = 0.55f

/**
 * How the shop changes from one room to another when a signboard is tapped. Quick, so that the
 * tap feels answered at once, and never two rooms at half strength: that let the room before
 * show through for a moment.
 */
object ShopFades {
    /** The new room fading in over the old one. */
    const val ROOM_IN_MILLIS = 140

    /** The old shelves fading out, before the new ones come in. */
    const val SHELVES_OUT_MILLIS = 70

    const val SHELVES_IN_MILLIS = 130

    /** The new room comes in on top; the old one stays whole underneath until it is covered. */
    fun room(): ContentTransform =
        (fadeIn(tween(ROOM_IN_MILLIS)) togetherWith fadeOut(tween(durationMillis = 1, delayMillis = ROOM_IN_MILLIS)))
            .apply { targetContentZIndex = 1f }

    /** One after the other: out, then in. */
    fun shelves(): ContentTransform =
        fadeIn(tween(SHELVES_IN_MILLIS, delayMillis = SHELVES_OUT_MILLIS)) togetherWith fadeOut(tween(SHELVES_OUT_MILLIS))
}
