package com.studioguu.miniaturegarden.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.studioguu.miniaturegarden.R
import androidx.compose.ui.platform.testTag
import com.studioguu.miniaturegarden.domain.model.GardenFeature
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenTip
import com.studioguu.miniaturegarden.domain.model.Gift
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.ui.components.CarvedButton
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.ItemIcon
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * The popup a gift box opens into: a wooden board with a parchment inset, what it holds, and a
 * button to take it. "Later" leaves the box where it is.
 */
@Composable
fun GiftDialog(gift: Gift, onReceive: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        WoodBoard(title = "\uD83C\uDF81 " + (gift.title ?: stringResource(R.string.gift_first_title))) {
            Text(
                text = gift.message ?: stringResource(R.string.gift_first_text),
                color = GardenColors.Ink,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            for (item in gift.items) {
                val thing = GardenItem.fromItemId(item.itemId) ?: continue
                ItemIcon(thing, 40.sp)
                Text(
                    text = stringResource(R.string.gift_item, itemDisplayName(thing), item.amount),
                    color = GardenColors.Ink,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (gift.coins > 0L) {
                Text(stringResource(R.string.gift_coins, gift.coins), color = GardenColors.Ink, fontWeight = FontWeight.Bold)
            }
            // Two buttons side by side, of one make and one size: wooden planks with a dark edge
            // and cream lettering.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WoodButton(
                    text = stringResource(R.string.gift_later),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).testTag(GIFT_LATER_TAG),
                )
                WoodButton(
                    text = stringResource(R.string.gift_receive),
                    onClick = onReceive,
                    modifier = Modifier.weight(1f).testTag(GIFT_RECEIVE_TAG),
                )
            }
        }
    }
}

/** Test tags of the gift box's two buttons. */
const val GIFT_LATER_TAG = "gift_later"
const val GIFT_RECEIVE_TAG = "gift_receive"

/**
 * What the current first step asks for; null when there is nothing to say. The hoe's step has two
 * halves, told apart by the tilled earth that comes of the first.
 */
@StringRes
fun tutorialHint(step: TutorialStep?, objects: List<GridObject> = emptyList()): Int? = when (step) {
    TutorialStep.TILL_SOIL -> if (objects.any { it.isLooseSoil }) R.string.tutorial_hint_ridge else R.string.tutorial_hint_till
    TutorialStep.BUY_SEED -> R.string.tutorial_hint_shop
    TutorialStep.PLANT_SEED -> R.string.tutorial_hint_plant_seed
    TutorialStep.WATER_SEEDLING -> R.string.tutorial_hint_water
    TutorialStep.FEED_SEEDLING -> R.string.tutorial_hint_feed
    TutorialStep.HARVEST -> R.string.tutorial_hint_harvest
    else -> null
}

/** The name and the few words that introduce [feature] as it is handed over. */
@Composable
private fun featureIntroduction(feature: GardenFeature): Pair<String, String> = when (feature) {
    GardenFeature.EDIT_MODE -> stringResource(R.string.reveal_edit_name) to stringResource(R.string.reveal_edit_text)
    GardenFeature.BLESSED_RAIN -> stringResource(R.string.reveal_rain_name) to stringResource(R.string.reveal_rain_text)
    GardenFeature.COLLECTION -> stringResource(R.string.reveal_collection_name) to stringResource(R.string.reveal_collection_text)
    GardenFeature.CAMERA -> stringResource(R.string.reveal_camera_name) to stringResource(R.string.reveal_camera_text)
    GardenFeature.POST -> stringResource(R.string.reveal_post_name) to stringResource(R.string.reveal_post_text)
}

/** A board of the first steps: a few words and one button, which stays until the player goes on. */
@Composable
private fun TutorialBoard(title: String, text: String, button: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        WoodBoard(title = title) {
            Text(
                text = text,
                color = GardenColors.Ink,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
            )
            WoodButton(text = button, onClick = onClose, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The first thing a new player reads. */
@Composable
fun WelcomeDialog(onClose: () -> Unit) = TutorialBoard(
    title = stringResource(R.string.tutorial_welcome_title),
    text = stringResource(R.string.tutorial_welcome_text),
    button = stringResource(R.string.tutorial_welcome_ok),
    onClose = onClose,
)

/** What the fertilizer did and did not do, and the one time that time is brought on. */
@Composable
fun PracticeGrowDialog(onClose: () -> Unit) = TutorialBoard(
    title = stringResource(R.string.tutorial_grow_title),
    text = stringResource(R.string.tutorial_grow_text),
    button = stringResource(R.string.tutorial_grow_ok),
    onClose = onClose,
)

/** A word of thanks after the first harvest; the practice garden goes with its button. */
@Composable
fun PracticeDoneDialog(onClose: () -> Unit) = TutorialBoard(
    title = stringResource(R.string.tutorial_thanks_title),
    text = stringResource(R.string.tutorial_thanks_text),
    button = stringResource(R.string.tutorial_thanks_ok),
    onClose = onClose,
)

/** The way out of the first steps, always in reach. */
@Composable
fun SkipTutorialButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CarvedButton(text = stringResource(R.string.tutorial_skip), onClick = onClick, modifier = modifier.testTag(SKIP_TUTORIAL_TAG))
}

const val SKIP_TUTORIAL_TAG = "skip_tutorial"

/** A word of advice on what the player has just met, gone at their tap and not seen again. */
@Composable
fun GardenTipSign(tip: GardenTip, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp).clickable(onClick = onClose),
        shape = RoundedCornerShape(12.dp),
        color = GardenColors.Parchment.copy(alpha = 0.95f),
        border = BorderStroke(2.dp, GardenColors.WoodLight),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("\uD83D\uDCA1", fontSize = 18.sp)
            Text(stringResource(gardenTipText(tip)), color = GardenColors.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            Text("\u00D7", color = GardenColors.InkSoft, fontSize = 18.sp)
        }
    }
}

