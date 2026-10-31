package com.studioguu.miniaturegarden.ui.collection

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.domain.model.BloomColours
import com.studioguu.miniaturegarden.domain.model.BloomRarity
import com.studioguu.miniaturegarden.domain.model.CollectionCategory
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.PageState
import com.studioguu.miniaturegarden.ui.components.ObjectPreview
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.objectDisplayName
import kotlinx.coroutines.launch

// The book's own colours: a leather cover, aged paper, brown ink.
internal val Leather = Color(0xFF5B3A29)
internal val LeatherDark = Color(0xFF3B2418)
internal val GoldLeaf = Color(0xFFE6C36A)
internal val Paper = Color(0xFFF4E6C3)
internal val PaperShade = Color(0xFFE2CFA0)
internal val Ink = Color(0xFF3E2A1E)
internal val InkFaded = Color(0xFF8A6F55)
internal val ReadyMark = Color(0xFFD8442F)

internal val BookFont = FontFamily.Serif
internal val SPREAD_HEIGHT = 330.dp
private const val REVEAL_MILLIS = 900

/**
 * The garden encyclopedia, laid out as an open book with a chapter per category: one spread per
 * entry, its picture on the left page and its description on the right. Swipe, or use the
 * arrows, to turn the page.
 *
 * A page does not fill itself in. Once its condition is met it is marked as ready, and it is the
 * player's tap on that page that turns its "？" into the picture.
 *
 * The text is written the way an old cartridge game would have put it, in kana with spaces.
 */
