package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.FarmTool
import com.studioguu.miniaturegarden.domain.model.TutorialTool
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.ui.components.pressScale
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import com.studioguu.miniaturegarden.ui.toolStars

/**
 * Floating tool bar. Fertilizer acts on the currently selected plant and is disabled while none
 * is selected ([canCare] false). Watering is not on the bar: it is the "水やり" button of the
 * selected plant's panel ([ObjectStatusDialog]), which a plant needs tapping for anyway, and a
 * second watering can here only stood in the way. The hoe is a tool that stays in hand until it is
 * put down: its button is lit while it is ([hoeActive]).
 *
 * @param allows which tools can be picked up now: during the first steps only the one the step
 *   asks for, in edit mode only the shop; the others are greyed out and cannot be tapped.
 * @param bagActive something from the bag is in hand: the bag's button is lit, and tapping it
 *   puts that down again, as the hoe's does for the hoe (#50).
 * @param hoeLevel how far the hoe has come with use ([FarmTool]),
 *   shown on its button as small gold stars from level 2 ([toolStars]) while the hoe shows its
 *   level ([FarmTool.showsLevel]; hidden for now). The watering can's stars are on the panel's
 *   watering button.
 */
@Composable
fun QuickActionBar(
    canCare: Boolean,
    hoeActive: Boolean,
    onToggleHoe: () -> Unit,
    onFertilize: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
    allows: (TutorialTool) -> Boolean = { true },
    bagActive: Boolean = false,
    hoeLevel: Int = 1,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ActionButton(
                "⛏️", stringResource(R.string.action_hoe), onToggleHoe, selected = hoeActive, enabled = allows(TutorialTool.HOE),
                badge = levelBadge(hoeLevel).takeIf { FarmTool.HOE.showsLevel },
            )
            ActionButton(
                "🧪", stringResource(R.string.action_fertilize, CareForPlantUseCase.FERTILIZER_COST), onFertilize,
                enabled = canCare && allows(TutorialTool.FERTILIZE),
            )
            ActionButton("🎒", stringResource(R.string.action_inventory), onOpenInventory, enabled = allows(TutorialTool.BAG), selected = bagActive)
            ActionButton("🛒", stringResource(R.string.action_shop), onOpenShop, enabled = allows(TutorialTool.SHOP))
        }
    }
}

/** A tool's level as its button shows it: stars from level 2 on; a new tool shows none. */
private fun levelBadge(level: Int): String? = toolStars(level).takeIf { it.isNotEmpty() }

@Composable
private fun ActionButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    badge: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = withTick(onClick),
        enabled = enabled,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        modifier = Modifier
            .widthIn(min = 62.dp)
            .pressScale(interactionSource)
            .background(if (selected) GardenColors.WoodLight else Color.Transparent, RoundedCornerShape(20.dp)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Text(emoji, fontSize = 24.sp)
                if (badge != null) {
                    Text(
                        text = badge,
                        color = BADGE_GOLD,
                        fontSize = 8.sp,
                        letterSpacing = (-0.5).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 14.dp, y = (-2).dp)
                            .background(GardenColors.WoodDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 3.dp),
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The stars of a tool that has come on with use: gold leaf on the dark wood tag. */
private val BADGE_GOLD = Color(0xFFE6C36A)
