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
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.ui.components.pressScale
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * Floating tool bar. Water and fertilizer act on the currently selected plant and are disabled
 * while none is selected ([canCare] false). The hoe is a tool that stays in hand until it is
 * put down: its button is lit while it is ([hoeActive]).
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
            ActionButton("⛏️", stringResource(R.string.action_hoe), onToggleHoe, selected = hoeActive)
            ActionButton("💧", stringResource(R.string.action_water), onWater, enabled = canCare)
            ActionButton("🧪", stringResource(R.string.action_fertilize, CareForPlantUseCase.FERTILIZER_COST), onFertilize, enabled = canCare)
            ActionButton("🎒", stringResource(R.string.action_inventory), onOpenInventory)
            ActionButton("🛒", stringResource(R.string.action_shop), onOpenShop)
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
