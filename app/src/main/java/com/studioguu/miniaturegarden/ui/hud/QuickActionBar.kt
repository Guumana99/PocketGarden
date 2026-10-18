package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.studioguu.miniaturegarden.domain.model.TutorialTool
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.ui.components.pressScale
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * Floating tool bar. Water and fertilizer act on the currently selected plant and are disabled
 * while none is selected ([canCare] false). The hoe is a tool that stays in hand until it is
 * put down: its button is lit while it is ([hoeActive]).
 *
 * @param allows which tools can be picked up now: during the first steps only the one the step
 *   asks for, in edit mode only the shop; the others are greyed out and cannot be tapped.
 * @param bagActive something from the bag is in hand: the bag's button is lit, and tapping it
 *   puts that down again, as the hoe's does for the hoe (#50).
 */
@Composable
fun QuickActionBar(
    canCare: Boolean,
    hoeActive: Boolean,
    onToggleHoe: () -> Unit,
    onWater: () -> Unit,
    onFertilize: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
    allows: (TutorialTool) -> Boolean = { true },
    bagActive: Boolean = false,
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
            ActionButton("⛏️", stringResource(R.string.action_hoe), onToggleHoe, selected = hoeActive, enabled = allows(TutorialTool.HOE))
            ActionButton("💧", stringResource(R.string.action_water), onWater, enabled = canCare && allows(TutorialTool.WATER))
            ActionButton(
                "🧪", stringResource(R.string.action_fertilize, CareForPlantUseCase.FERTILIZER_COST), onFertilize,
                enabled = canCare && allows(TutorialTool.FERTILIZE),
            )
            ActionButton("🎒", stringResource(R.string.action_inventory), onOpenInventory, enabled = allows(TutorialTool.BAG), selected = bagActive)
            ActionButton("🛒", stringResource(R.string.action_shop), onOpenShop, enabled = allows(TutorialTool.SHOP))
        }
    }
}

@Composable
private fun ActionButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
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
            Text(emoji, fontSize = 24.sp)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
