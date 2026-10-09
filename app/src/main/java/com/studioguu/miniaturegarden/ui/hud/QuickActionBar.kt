package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R

/**
 * Floating tool bar. Water and fertilizer act on the currently selected plant and are disabled
 * while none is selected ([canCare] false).
 */
@Composable
fun QuickActionBar(
    canCare: Boolean,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActionButton("💧", stringResource(R.string.action_water), onWater, enabled = canCare)
            ActionButton("🧪", stringResource(R.string.action_fertilize), onFertilize, enabled = canCare)
            ActionButton("🎒", stringResource(R.string.action_inventory), onOpenInventory)
            ActionButton("🛒", stringResource(R.string.action_shop), onOpenShop)
        }
    }
}

@Composable
private fun ActionButton(emoji: String, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.widthIn(min = 72.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
