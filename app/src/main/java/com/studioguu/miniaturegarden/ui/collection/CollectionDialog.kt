package com.studioguu.miniaturegarden.ui.collection

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.itemEmoji
import com.studioguu.miniaturegarden.ui.objectDisplayName
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// The book's own colours: a leather cover, aged paper, brown ink.
private val Leather = Color(0xFF5B3A29)
private val LeatherDark = Color(0xFF3B2418)
private val GoldLeaf = Color(0xFFE6C36A)
private val Paper = Color(0xFFF4E6C3)
private val PaperShade = Color(0xFFE2CFA0)
private val Ink = Color(0xFF3E2A1E)
private val InkFaded = Color(0xFF8A6F55)

private val BookFont = FontFamily.Serif
private val SPREAD_HEIGHT = 330.dp

/**
 * The garden encyclopedia, laid out as an open book: one spread per entry, with its picture on
 * the left page and its description on the right. Swipe, or use the arrows, to turn the page.
 * The text is written the way an old cartridge game would have put it, in kana with spaces.
 */
@Composable
fun CollectionDialog(
    onDismiss: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val atlas by viewModel.atlas.collectAsStateWithLifecycle()
    val atlasImage = remember(atlas) { atlas.bitmap?.asImageBitmap() }
    val entries = CollectionEntry.entries
    val pagerState = rememberPagerState(pageCount = { entries.size })
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .popIn()
                .fillMaxWidth(0.94f)
                .background(Leather, RoundedCornerShape(14.dp))
                .border(3.dp, LeatherDark, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Cover(progress)

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(SPREAD_HEIGHT)) { page ->
                Spread(
                    number = page + 1,
                    entry = entries[page],
                    record = progress.recordOf(entries[page]),
                    atlas = atlas,
                    atlasImage = atlasImage,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PageButton("◀", enabled = pagerState.currentPage > 0) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                }
                Text(
                    text = stringResource(R.string.collection_page, pagerState.currentPage + 1, entries.size),
                    color = GoldLeaf,
                    fontFamily = BookFont,
                    style = MaterialTheme.typography.titleSmall,
                )
                PageButton("▶", enabled = pagerState.currentPage < entries.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close), color = GoldLeaf, fontFamily = BookFont)
                }
            }
        }
    }
}

/** Title and progress, stamped on the cover in gold. */
@Composable
private fun Cover(progress: CollectionProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.collection_title),
            color = GoldLeaf,
            fontFamily = BookFont,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.collection_progress, progress.discovered, progress.total, progress.percent),
            color = GoldLeaf,
            fontFamily = BookFont,
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { progress.discovered.toFloat() / progress.total },
            color = GoldLeaf,
            trackColor = LeatherDark,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (progress.isComplete) {
                stringResource(R.string.collection_complete)
            } else {
                stringResource(
                    R.string.collection_complete_reward,
                    CollectionEntry.COMPLETION_COINS,
                    CollectionEntry.COMPLETION_XP,
                )
            },
            color = GoldLeaf.copy(alpha = 0.85f),
            fontFamily = BookFont,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Two facing pages with a shaded gutter between them. */
@Composable
private fun Spread(
    number: Int,
    entry: CollectionEntry,
    record: CollectionRecord?,
    atlas: SpriteAtlas,
    atlasImage: ImageBitmap?,
) {
    val discovered = record != null
    Row(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
        // Left page: the picture, in a frame.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Paper, Paper, PaperShade)))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperShade.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .border(2.dp, InkFaded, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Illustration(entry, discovered, atlas, atlasImage)
            }
        }
        // The gutter.
        Box(Modifier.width(2.dp).fillMaxHeight().background(InkFaded))
        // Right page: the words.
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(PaperShade, Paper, Paper)))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.collection_number, number),
                color = InkFaded,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = if (discovered) objectDisplayName(entry.objectId) else stringResource(R.string.collection_unknown),
                color = Ink,
                fontFamily = BookFont,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(InkFaded))
            if (record != null) {
                Text(
                    text = stringResource(flavorText(entry)),
                    color = Ink,
                    fontFamily = BookFont,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        if (entry.trigger == CollectionTrigger.HARVEST) {
                            R.string.collection_count_harvest
                        } else {
                            R.string.collection_count_placement
                        },
                        record.count,
                    ),
                    color = InkFaded,
                    fontFamily = BookFont,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                Text(
                    text = stringResource(
                        if (entry.trigger == CollectionTrigger.HARVEST) {
                            R.string.collection_hint_harvest
                        } else {
                            R.string.collection_hint_placement
                        }
                    ),
                    color = InkFaded,
                    fontFamily = BookFont,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * The entry's picture: its sprite from the atlas when there is one (a full-grown plant, for
 * plants), otherwise its emoji. An undiscovered entry shows only a dark silhouette or a "？".
 */
@Composable
private fun Illustration(entry: CollectionEntry, discovered: Boolean, atlas: SpriteAtlas, atlasImage: ImageBitmap?) {
    val sprite = atlas[illustrationAssetId(entry)]
    if (sprite != null && atlasImage != null) {
        Canvas(Modifier.fillMaxSize()) { drawFitted(atlasImage, sprite, if (discovered) null else Ink) }
    } else {
        Text(
            text = if (discovered) entryEmoji(entry) else "？",
            fontSize = 64.sp,
            color = Ink,
            fontFamily = BookFont,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFitted(
    atlasImage: ImageBitmap,
    sprite: AssetManifest,
    silhouette: Color?,
) {
    val scale = min(size.width / sprite.width, size.height / sprite.height)
    val width = (sprite.width * scale).roundToInt().coerceAtLeast(1)
    val height = (sprite.height * scale).roundToInt().coerceAtLeast(1)
    drawImage(
        image = atlasImage,
        srcOffset = IntOffset(sprite.atlasX, sprite.atlasY),
        srcSize = IntSize(sprite.width, sprite.height),
        dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
        dstSize = IntSize(width, height),
        colorFilter = silhouette?.let { ColorFilter.tint(it, BlendMode.SrcIn) },
        filterQuality = FilterQuality.Medium,
    )
}

@Composable
private fun PageButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) GoldLeaf else GoldLeaf.copy(alpha = 0.3f), fontSize = 18.sp)
    }
}

/** Plants are shown in full bloom; everything else by its own sprite. */
private fun illustrationAssetId(entry: CollectionEntry): String {
    if (!entry.objectId.startsWith(GridObject.PLANT_ID_PREFIX)) return entry.objectId
    return "${entry.objectId}_stage${PlantSpecies.forObjectId(entry.objectId).growthStages}"
}

/** The shop item for the same object carries the emoji. */
private fun entryEmoji(entry: CollectionEntry): String =
    GardenItem.entries.firstOrNull { it.objectId == entry.objectId }?.let(::itemEmoji) ?: "？"

@StringRes
private fun flavorText(entry: CollectionEntry): Int = when (entry) {
    CollectionEntry.TULIP -> R.string.flavor_tulip
    CollectionEntry.SUNFLOWER -> R.string.flavor_sunflower
    CollectionEntry.ROSE -> R.string.flavor_rose
    CollectionEntry.GRASS -> R.string.flavor_grass
    CollectionEntry.PEBBLE -> R.string.flavor_pebble
    CollectionEntry.ROCK -> R.string.flavor_rock
    CollectionEntry.RIVER -> R.string.flavor_river
    CollectionEntry.WELL -> R.string.flavor_well
    CollectionEntry.HUT -> R.string.flavor_hut
}
