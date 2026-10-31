package com.studioguu.miniaturegarden.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * The game's main button: a plank of wood with a dark edge and cream lettering, that sinks a
 * little when pressed.
 */
@Composable
fun WoodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = withTick(onClick),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, if (enabled) GardenColors.WoodDark else GardenColors.WoodLight),
        colors = ButtonDefaults.buttonColors(
            containerColor = GardenColors.Wood,
            contentColor = GardenColors.Cream,
            disabledContainerColor = GardenColors.ParchmentDark,
            disabledContentColor = GardenColors.InkSoft.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier.pressScale(interactionSource),
    ) {
        Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