@Composable
fun CollectionDialog(
    onDismiss: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val atlas by viewModel.atlas.collectAsStateWithLifecycle()
    val terrain by viewModel.terrain.collectAsStateWithLifecycle()
    val natureNotes by viewModel.natureNotes.collectAsStateWithLifecycle()

    // Opening the book is what earns the day's bonus.
    LaunchedEffect(Unit) { viewModel.onOpened() }

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
            val loaded = progress
            Cover(loaded)
            if (loaded == null) {
                Spacer(Modifier.height(SPREAD_HEIGHT))
            } else {
                // Open at a chapter that has a page waiting, if there is one.
                var category by rememberSaveable {
                    mutableStateOf(CollectionCategory.entries.firstOrNull { loaded.readyIn(it) > 0 } ?: CollectionCategory.CROPS)
                }
                // The last chapter, the nature notes, is a different kind of page. With no page
                // waiting elsewhere, the book opens there when a creature newly met is in it.
                var natureOpen by rememberSaveable {
                    mutableStateOf(loaded.ready == 0 && (natureNotes?.unopened ?: 0) > 0)
                }
                CategoryTabs(
                    selected = category.takeUnless { natureOpen },
                    progress = loaded,
                    natureNew = (natureNotes?.unopened ?: 0) > 0,
                    onSelect = {
                        category = it
                        natureOpen = false
                    },
                    onSelectNature = { natureOpen = true },
                )
                val notes = natureNotes
                if (natureOpen && notes != null) {
                    NatureNotesChapter(notes, atlas, onOpenNote = viewModel::openNote, onDismiss = onDismiss)
                } else if (natureOpen) {
                    Spacer(Modifier.height(SPREAD_HEIGHT))
                } else key(category) {
                    Chapter(
                        entries = remember(category) { CollectionEntry.inCategory(category) },
                        progress = loaded,
                        atlas = atlas,
                        terrain = terrain,
                        onReveal = viewModel::reveal,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

/** Title and progress, stamped on the cover in gold. */
@Composable
private fun Cover(progress: CollectionProgress?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.collection_title),
            color = GoldLeaf,
            fontFamily = BookFont,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        val revealed = progress?.revealed ?: 0
        val total = progress?.total ?: CollectionEntry.entries.size
        Text(
            text = stringResource(R.string.collection_progress, revealed, total, progress?.percent ?: 0),
            color = GoldLeaf,
            fontFamily = BookFont,
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { revealed.toFloat() / total },
            color = GoldLeaf,
            trackColor = LeatherDark,
            modifier = Modifier.fillMaxWidth(),
        )
        val ready = progress?.ready ?: 0
        if (ready > 0) {
            Text(
                text = stringResource(R.string.collection_ready_count, ready),
                color = GoldLeaf.copy(alpha = 0.85f),
                fontFamily = BookFont,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The chapters, as bookmarks. A red dot marks a chapter with a page waiting to be filled in. The
 * last bookmark is the nature notes; [selected] is null while that is open. Its dot, [natureNew],
 * marks a creature newly met whose page has not been looked at.
 */
@Composable
private fun CategoryTabs(
    selected: CollectionCategory?,
    progress: CollectionProgress,
    natureNew: Boolean,
    onSelect: (CollectionCategory) -> Unit,
    onSelectNature: () -> Unit,
) {
    // Four bookmarks share the width, so their labels are a size smaller.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (category in CollectionCategory.entries) {
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(if (isSelected) Paper else LeatherDark)
                    .clickable(onClick = withTick { onSelect(category) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TabLabel(stringResource(categoryLabel(category)), isSelected)
                if (progress.readyIn(category) > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .size(8.dp)
                            .background(ReadyMark, CircleShape)
                    )
                }
            }
        }
        val natureSelected = selected == null
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(if (natureSelected) Paper else LeatherDark)
                .clickable(onClick = withTick(onSelectNature))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            TabLabel(stringResource(R.string.nature_tab), natureSelected)
            if (natureNew) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(8.dp)
                        .background(ReadyMark, CircleShape)
                )
            }
        }
    }
}

/** The smallest a bookmark's label may shrink to before it is left clipped. */
private val MinTabLabelSize = 8.sp

/**
 * A bookmark's label on one line. When it is wider than its bookmark (a narrow screen, or a
 * large font setting), it shrinks a step at a time until it fits, and is drawn only once it
 * does, so the cut-off text is never seen.
 */
@Composable
private fun TabLabel(text: String, selected: Boolean) {
    val style = MaterialTheme.typography.labelMedium
    var fontSize by remember(text, selected, style) { mutableStateOf(style.fontSize) }
    var fits by remember(text, selected, style) { mutableStateOf(false) }
    Text(
        text = text,
        color = if (selected) Ink else GoldLeaf,
        fontFamily = BookFont,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        style = style.copy(fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && fontSize > MinTabLabelSize) {
                fontSize = maxOf(fontSize.value * 0.9f, MinTabLabelSize.value).sp
            } else {
                fits = true
            }
        },
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .drawWithContent { if (fits) drawContent() },
    )
}

/**
 * One category: its contents page first, every entry at a glance, and a tap on one turns to its
 * page. A page waiting to be filled in is marked on the contents with a red dot.
 */
@Composable
private fun Chapter(
    entries: List<CollectionEntry>,
    progress: CollectionProgress,
    atlas: SpriteAtlas,
    terrain: TerrainTextures,
    onReveal: (CollectionEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // Page 0 is the contents; entry i is on page i + 1.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { entries.size + 1 })
    val scope = rememberCoroutineScope()

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(SPREAD_HEIGHT)) { page ->
        if (page == 0) {
            ContentsPage(entries.size) { index ->
                val entry = entries[index]
                EntryCell(entry, progress.stateOf(entry), progress.coloursOf(entry).firstOrNull(), atlas, terrain) {
                    scope.launch { pagerState.turnTo(index + 1) }
                }
            }
        } else {
            val entry = entries[page - 1]
            Spread(
                entry = entry,
                state = progress.stateOf(entry),
                enteredAt = progress.recordOf(entry)?.firstObtainedAt,
                colours = progress.coloursOf(entry),
                atlas = atlas,
                terrain = terrain,
                onReveal = { onReveal(entry) },
            )
        }
    }

    BookFooter(pagerState, entries.size, onDismiss)
}

