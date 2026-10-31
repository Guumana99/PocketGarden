package com.studioguu.miniaturegarden.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.studioguu.miniaturegarden.ui.theme.GardenColors

// The cosy pieces the menus are built from: parchment sheets in wooden frames, shallow wooden
// boards to group things on, and controls carved into them rather than stuck on top. They keep
// to a few tones of wood, parchment and olive, and stay low (inset, soft shadows) so that no
// button stands out of its board more than the rest. WoodButton stays the one raised plank, for
// the single thing a screen is for (buying, receiving).

private val DialogShape = RoundedCornerShape(14.dp)
private val PanelShape = RoundedCornerShape(10.dp)
private val CarvedShape = RoundedCornerShape(8.dp)

/** The room inside a dialog's parchment, all round. */
internal val DIALOG_PADDING = 16.dp

/** The room between the parts of a dialog, the buttons included. */
internal val DIALOG_GAP = 12.dp

/** The room between one button of a dialog and the next. */
private val DIALOG_BUTTON_GAP = 8.dp

/** One button of a dialog, for [DialogButtons]. */
data class DialogAction(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String? = null,
)

/**
 * The buttons at the foot of every dialog, so that all of them are laid out alike: wooden planks
 * filling the dialog's width. One or two stand side by side at one width, the way out on the left
 * and what the dialog is for on the right. Three or more, whose words would not fit, are stacked.
 */
@Composable
fun DialogButtons(actions: List<DialogAction>, modifier: Modifier = Modifier) {
    if (actions.size <= 2) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DIALOG_BUTTON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (action in actions) DialogButton(action, Modifier.weight(1f))
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DIALOG_BUTTON_GAP)) {
            for (action in actions) DialogButton(action, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DialogButton(action: DialogAction, modifier: Modifier) {
    WoodButton(
        text = action.text,
        onClick = action.onClick,
        enabled = action.enabled,
        modifier = action.testTag?.let { modifier.testTag(it) } ?: modifier,
    )
}

/**
 * A dialog on a sheet of pale parchment in a wooden frame, with a soft shadow under it: [title]
 * (if any) at the top, [content] in the middle and [actions] along the bottom ([DialogButtons]).
 * On a screen too short for all of it the content is squeezed, never the buttons: give it
 * something that scrolls.
 */
@Composable
fun ParchmentDialog(
    onDismissRequest: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    actions: List<DialogAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.popIn(),
            shape = DialogShape,
            color = GardenColors.ParchmentPale,
            border = BorderStroke(3.dp, GardenColors.Wood),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    // The edge of the sheet a little darker, as old paper is.
                    .drawBehind { drawParchmentEdge() }
                    .padding(DIALOG_PADDING),
                verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = GardenColors.Ink,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                // The content gives way first on a short screen (anything that scrolls inside it
                // shrinks), so the title and the buttons always stay in reach.
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
                    content = content,
                )
                if (actions.isNotEmpty()) DialogButtons(actions)
            }
        }
    }
}

/**
 * A shallow wooden board that groups a few things: pale planks with a little grain, sunk a
 * touch into the parchment (a shadow along its top edge, light along its bottom). [title], when
 * given, is cut into the top of it.
 */
@Composable
fun WoodPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Brush.verticalGradient(listOf(GardenColors.WoodLight, GardenColors.WoodLight.copy(alpha = 0.85f))))
            .drawBehind { drawWoodGrain() }
            .drawBehind { drawInset() }
            .border(1.5.dp, GardenColors.Wood.copy(alpha = 0.6f), PanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                color = GardenColors.WoodDark,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        content()
    }
}

/**
 * A button carved into the board it sits on: the same tones as its surroundings, sunk in rather
 * than raised, for the actions that are not the point of the screen (close, reset, read more).
 */
@Composable
fun CarvedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val click = withTick(onClick)
    Box(
        modifier = modifier
            .pressScale(interactionSource)
            .heightIn(min = 40.dp)
            .clip(CarvedShape)
            .background(if (enabled) GardenColors.Parchment else GardenColors.ParchmentDark.copy(alpha = 0.5f))
            .drawBehind { drawInset(depth = 0.22f) }
            .border(1.5.dp, if (enabled) GardenColors.Wood else GardenColors.WoodLight, CarvedShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = click)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) GardenColors.Ink else GardenColors.InkSoft.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** An on/off switch: an olive strap in a wooden frame when on, bare parchment when off. */
@Composable
fun WoodToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = GardenColors.Cream,
            checkedTrackColor = GardenColors.Olive,
            checkedBorderColor = GardenColors.WoodDark,
            uncheckedThumbColor = GardenColors.Wood,
            uncheckedTrackColor = GardenColors.ParchmentDark,
            uncheckedBorderColor = GardenColors.Wood,
            disabledCheckedTrackColor = GardenColors.Olive.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = GardenColors.ParchmentDark.copy(alpha = 0.5f),
        ),
    )
}

/** A slider: an olive strap filled up to a wooden knob. */
@Composable
fun WoodSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = GardenColors.Wood,
            activeTrackColor = GardenColors.Olive,
            inactiveTrackColor = GardenColors.ParchmentDark,
            disabledThumbColor = GardenColors.WoodLight,
            disabledActiveTrackColor = GardenColors.Olive.copy(alpha = 0.35f),
            disabledInactiveTrackColor = GardenColors.ParchmentDark.copy(alpha = 0.6f),
        ),
    )
}

/** Tabs across the top of a parchment sheet, the chosen one underlined in wood. */
@Composable
fun WoodTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = GardenColors.Ink,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                height = 3.dp,
                color = GardenColors.Wood,
            )
        },
        divider = { Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(GardenColors.WoodLight)) },
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                selectedContentColor = GardenColors.Ink,
                unselectedContentColor = GardenColors.InkSoft,
                text = { Text(label, maxLines = 1, fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal) },
            )
        }
    }
}

// --- Texture ----------------------------------------------------------------------------------

/** Faint darker bands along a sheet's edges, as on old paper. */
private fun DrawScope.drawParchmentEdge() {
    val edge = GardenColors.ParchmentDark.copy(alpha = 0.55f)
    val band = 10.dp.toPx()
    drawRect(Brush.verticalGradient(listOf(edge, Color.Transparent), endY = band))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, edge), startY = size.height - band, endY = size.height))
    drawRect(Brush.horizontalGradient(listOf(edge, Color.Transparent), endX = band))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, edge), startX = size.width - band, endX = size.width))
}

/** A few long, slightly wavy lines of grain across a plank, always in the same places. */
private fun DrawScope.drawWoodGrain() {
    val line = 1.dp.toPx()
    val gap = 9.dp.toPx()
    var y = gap * 0.6f
    var n = 0
    while (y < size.height) {
        val lift = if (n % 2 == 0) line else -line
        drawLine(GardenColors.WoodGrain.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width * 0.55f, y + lift), line)
        drawLine(GardenColors.WoodGrain.copy(alpha = 0.4f), Offset(size.width * 0.45f, y + lift), Offset(size.width, y), line)
        y += gap + (n % 3) * line * 2f
        n++
    }
}

/** Sunk in: a soft shadow under the top edge and a little light along the bottom one. */
private fun DrawScope.drawInset(depth: Float = 0.28f) {
    val band = 6.dp.toPx()
    drawRect(Brush.verticalGradient(listOf(GardenColors.WoodDark.copy(alpha = depth), Color.Transparent), endY = band))
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = depth * 0.8f)), startY = size.height - band / 2f, endY = size.height),
    )
}
