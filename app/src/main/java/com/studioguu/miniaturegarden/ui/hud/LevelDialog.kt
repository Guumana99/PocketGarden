package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * What the level's bubble says ([LevelDialog]): the level, the experience through it towards the
 * next and how far that is, and what the next level puts in the shop.
 *
 * @property gained experience since this level was reached.
 * @property needed experience from this level to the next.
 * @property percent how far through this level, 0 to 100, rounded down (100 only at the next level).
 * @property nextItems what the next level puts in the shop; empty for nothing, and at the top level.
 */
data class LevelInfo(
    val level: Int,
    val isMax: Boolean,
    val gained: Long,
    val needed: Long,
    val percent: Int,
    val progress: Float,
    val nextItems: List<GardenItem>,
) {
    companion object {
        fun of(level: Int, xp: Long): LevelInfo {
            if (level >= LevelTable.MAX_LEVEL) return LevelInfo(level, true, 0L, 0L, 100, 1f, emptyList())
            val from = LevelTable.xpForLevel(level)
            val needed = LevelTable.xpForLevel(level + 1) - from
            val gained = (xp - from).coerceIn(0L, needed)
            val progress = LevelTable.progressInLevel(xp, level)
            return LevelInfo(
                level = level,
                isMax = false,
                gained = gained,
                needed = needed,
                percent = if (needed <= 0L) 100 else (gained * 100 / needed).toInt(),
                progress = progress,
                nextItems = GardenItem.unlockedBetween(level, level + 1),
            )
        }
    }
}

/**
 * The level's bubble, opened from the level on the status bar: made like the weather's
 * ([WeatherDialog]), closed by a tap outside it. "Lv.12", the experience through the level with
 * a gauge ("経験値 340 / 500（68％）"), and a line for what the next level opens, if anything,
 * worded by how many things it opens. At
 * the top level it says so instead of the experience.
 */
@Composable
fun LevelDialog(info: LevelInfo, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.status_level, info.level),
        actions = listOf(DialogAction(stringResource(R.string.action_close), onDismiss)),
    ) {
        if (info.isMax) {
            Text(stringResource(R.string.level_info_max), style = MaterialTheme.typography.titleMedium, color = GardenColors.Ink, modifier = Modifier.testTag(LEVEL_MAX_TAG))
            return@ParchmentDialog
        }
        Text(
            stringResource(R.string.level_info_xp, info.gained, info.needed, info.percent),
            style = MaterialTheme.typography.titleSmall,
            color = GardenColors.Ink,
            modifier = Modifier.testTag(LEVEL_XP_TAG),
        )
        val track = GardenColors.WoodDark.copy(alpha = 0.18f)
        val fill = GardenColors.Leaf
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(track, cornerRadius = radius)
                    val filled = size.width * info.progress
                    if (filled > 0f) drawRoundRect(fill, size = Size(filled, size.height), cornerRadius = radius)
                },
        )
        if (info.nextItems.isNotEmpty()) {
            // One: "次の Lv で 池 が手に入ります"; two: "… 小屋 と パプリカの苗 が…"; more: the first two "… や … など".
            // Each name kept whole on its line ("パプリカの苗" broke after "の"): word joiners between its letters.
            val names = info.nextItems.take(2).map { itemDisplayName(it).toList().joinToString(WORD_JOINER) }
            val line = when (info.nextItems.size) {
                1 -> stringResource(R.string.level_info_next_one, names[0])
                2 -> stringResource(R.string.level_info_next_two, names[0], names[1])
                else -> stringResource(R.string.level_info_next_many, names[0], names[1])
            }
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = GardenColors.InkSoft,
                modifier = Modifier.testTag(LEVEL_NEXT_TAG),
            )
        }
    }
}

private const val WORD_JOINER = "⁠"

const val LEVEL_XP_TAG = "level_xp"
const val LEVEL_NEXT_TAG = "level_next"
const val LEVEL_MAX_TAG = "level_max"
