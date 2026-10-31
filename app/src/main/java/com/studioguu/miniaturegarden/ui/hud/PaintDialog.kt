package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PaintColour
import com.studioguu.miniaturegarden.domain.model.PaintPart
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.paintColourName
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * Chooses the colours of [building], its roof and its walls apart. Every colour can be had from
 * the first, for nothing, and changed at any time; the building takes each at the tap.
 */
@Composable
fun PaintDialog(building: GridObject, onPaint: (PaintPart, PaintColour) -> Unit, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.paint_title),
        actions = listOf(DialogAction(stringResource(R.string.action_close), onDismiss)),
    ) {
        PaintRow(stringResource(R.string.paint_roof), building, PaintPart.ROOF, onPaint)
        PaintRow(stringResource(R.string.paint_wall), building, PaintPart.WALL, onPaint)
    }
}

@Composable
private fun PaintRow(label: String, building: GridObject, part: PaintPart, onPaint: (PaintPart, PaintColour) -> Unit) {
    val chosen = BuildingPaint.colourOf(building, part)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = GardenColors.InkSoft, style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (colour in BuildingPaint.colours(building.objectId, part)) {
                val name = stringResource(paintColourName(colour))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = withTick { onPaint(part, colour) })
                        .semantics {
                            contentDescription = name
                            selected = colour == chosen
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(colour.argb), CircleShape)
                            .border(
                                width = if (colour == chosen) 3.dp else 1.dp,
                                color = if (colour == chosen) GardenColors.WoodDark else GardenColors.WoodLight,
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = name,
                        color = GardenColors.Ink,
                        fontWeight = if (colour == chosen) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