/**
 * An entry on the contents page: its picture once filled in, otherwise a "？". A crop of several
 * colours is drawn in [colour], the first one the player harvested (#59).
 */
@Composable
private fun EntryCell(
    entry: CollectionEntry,
    state: PageState,
    colour: String?,
    atlas: SpriteAtlas,
    terrain: TerrainTextures,
    onClick: () -> Unit,
) {
    val revealed = state == PageState.REVEALED
    ContentsCell(
        label = if (revealed) objectDisplayName(entry.objectId) else stringResource(R.string.collection_unknown),
        known = revealed,
        onClick = onClick,
        highlighted = state == PageState.READY,
        mark = if (state == PageState.READY) ({ ReadyDot() }) else null,
    ) {
        if (revealed) {
            ObjectPreview(objectId = entry.objectId, atlas = atlas, terrain = terrain, modifier = Modifier.fillMaxSize(), bloomColour = colour)
        } else {
            Text(
                text = "？",
                fontSize = 26.sp,
                color = if (state == PageState.READY) Leather else InkFaded,
                fontFamily = BookFont,
            )
        }
    }
}

/** Two facing pages with a shaded gutter between them. */
@Composable
private fun Spread(
    entry: CollectionEntry,
    state: PageState,
    enteredAt: Long?,
    colours: List<String>,
    atlas: SpriteAtlas,
    terrain: TerrainTextures,
    onReveal: () -> Unit,
) {
    // 0 while the page shows its question mark, 1 once the picture is in. A page that is already
    // filled in when it comes on screen starts at 1; one that is filled in while on screen runs
    // from 0 to 1, and that run is the reveal.
    val reveal = remember { Animatable(if (state == PageState.REVEALED) 1f else 0f) }
    LaunchedEffect(state) {
        if (state == PageState.REVEALED) {
            reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
        } else {
            reveal.snapTo(0f)
        }
    }
    val ready = state == PageState.READY

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
                    .border(2.dp, if (ready) GoldLeaf else InkFaded, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (ready) Modifier.clickable(onClick = onReveal) else Modifier)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Illustration(entry, state, reveal.value, colours.firstOrNull(), atlas, terrain)
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
            PageHeader(entry.ordinal + 1, enteredAt.takeIf { state == PageState.REVEALED })
            Text(
                text = if (state == PageState.REVEALED) objectDisplayName(entry.objectId) else stringResource(R.string.collection_unknown),
                color = Ink,
                fontFamily = BookFont,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(InkFaded))
            when (state) {
                PageState.REVEALED -> Column(
                    // The words come in with the picture.
                    modifier = Modifier.graphicsLayer { alpha = reveal.value },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(flavorText(entry)),
                        color = Ink,
                        fontFamily = BookFont,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // A crop of several colours lists the ones the player has grown (#59).
                    val all = PlantSpecies.fromObjectId(entry.objectId)?.bloomColours.orEmpty()
                    if (all.size > 1) {
                        for (colour in all) {
                            BloomColourSlot(entry, colour, grown = colour in colours, atlas, terrain)
                        }
                    }
                }
                PageState.READY -> {
                    Text(
                        text = stringResource(R.string.collection_ready_hint),
                        color = Ink,
                        fontFamily = BookFont,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.collection_reveal_button),
                        color = Ink,
                        fontFamily = BookFont,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GoldLeaf)
                            .border(2.dp, Ink, RoundedCornerShape(8.dp))
                            .clickable(onClick = withTick(onReveal))
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                    )
                }
                PageState.LOCKED -> Text(
                    text = stringResource(
                        when {
                            entry.trigger == CollectionTrigger.HARVEST -> R.string.collection_hint_harvest
                            entry.objectId == LawnGrowth.OBJECT_ID -> R.string.collection_hint_lawn
                            else -> R.string.collection_hint_placement
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
 * One colour's place on a crop's page: the flower and its name once it has been harvested in that
 * colour; until then a shadow, and a line on where it is said to bloom.
 */
@Composable
private fun BloomColourSlot(entry: CollectionEntry, colour: String, grown: Boolean, atlas: SpriteAtlas, terrain: TerrainTextures) {
    val species = PlantSpecies.forObjectId(entry.objectId)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shadow = if (grown) {
            Modifier
        } else {
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(InkFaded, blendMode = BlendMode.SrcIn)
                }
        }
        ObjectPreview(
            objectId = entry.objectId,
            atlas = atlas,
            terrain = terrain,
            bloomColour = colour,
            modifier = Modifier.size(BLOOM_SLOT_SIZE).then(shadow).testTag(bloomSlotTag(colour, grown)),
        )
        Text(
            text = stringResource(if (grown) bloomColourLabel(colour) else bloomHint(BloomColours.rarityOf(species, colour))),
            color = if (grown) Ink else InkFaded,
            fontFamily = BookFont,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Test tag of one colour's place on the page. */
fun bloomSlotTag(colour: String, grown: Boolean): String = "bloom_${colour}_${if (grown) "grown" else "shadow"}"

private val BLOOM_SLOT_SIZE = 40.dp

/** Where a colour not grown yet is said to bloom. No numbers: only a hint. */
@StringRes
private fun bloomHint(rarity: BloomRarity): Int = when (rarity) {
    BloomRarity.COMMON -> R.string.bloom_hint_common
    BloomRarity.RARE -> R.string.bloom_hint_rare
    BloomRarity.VERY_RARE -> R.string.bloom_hint_very_rare
}

/**
 * The entry's picture: the thing itself as the garden draws it, standing on a patch of lawn.
 * Until the page is filled in there is only a "？", which beats gently once the page is ready.
 *
 * @param reveal 0 = question mark, 1 = picture; in between, the question mark swells and fades
 *   while a ring of gold spreads from the middle and the picture pops up out of it.
 */
@Composable
private fun Illustration(
    entry: CollectionEntry,
    state: PageState,
    reveal: Float,
    colour: String?,
    atlas: SpriteAtlas,
    terrain: TerrainTextures,
) {
    // Kept as a State and read only in the question mark's layer, so that its beat redraws that
    // one layer rather than recomposing the whole page on every frame.
    val beat: State<Float> = if (state == PageState.READY) {
        val transition = rememberInfiniteTransition(label = "ready")
        transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "beat",
        )
    } else {
        STILL_BEAT
    }
    Box(
        modifier = Modifier.fillMaxSize().drawBehind {
            if (reveal > 0f && reveal < 1f) {
                val reach = size.maxDimension * 0.75f
                drawCircle(GoldLeaf.copy(alpha = (1f - reveal) * 0.9f), radius = reach * reveal)
                drawCircle(
                    color = Color.White.copy(alpha = 1f - reveal),
                    radius = reach * reveal,
                    style = Stroke(width = 6.dp.toPx() * (1f - reveal)),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (reveal > 0f) {
            ObjectPreview(
                objectId = entry.objectId,
                atlas = atlas,
                terrain = terrain,
                bloomColour = colour,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Up from small, a little past full size, and back.
                    val pop = if (reveal < 0.7f) 0.4f + 0.7f * (reveal / 0.7f) else 1.1f - 0.1f * ((reveal - 0.7f) / 0.3f)
                    scaleX = pop
                    scaleY = pop
                    alpha = (reveal / 0.4f).coerceAtMost(1f)
                },
            )
        }
        if (reveal < 0.5f) {
            Text(
                text = "？",
                fontSize = 64.sp,
                color = if (state == PageState.READY) Leather else InkFaded,
                fontFamily = BookFont,
                modifier = Modifier.graphicsLayer {
                    val grow = beat.value * (1f + reveal * 2f)
                    scaleX = grow
                    scaleY = grow
                    alpha = 1f - reveal / 0.5f
                },
            )
        }
    }
}

@Composable
internal fun PageButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) GoldLeaf else GoldLeaf.copy(alpha = 0.3f), fontSize = 18.sp)
    }
}

@StringRes
private fun categoryLabel(category: CollectionCategory): Int = when (category) {
    CollectionCategory.CROPS -> R.string.collection_tab_crops
    CollectionCategory.TERRAIN -> R.string.collection_tab_terrain
    CollectionCategory.STRUCTURES -> R.string.collection_tab_structures
}

/** The name of a colour a crop blooms in ([PlantSpecies.bloomColours]). */
@StringRes
internal fun bloomColourLabel(colour: String): Int = when (colour) {
    "red" -> R.string.bloom_colour_red
    "pink" -> R.string.bloom_colour_pink
    "yellow" -> R.string.bloom_colour_yellow
    "white" -> R.string.bloom_colour_white
    "purple" -> R.string.bloom_colour_purple
    "blue" -> R.string.bloom_colour_blue
    else -> R.string.bloom_colour_other
}

@StringRes
internal fun flavorText(entry: CollectionEntry): Int = when (entry) {
    CollectionEntry.TULIP -> R.string.flavor_tulip
    CollectionEntry.SUNFLOWER -> R.string.flavor_sunflower
    CollectionEntry.ROSE -> R.string.flavor_rose
    CollectionEntry.COSMOS -> R.string.flavor_cosmos
    CollectionEntry.SNOWDROP -> R.string.flavor_snowdrop
    CollectionEntry.MINT -> R.string.flavor_mint
    CollectionEntry.TOMATO -> R.string.flavor_tomato
    CollectionEntry.PAPRIKA -> R.string.flavor_paprika
    CollectionEntry.DAIKON -> R.string.flavor_daikon
    CollectionEntry.CARROT -> R.string.flavor_carrot
    CollectionEntry.EGGPLANT -> R.string.flavor_eggplant
    CollectionEntry.WHEAT -> R.string.flavor_wheat
    CollectionEntry.CORN -> R.string.flavor_corn
    CollectionEntry.STRAWBERRY -> R.string.flavor_strawberry
    CollectionEntry.WATERMELON -> R.string.flavor_watermelon
    CollectionEntry.LAVENDER -> R.string.flavor_lavender
    CollectionEntry.SHIITAKE -> R.string.flavor_shiitake
    CollectionEntry.POINSETTIA -> R.string.flavor_poinsettia
    CollectionEntry.GREENHOUSE -> R.string.flavor_greenhouse
    CollectionEntry.GRASS -> R.string.flavor_grass
    CollectionEntry.PEBBLE -> R.string.flavor_pebble
    CollectionEntry.ROCK -> R.string.flavor_rock
    CollectionEntry.RIVER -> R.string.flavor_river
    CollectionEntry.PATH -> R.string.flavor_path
    CollectionEntry.FLOWERS -> R.string.flavor_flowers
    CollectionEntry.DECK -> R.string.flavor_deck
    CollectionEntry.POND -> R.string.flavor_pond
    CollectionEntry.BENCH -> R.string.flavor_bench
    CollectionEntry.LANTERN -> R.string.flavor_lantern
    CollectionEntry.SPRINKLER -> R.string.flavor_sprinkler
    CollectionEntry.FENCE -> R.string.flavor_fence
    CollectionEntry.WELL -> R.string.flavor_well
    CollectionEntry.HUT -> R.string.flavor_hut
    CollectionEntry.WINDMILL -> R.string.flavor_windmill
    CollectionEntry.COTTAGE -> R.string.flavor_cottage
}

/** The question mark's size when its page is not waiting to be filled in. */
private val STILL_BEAT: State<Float> = mutableFloatStateOf(1f)