@StringRes
private fun gardenTipText(tip: GardenTip): Int = when (tip) {
    GardenTip.NO_FOOD -> R.string.tip_no_food
    GardenTip.WITHERED -> R.string.tip_withered
    GardenTip.NEW_PAGE -> R.string.tip_new_page
    GardenTip.VISITOR -> R.string.tip_visitor
}

/**
 * The board that closes the first steps: a wooden board like the crate's, which stays until
 * the player is ready to go on, and sends them off to make the garden their own.
 */
@Composable
fun TutorialDoneDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        WoodBoard(title = stringResource(R.string.tutorial_done_title)) {
            Text("🌱 🌷 🏡 🌻", fontSize = 28.sp)
            Text(
                text = stringResource(R.string.tutorial_done_text),
                color = GardenColors.Ink,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
            )
            WoodButton(text = stringResource(R.string.tutorial_done_ok), onClick = onClose, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Tells the player that [feature] has just been put in their hands and where to find it. Laid
 * over the garden rather than shown as a dialog, so that the button it is about, which lights up
 * at the same time, can be seen beside it.
 */
@Composable
fun FeatureRevealCard(feature: GardenFeature, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val (name, text) = featureIntroduction(feature)
    WoodBoard(title = "✨ " + stringResource(R.string.reveal_title), modifier = modifier) {
        Text(name, color = GardenColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            text = text,
            color = GardenColors.Ink,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        WoodButton(text = stringResource(R.string.reveal_ok), onClick = onClose, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A wooden board with a title burnt into it and a parchment inset holding [content]. */
@Composable
internal fun WoodBoard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.popIn(),
        shape = RoundedCornerShape(14.dp),
        color = GardenColors.Wood,
        border = BorderStroke(3.dp, GardenColors.WoodDark),
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = GardenColors.Cream,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GardenColors.Parchment,
                border = BorderStroke(1.dp, GardenColors.WoodLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { content() }
            }
        }
    }
}

/**
 * Lights up a button that has just been handed over: a warm ring breathes round it and a small
 * "NEW" tag sits on its corner, for as long as [active].
 */
fun Modifier.revealGlow(active: Boolean): Modifier = composed {
    if (!active) return@composed this
    val pulse by rememberInfiniteTransition(label = "reveal").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(REVEAL_PULSE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    // A rounded outline that follows the button's own shape: a circle round a round button,
    // a pill round a long one.
    drawBehind {
        val grow = 4.dp.toPx() + 4.dp.toPx() * pulse
        val topLeft = Offset(-grow, -grow)
        val area = Size(size.width + 2 * grow, size.height + 2 * grow)
        val corner = CornerRadius(area.minDimension / 2f)
        drawRoundRect(GardenColors.Gold.copy(alpha = 0.22f + 0.28f * pulse), topLeft, area, corner)
        drawRoundRect(GardenColors.Gold.copy(alpha = 0.9f), topLeft, area, corner, style = Stroke(width = 3.dp.toPx()))
    }
}

/** The small tag that sits on a button that has just been handed over. */
@Composable
fun RevealBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.reveal_badge),
        color = GardenColors.Cream,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(GardenColors.WoodDark, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private const val REVEAL_PULSE_MILLIS = 900

/**
 * A small wooden sign above the tool bar with the current first step. Like the other panels
 * there, it is laid over the garden and fades in and out in place.
 */
@Composable
fun TutorialHint(@StringRes text: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GardenColors.Parchment.copy(alpha = 0.95f),
        border = BorderStroke(2.dp, GardenColors.WoodLight),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("✨", fontSize = 18.sp)
            Text(stringResource(text), color = GardenColors.Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
