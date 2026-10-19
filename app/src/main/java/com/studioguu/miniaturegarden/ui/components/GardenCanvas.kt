package com.studioguu.miniaturegarden.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.studioguu.miniaturegarden.data.asset.Decoration
import com.studioguu.miniaturegarden.data.asset.GroundCover
import com.studioguu.miniaturegarden.data.asset.GroundTile
import com.studioguu.miniaturegarden.data.asset.GroundSlab
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TileType
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.render.Daylight
import com.studioguu.miniaturegarden.ui.render.FenceAutotile
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import com.studioguu.miniaturegarden.ui.render.drawGreenhouseGlass
import com.studioguu.miniaturegarden.ui.render.drawCropInCode
import com.studioguu.miniaturegarden.ui.render.drawTurningSprite
import com.studioguu.miniaturegarden.ui.render.WindmillSails
import com.studioguu.miniaturegarden.ui.render.GustAxis
import com.studioguu.miniaturegarden.ui.render.GustFront
import com.studioguu.miniaturegarden.ui.render.IsometricMetrics
import com.studioguu.miniaturegarden.ui.render.IsometricTransform
import com.studioguu.miniaturegarden.ui.render.LayerPipeline
import com.studioguu.miniaturegarden.ui.render.RenderLayer
import com.studioguu.miniaturegarden.ui.render.FeatheredLawn
import com.studioguu.miniaturegarden.ui.render.RiverAutotile
import com.studioguu.miniaturegarden.ui.render.SoilTexture
import com.studioguu.miniaturegarden.ui.render.SunLight
import com.studioguu.miniaturegarden.ui.render.TileCoord
import com.studioguu.miniaturegarden.ui.render.TapTargets
import androidx.compose.ui.graphics.drawscope.clipPath
import com.studioguu.miniaturegarden.ui.render.LawnShade
import com.studioguu.miniaturegarden.ui.render.SoilJoins
import com.studioguu.miniaturegarden.ui.render.plantScale
import com.studioguu.miniaturegarden.ui.render.PondWater
import com.studioguu.miniaturegarden.ui.render.SprinklerSpray
import com.studioguu.miniaturegarden.ui.render.drawFlatGround
import com.studioguu.miniaturegarden.ui.render.drawGroundSlab
import com.studioguu.miniaturegarden.ui.render.Wind
import com.studioguu.miniaturegarden.ui.render.drawVisitor
import com.studioguu.miniaturegarden.ui.render.drawFenceArmShadows
import com.studioguu.miniaturegarden.ui.render.drawFenceArms
import com.studioguu.miniaturegarden.ui.render.drawGroundTexture
import com.studioguu.miniaturegarden.ui.render.drawRiverTile
import com.studioguu.miniaturegarden.ui.render.drawSprite
import com.studioguu.miniaturegarden.ui.render.drawSpriteShadow
import com.studioguu.miniaturegarden.ui.render.drawSwayingSprite
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TILE_WIDTH = 128.dp
private val TILE_HEIGHT = 64.dp
private val HEIGHT_STEP = 16.dp

/** The least reach round a bubble for a tap, about half a fingertip, however far the garden is zoomed out. */
private val BUBBLE_MIN_REACH = 22.dp

/** Stores a camera as [scale, pan.x, pan.y]; an empty list stands for "not set yet". */
private val CameraSaver = Saver<GardenCamera?, List<Float>>(
    save = { camera -> camera?.let { listOf(it.scale, it.pan.x, it.pan.y) } ?: emptyList() },
    restore = { saved -> if (saved.size == 3) GardenCamera(saved[0], Offset(saved[1], saved[2])) else null },
)

/** Object ids with this prefix are flat ground cover and render below plants and decorations. */
const val GROUND_OBJECT_ID_PREFIX = "ground_"

/**
 * The isometric garden view: pinch to zoom, drag to pan, tap to pick a tile.
 *
 * @param atlas sprites to draw with; anything missing from it falls back to a placeholder shape.
 * @param terrain ground textures for river and paving; plain shapes are drawn without them.
 * @param gardenMap the terrain under the objects.
 * @param weather decides how hard the wind blows and how strong the shadows are.
 * @param auraCells cells to mark softly on the ground: the reach of the selected building's effect.
 * @param hintCells cells the first steps point at: a few small stars twinkle quietly over each.
 * @param visitors creatures staying in the garden; each fades in and out on its own clock.
 * @param fadedObjectIds buildings to draw see-through, so that what is behind them shows (see
 *   [com.studioguu.miniaturegarden.ui.render.Occlusion]). They fade softly in and out.
 * @param onTileTapped receives the tapped tile, or null for a tap outside the grid. A tap on a
 *   bubble, or on a building's walls over bare ground, stands for that thing's tile instead
 *   ([TapTargets]).
 * @param savedView the view to open with, once known; until then the garden shows at its starting
 *   zoom ([GardenCamera.starting]), tiles about finger-sized.
 * @param onViewChanged told the view a moment after the player has zoomed or panned, to be kept.
 * @param paused holds the wind and the river still, while something covers the garden.
 * @param season decides how the lawn looks: petals, deep green, fallen leaves or snow.
 * @param initialZoom 1 for the garden itself, which starts at [GardenCamera.starting]; anything
 *   else shows it that much closer than "all of it on screen", for a garden meant to fill the
 *   view (the title's sample garden).
 * @param editing something is being placed or moved, or the hoe is in hand: taps are read on
 *   the ground only, since a cell is being looked for.
 * @param growableId the symbol building the garden can grow from now, if any: a bubble over it
 *   says so, and tapping the bubble picks it.
 * @param onZoomChanged told the zoom at every change, as it happens (the debug read-out).
 * @param bareGround what a cell with nothing laid on it looks like: the untilled wasteland of a
 *   garden being made, or the lawn of the title's garden in full swing.
 */
@Composable
fun GardenCanvas(
    gridObjects: List<GridObject>,
    selectedTile: TileCoord?,
    onTileTapped: (TileCoord?) -> Unit,
    modifier: Modifier = Modifier,
    atlas: SpriteAtlas = SpriteAtlas.EMPTY,
    terrain: TerrainTextures = TerrainTextures.EMPTY,
    gardenMap: GardenMap = GardenMap.allGrass(),
    showGrid: Boolean = true,
    weather: WeatherType = WeatherType.SUNNY,
    auraCells: Set<GridCell> = emptySet(),
    hintCells: Set<GridCell> = emptySet(),
    fadedObjectIds: Set<String> = emptySet(),
    visitors: List<CreatureVisit> = emptyList(),
    savedView: GardenView? = null,
    onViewChanged: ((GardenView) -> Unit)? = null,
    paused: Boolean = false,
    initialZoom: Float = 1f,
    season: Season = Season.SPRING,
    editing: Boolean = false,
    growableId: String? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    bareGround: BareGround = BareGround.WASTELAND,
) {
    val density = LocalDensity.current
    // As large as the garden is: it can grow.
    val metrics = remember(density, gardenMap.size) {
        with(density) {
            IsometricMetrics(TILE_WIDTH.toPx(), TILE_HEIGHT.toPx(), HEIGHT_STEP.toPx(), gardenMap.size)
        }
    }
    // Null until the first layout pass tells us how far to zoom out to fit the grid. Saved, so a
    // rotation or a return from the background comes back to the same view.
    var camera by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<GardenCamera?>(null) }
    // When the garden grows (7 x 7 to 9 x 9, and on), the view goes back to the starting zoom,
    // centred, rather than keeping whatever zoom it had before: the same as a new garden.
    var viewport by remember { mutableStateOf(Size.Zero) }
    var fittedGridSize by rememberSaveable { mutableStateOf(metrics.gridSize) }
    // Whether the view has been changed here (by the fingers, or by the garden growing), which
    // is when it is worth keeping; the first fitting is not.
    var viewMoved by remember { mutableStateOf(false) }
    LaunchedEffect(metrics, viewport) {
        if (fittedGridSize != metrics.gridSize && viewport != Size.Zero) {
            camera = GardenCamera.starting(viewport, metrics)
            fittedGridSize = metrics.gridSize
            viewMoved = true
        }
    }
    // The view the player left the garden with, taken up once when it is known (it is read from
    // storage, so it may come a moment after the first layout).
    var viewRestored by remember { mutableStateOf(false) }
    LaunchedEffect(savedView, viewport, metrics) {
        if (!viewRestored && savedView != null && viewport != Size.Zero) {
            camera = GardenCamera.restored(savedView.scale, Offset(savedView.panX, savedView.panY), viewport, metrics)
            viewRestored = true
        }
    }
    // And the view as it is now is handed back once the fingers have rested a moment, not at
    // every step of a pinch.
    val currentOnViewChanged by rememberUpdatedState(onViewChanged)
    LaunchedEffect(camera) {
        val settled = camera ?: return@LaunchedEffect
        val report = currentOnViewChanged ?: return@LaunchedEffect
        if (!viewMoved) return@LaunchedEffect
        delay(VIEW_SAVE_DELAY_MILLIS)
        report(GardenView(settled.scale, settled.pan.x, settled.pan.y))
    }
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    LaunchedEffect(camera?.scale) {
        camera?.let { currentOnZoomChanged?.invoke(it.scale) }
    }
    // Buildings fade to see-through and back; the last set faded is kept while fading back.
    val fade by animateFloatAsState(if (fadedObjectIds.isEmpty()) 0f else 1f, tween(FADE_MILLIS), label = "fade")
    var fadingIds by remember { mutableStateOf(emptySet<String>()) }
    if (fadedObjectIds.isNotEmpty()) fadingIds = fadedObjectIds
    val fadeAlpha = 1f - (1f - FADED_ALPHA) * fade
    val atlasImage = remember(atlas) { atlas.bitmap?.asImageBitmap() }
    val lawn = remember(atlas) { FeatheredLawn.from(atlas, GroundTile.GRASS.assetId) }
    val pipeline = remember { LayerPipeline() }
    val waterTick by rememberWaterTick(running = !paused && gridObjects.any { it.isTerrain })
    val grid = remember(gardenMap, gridObjects) { GardenGrid(gardenMap, gridObjects) }
    val currentOnTileTapped by rememberUpdatedState(onTileTapped)
    val currentGrid by rememberUpdatedState(grid)
    val currentObjects by rememberUpdatedState(gridObjects)
    val currentEditing by rememberUpdatedState(editing)
    val currentGrowableId by rememberUpdatedState(growableId)
    // The wind's clock. It is read while drawing only, so a new value redraws the garden without
    // recomposing anything. A breeze is slow: a modest frame rate is plenty and saves battery.
    // Held still while something covers the garden, so that a sheet sliding up over it has the
    // frames to itself.
    val windMillis by produceState(0L, paused) {
        while (!paused) {
            withFrameMillis { value = it }
            delay(WIND_FRAME_MILLIS)
        }
    }
    // The time of day, for the direction of the light; looked at again every minute.
    val hourOfDay by produceState(currentHourOfDay()) {
        while (true) {
            delay(60_000)
            value = currentHourOfDay()
        }
    }

    // Whatever was in the garden a moment ago and is not any more (removed, harvested, put back
    // into the bag) is kept as a ghost for a moment and drawn fading away, instead of blinking out.
    val ghosts = remember { mutableStateListOf<Ghost>() }
    // And where a building has just been unveiled, its wraps burst apart.
    val bursts = remember { mutableStateListOf<Ghost>() }
    val lastSeen = remember { arrayOf(gridObjects) }
    val ghostScope = rememberCoroutineScope()
    LaunchedEffect(gridObjects) {
        val present = gridObjects.mapTo(HashSet()) { it.instanceId }
        val gone = lastSeen[0].filter { it.instanceId !in present }
        val wasUnderWraps = lastSeen[0].filter { it.workKind == WorkKind.READY || it.workKind == WorkKind.BUILDING }
            .mapTo(HashSet()) { it.instanceId }
        val built = gridObjects.filter { it.instanceId in wasUnderWraps && !it.isWorking }
        lastSeen[0] = gridObjects
        for (gridObject in built) {
            val burst = Ghost(gridObject)
            bursts += burst
            ghostScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    burst.progress.animateTo(1f, tween(BURST_MILLIS))
                } finally {
                    bursts -= burst
                }
            }
        }
        for (gridObject in gone) {
            val ghost = Ghost(gridObject)
            ghosts += ghost
            // In a scope of their own rather than this effect's, which is cancelled at the next
            // change to the garden and would cut the fading short.
            ghostScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    ghost.progress.animateTo(1f, tween(VANISH_MILLIS))
                } finally {
                    ghosts -= ghost
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size ->
                viewport = size.toSize()
                // A camera brought back from saved state does not know how far out this screen
                // lets it zoom; that is worked out afresh.
                camera = camera?.let { GardenCamera.restored(it.scale, it.pan, size.toSize(), metrics) }
                    ?: GardenCamera.fitting(size.toSize(), metrics).let { fit ->
                        if (initialZoom == 1f) GardenCamera.starting(size.toSize(), metrics) else GardenCamera.restored(fit.scale * initialZoom, Offset.Zero, size.toSize(), metrics)
                    }
            }
            .pointerInput(metrics) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera = camera?.transformedBy(centroid, pan, zoom, size.toSize(), metrics)
                    viewMoved = true
                }
            }
            .pointerInput(metrics) {
                detectTapGestures { position ->
                    val transform = camera?.toTransform(metrics, size.toSize()) ?: return@detectTapGestures
                    // Read on the ground, unless the finger is on a bubble or a building (see TapTargets).
                    val tile = TapTargets.resolve(
                        position, transform, currentGrid, currentObjects, currentEditing, minReach = BUBBLE_MIN_REACH.toPx(),
                        growableId = currentGrowableId,
                    )
                    currentOnTileTapped(tile)
                }
            }
    ) {
        val transform = camera?.toTransform(metrics, size) ?: return@Canvas
        val spriteScale = transform.tileWidth / atlas.manifest.tileWidth
        val ambience = Ambience(Daylight.at(hourOfDay, weather), weather, windMillis, season = season)

        pipeline.submit(RenderLayer.BASE_TERRAIN) {
            val wasteland = if (bareGround == BareGround.WASTELAND) terrain.wasteland else null
            drawTerrain(transform, gardenMap, framed = true, ambience, lawn, wasteland) { tile, center, flip ->
                val sprite = atlas[tile.assetId]
                if (sprite != null && atlasImage != null) drawSprite(atlasImage, sprite, center, spriteScale * groundOverlap(tile), flipped = flip)
                sprite != null
            }
        }
        // Last of the ground, over the stream and the beds at the garden's edge too.
        pipeline.submit(RenderLayer.BASE_TERRAIN, Float.MAX_VALUE) { drawFrameLip(transform, gardenMap.size.toFloat()) }
        if (showGrid) {
            pipeline.submit(RenderLayer.GRID_OVERLAY) { drawIsometricGrid(transform, metrics.gridSize) }
        }
        if (auraCells.isNotEmpty()) {
            pipeline.submit(RenderLayer.GRID_OVERLAY, depth = 0.5f) {
                for (cell in auraCells) {
                    if (!gardenMap.isInside(cell.x, cell.y)) continue
                    val tile = transform.tilePath(cell.x, cell.y)
                    drawPath(tile, AURA_FILL)
                    drawPath(tile, AURA_EDGE, style = Stroke(width = transform.tileWidth * 0.014f))
                }
            }
        }
        if (selectedTile != null) {
            // Selecting any cell of a structure marks its whole footprint.
            val selected = grid.objectAt(selectedTile.x, selectedTile.y)
            val origin = selected?.let { TileCoord(it.gridX, it.gridY) } ?: selectedTile
            val spanX = selected?.footprint?.width ?: 1
            val spanY = selected?.footprint?.height ?: 1
            pipeline.submit(RenderLayer.GRID_OVERLAY, depth = 1f) {
                drawTileHighlight(transform, origin, spanX, spanY)
            }
            pipeline.submit(RenderLayer.HUD_OVERLAY) { drawTileOutline(transform, origin, spanX, spanY) }
        }
        val pops = if (bursts.isEmpty()) emptyMap() else bursts.associate { it.gridObject.instanceId to it.progress.value }
        val fades = if (fadeAlpha >= 1f) emptyMap() else fadingIds.associateWith { fadeAlpha }
        submitObjects(pipeline, gridObjects, grid, transform, atlas, atlasImage, terrain, waterTick, ambience, pops = pops, fades = fades)
        // The garden can grow: a bubble over the building it is grown from.
        gridObjects.firstOrNull { it.instanceId == growableId }?.let { building ->
            pipeline.submit(RenderLayer.TOP_EFFECTS) {
                drawGrowBubble(TapTargets.growBubbleTip(building, transform), transform.tileWidth, ambience.windMillis ?: 0L)
            }
        }
        for (ghost in ghosts) {
            submitObjects(
                pipeline, listOf(ghost.gridObject), grid, transform, atlas, atlasImage, terrain, waterTick, ambience,
                vanish = ghost.progress.value,
            )
        }
        for (visit in visitors) {
            val opacity = visit.opacityAt(ambience.epochMillis)
            if (opacity <= 0f) continue
            pipeline.submit(RenderLayer.GRID_OBJECTS, depth = LayerPipeline.depthOf(visit.cell.x, visit.cell.y, 0) + 0.6f) {
                val base = visitorBase(transform, visit)
                val draw: DrawScope.() -> Unit = { drawVisitor(visit.creature, base, transform.tileWidth, windMillis, atlas, atlasImage) }
                if (opacity < 1f) drawFaded(opacity, draw) else draw()
            }
        }
        for (cell in hintCells) {
            if (!gardenMap.isInside(cell.x, cell.y)) continue
            pipeline.submit(RenderLayer.TOP_EFFECTS, depth = LayerPipeline.depthOf(cell.x, cell.y, 0) + 0.5f) {
                drawHintTwinkle(transform, cell, windMillis)
            }
        }
        val gust = Wind.gustAt(windMillis, weather)
        if (gust != null) {
            pipeline.submit(RenderLayer.TOP_EFFECTS, depth = Float.MAX_VALUE - 1f) {
                drawGustStreaks(transform, metrics.gridSize, gust)
            }
        }
        for (burst in bursts) {
            val progress = burst.progress.value
            pipeline.submit(RenderLayer.TOP_EFFECTS, depth = Float.MAX_VALUE) {
                drawCompletionBurst(transform, burst.gridObject, progress)
            }
        }
        pipeline.render(this)
    }
}

private val AURA_FILL = Color(0x334FC3F7)
private val AURA_EDGE = Color(0x994FC3F7)

/** How long the garden waits between two frames of wind. */
private const val WIND_FRAME_MILLIS = 50L

/** How long the view must rest before it is handed back to be kept. */
private const val VIEW_SAVE_DELAY_MILLIS = 600L

/** Local time as hours since midnight, minutes included as a fraction. */
private fun currentHourOfDay(): Float = LocalTime.now().let { it.hour + it.minute / 60f }

/**
 * The light and the air a scene is drawn in.
 *
 * @property windMillis the wind's clock, or null for still air (the pictures in the encyclopedia).
 */
private class Ambience(
    val light: SunLight,
    val weather: WeatherType,
    val windMillis: Long?,
    /** Wall clock, for how far along building jobs are. */
    val epochMillis: Long = System.currentTimeMillis(),
    /** The garden's season, which the lawn shows. */
    val season: Season = Season.SPRING,
) {
    /** How far something at this place in the grid leans right now, see [Wind.sway]. */
    fun sway(gridX: Float, gridY: Float): Float =
        if (windMillis == null) 0f else Wind.sway(windMillis, gridX, gridY, weather)

    companion object {
        /** A still, fine afternoon. */
        val CALM = Ambience(Daylight.at(15f, WeatherType.SUNNY), WeatherType.SUNNY, windMillis = null)
    }
}

/**
 * A counter that steps while there is water on show, a few times a second, and stands still
 * otherwise. River tiles take their animation frame from it, so every tile moves in step.
 */
@Composable
private fun rememberWaterTick(running: Boolean): State<Int> = produceState(0, running) {
    while (running) {
        delay(RiverAutotile.FRAME_MILLIS)
        value = (value + 1) % WATER_TICK_CYCLE
    }
}

/**
 * Queues everything lying or standing on the garden: river and paving with the ground, the rest
 * in the object layers, sorted by depth.
 *
 * @param waterTick animation counter for the river, see [rememberWaterTick].
 * @param pops for things that were unveiled a moment ago, by instance id: how far along the
 *   unveiling is, 0 to 1.
 */
private fun submitObjects(
    pipeline: LayerPipeline,
    gridObjects: List<GridObject>,
    grid: GardenGrid,
    transform: IsometricTransform,
    atlas: SpriteAtlas,
    atlasImage: ImageBitmap?,
    terrain: TerrainTextures,
    waterTick: Int,
    ambience: Ambience,
    vanish: Float? = null,
    pops: Map<String, Float> = emptyMap(),
    fades: Map<String, Float> = emptyMap(),
) {
    val spriteScale = transform.tileWidth / atlas.manifest.tileWidth
    // Everything is queued through here, so that objects on their way out can be drawn fading.
    fun submit(obj: GridObject, layer: RenderLayer, depth: Float, plainDraw: DrawScope.() -> Unit) {
        val pop = pops[obj.instanceId]
        // See-through, so that what stands behind it shows. Only the thing itself: its shadow
        // stays on the ground as it is.
        val alpha = fades[obj.instanceId]
        val draw: DrawScope.() -> Unit =
            if (alpha != null && layer == RenderLayer.GRID_OBJECTS) ({ drawFaded(alpha, plainDraw) }) else plainDraw
        if (obj.isWorking) {
            // A work site: sheeting and scaffolding, and nothing of what is inside. Its shadow
            // and anything else the finished thing would draw are left out with it. Taken away
            // (put back in the bag, called off), it is the site that fades, never the finished
            // building (#50: that showed for a moment).
            if (layer == RenderLayer.GRID_OBJECTS) {
                val progress = obj.workProgress(ambience.epochMillis)
                val site: DrawScope.() -> Unit = { drawWorkSite(transform, obj, progress, ambience.windMillis ?: 0L) }
                if (vanish != null) {
                    val footprint = obj.footprint
                    val pivot = transform.worldToScreen(obj.gridX + footprint.width / 2f, obj.gridY + footprint.height / 2f)
                    pipeline.submit(layer, depth) { drawVanishing(pivot, vanish, transform.tileHeight, site) }
                } else {
                    pipeline.submit(layer, depth) { if (alpha != null) drawFaded(alpha, site) else site() }
                }
            }
        } else if (vanish == null && pop != null && layer == RenderLayer.GRID_OBJECTS) {
            // Just unveiled: it pops up out of the ground, a little too big, and settles.
            val footprint = obj.footprint
            val pivot = transform.worldToScreen(obj.gridX + footprint.width / 2f, obj.gridY + footprint.height / 2f)
            pipeline.submit(layer, depth) { scale(popScale(pop), pivot) { draw() } }
        } else if (vanish == null) {
            pipeline.submit(layer, depth, draw)
        } else {
            val footprint = obj.footprint
            val pivot = transform.worldToScreen(obj.gridX + footprint.width / 2f, obj.gridY + footprint.height / 2f)
            pipeline.submit(layer, depth) { drawVanishing(pivot, vanish, transform.tileHeight, draw) }
        }
    }
    for (obj in gridObjects) {
        if (obj.isTerrain) {
            // River: part of the ground, drawn with the terrain right after the lawn. Its
            // picture depends on which neighbours are river too, so the tiles join up.
            val isRiver = { x: Int, y: Int -> grid.occupantAt(x, y)?.isTerrain == true }
            val autotile = terrain.riverAutotile
            submit(obj, RenderLayer.BASE_TERRAIN, 1f) {
                if (autotile != null) {
                    val mask = RiverAutotile.maskAt(obj.gridX, obj.gridY, isRiver)
                    val frame = RiverAutotile.frameFor(waterTick, obj.gridX, obj.gridY)
                    drawRiverTile(autotile, mask, frame, transform, obj.gridX, obj.gridY)
                } else {
                    drawWaterTile(transform, obj.gridX, obj.gridY, waterTick % WATER_PHASES, isRiver)
                }
            }
            continue
        }
        if (obj.isFloor) {
            // Paving and tilled soil: also ground. Whatever stands on the cell is a separate
            // object and is drawn later, in the object layer, so it always appears on top.
            val paving = terrain.cobblestone
            val soil = atlas[GridObject.TILLED_SOIL_OBJECT_ID]
            // Back to front within the ground: a bed of soil stands a little proud of the lawn,
            // and its front edge has to come over the bed behind it.
            submit(obj, RenderLayer.BASE_TERRAIN, 1f + LayerPipeline.depthOf(obj.gridX, obj.gridY)) {
                if (obj.isTilledSoil) {
                    // Beds side by side are one field: each knows which of its sides carry on (#47).
                    val joins = SoilJoins.at(obj.gridX, obj.gridY) { bx, by -> grid.floorAt(bx, by)?.isTilledSoil == true }
                    drawTilledSoil(transform, obj.gridX, obj.gridY, composted = obj.isComposted, joins = joins)
                } else if (obj.isGreenhouse && soil != null && atlasImage != null) {
                    // Compost darkens the earth.
                    val center = transform.tileCenter(obj.gridX, obj.gridY)
                    drawSprite(atlasImage, soil, center, spriteScale, tint = if (obj.isComposted) COMPOST_TINT else null)
                } else if (obj.isGreenhouse) {
                    drawTilledSoil(transform, obj.gridX, obj.gridY, composted = obj.isComposted)
                } else if (obj.objectId == GridObject.WOOD_DECK_OBJECT_ID) {
                    drawWoodDeck(transform, obj.gridX, obj.gridY)
                } else if (paving != null) {
                    drawGroundTexture(paving, transform, obj.gridX, obj.gridY)
                } else {
                    drawPath(transform.tilePath(obj.gridX, obj.gridY), PAVING_FALLBACK)
                }
            }
            // The glass house of a greenhouse bed goes over whatever grows in it.
            if (obj.isGreenhouse) {
                val house = atlas[GridObject.GREENHOUSE_OBJECT_ID]
                // With a crop inside, the house is drawn see-through, so that the crop shows.
                val growing = grid.occupantAt(obj.gridX, obj.gridY)?.isPlant == true
                submit(obj, RenderLayer.GRID_OBJECTS, LayerPipeline.depthOf(obj.gridX, obj.gridY, 0) + 0.5f) {
                    if (house != null && atlasImage != null) {
                        val center = transform.tileCenter(obj.gridX, obj.gridY)
                        drawSprite(atlasImage, house, center, spriteScale, flipped = obj.isFlipped, alpha = if (growing) GREENHOUSE_OVER_CROP_ALPHA else 1f)
                    } else {
                        drawGreenhouseGlass(transform, obj.gridX, obj.gridY)
                    }
                }
            }
            continue
        }
        val footprint = obj.footprint
        val structure = StructureData.fromObjectId(obj.objectId)
        // Sorted by its front-most cell, so things standing behind a wide object stay behind it.
        val depth = LayerPipeline.depthOf(
            obj.gridX + footprint.width - 1,
            obj.gridY + footprint.height - 1,
            obj.heightLevel,
        )
        // Ground point under the middle of the footprint: the tile center for a 1x1 object.
        val base = transform.worldToScreen(
            obj.gridX + footprint.width / 2f,
            obj.gridY + footprint.height / 2f,
            obj.heightLevel.toFloat(),
        )
        val unit = transform.tileWidth
        val isGroundCover = obj.objectId.startsWith(GROUND_OBJECT_ID_PREFIX)
        // Null while the atlas has no sprite for this object; the placeholder is drawn instead.
        val sprite = atlas[TileSetDefinition.assetIdFor(obj)]

        val light = ambience.light
        val sway = ambience.sway(obj.gridX + 0.5f, obj.gridY + 0.5f)
        if (obj.isPond) {
            // Low, lying in the ground: with the ground cover, under whatever stands round it.
            val pond = terrain.pond
            // Its water moves on the garden's clock: glints and spreading rings (PondWater).
            val water = PondWater.frameAt(ambience.windMillis ?: 0L, seed = obj.gridX * 31 + obj.gridY * 17)
            submit(obj, RenderLayer.BOTTOM_OBJECTS, depth) {
                if (pond != null) {
                    drawGroundSlab(pond, GroundSlab.POND, transform.tileCenter(obj.gridX, obj.gridY), unit, coverage = POND_COVERAGE)
                } else {
                    drawPondInCode(transform, obj.gridX, obj.gridY)
                }
                drawPondWater(transform, obj.gridX, obj.gridY, water)
            }
            continue
        }
        if (obj.isFence) {
            // How a fence looks is read off its neighbours every time it is drawn: alone it is a
            // short section; next to other fences it is a post with rails reaching out to them.
            val mask = FenceAutotile.maskAt(obj.gridX, obj.gridY) { x, y -> grid.occupantAt(x, y)?.isFence == true }
            val section = atlas[FencePart.SECTION.objectId]
            val post = atlas[FencePart.POST.objectId]
            val alone = mask == 0 && section != null && atlasImage != null
            val postHeight = if (post != null) post.height * post.anchorY * spriteScale else unit * 0.2f
            submit(obj, RenderLayer.SHADOWS, depth) {
                if (alone) {
                    drawContactShadow(base, min(section!!.width * spriteScale * 0.62f, unit * 0.7f))
                    drawSpriteShadow(atlasImage!!, section, base, spriteScale, light)
                } else {
                    drawContactShadow(base, unit * 0.22f)
                    drawFenceArmShadows(transform, obj.gridX, obj.gridY, mask, postHeight, light)
                    if (post != null && atlasImage != null) drawSpriteShadow(atlasImage, post, base, spriteScale, light)
                }
            }
            submit(obj, RenderLayer.GRID_OBJECTS, depth) {
                if (alone) {
                    drawSprite(atlasImage!!, section!!, base, spriteScale)
                } else {
                    drawFenceArms(transform, obj.gridX, obj.gridY, mask, behind = true, postHeight)
                    if (post != null && atlasImage != null) {
                        drawSprite(atlasImage, post, base, spriteScale)
                    } else {
                        drawPlaceholder(obj, base, unit)
                    }
                    drawFenceArms(transform, obj.gridX, obj.gridY, mask, behind = false, postHeight)
                }
                if (grid.floorAt(obj.gridX, obj.gridY) == null) {
                    drawGrassAtFoot(base, unit, unit * (if (alone) 0.28f else 0.1f), sway, seed = obj.gridX * 31 + obj.gridY * 17)
                }
            }
            continue
        }
        // Two shadows tie a thing to the ground: a dark, tight one right under its foot, which
        // is there in any light, and the one the sun throws, which turns with the time of day.
        if (!isGroundCover) {
            submit(obj, RenderLayer.SHADOWS, depth) {
                if (sprite != null && atlasImage != null) {
                    drawContactShadow(base, min(sprite.drawnWidth(spriteScale) * 0.62f, unit * 0.7f))
                    drawSpriteShadow(atlasImage, sprite, base, spriteScale, light)
                } else if (structure != null) {
                    drawStructureShadow(transform, obj, structure, light)
                } else {
                    val scale = if (obj.isPlant) plantScale(obj) else 1f
                    drawContactShadow(base, unit * 0.4f * scale)
                    drawBlobShadow(base, unit * 0.36f * scale, unit * (if (obj.isPlant) 0.55f else 0.2f) * scale, light)
                }
            }
        }
        submit(obj, if (isGroundCover) RenderLayer.BOTTOM_OBJECTS else RenderLayer.GRID_OBJECTS, depth) {
            if (sprite != null && atlasImage != null) {
                // Plants lean with the wind; a withered one is stiff and barely moves.
                val lean = when {
                    !obj.isPlant -> 0f
                    obj.status == PlantStatus.WITHERED -> sway * PLANT_LEAN * 0.3f
                    else -> sway * PLANT_LEAN
                }
                drawSwayingSprite(atlasImage, sprite, base, spriteScale, lean, flipped = obj.isFlipped)
                if (obj.objectId == WindmillSails.MILL_OBJECT_ID) {
                    val sails = atlas[WindmillSails.SAILS_ASSET_ID]
                    val hub = sprite.attachmentAt(base.x, base.y, spriteScale, obj.isFlipped)
                    if (sails != null && hub != null) {
                        // Mirrored with the mill, the sails turn the other way, as seen from its other side.
                        val angle = WindmillSails.angleAt(ambience.windMillis, ambience.weather, obj.gridX, obj.gridY)
                        drawTurningSprite(
                            atlasImage, sails, Offset(hub.first, hub.second), spriteScale,
                            if (obj.isFlipped) -angle else angle, flipped = obj.isFlipped,
                        )
                    }
                }
            } else if (structure != null) {
                drawStructure(transform, obj, structure)
            } else {
                drawPlaceholder(obj, base, unit)
            }
            // What it stands in comes up around its foot: blades of grass on open ground, crumbs
            // of earth on a bed. Paving is hard and shows nothing.
            if (!isGroundCover && structure == null) {
                val floor = grid.floorAt(obj.gridX, obj.gridY)
                val reach = if (sprite != null) min(sprite.drawnWidth(spriteScale) * 0.3f, unit * 0.3f) else unit * 0.16f
                when {
                    floor == null -> drawGrassAtFoot(base, unit, reach, sway, seed = obj.gridX * 31 + obj.gridY * 17)
                    floor.isTilledSoil || floor.isGreenhouse -> drawEarthAtFoot(base, unit, reach, seed = obj.gridX * 31 + obj.gridY * 17)
                }
            }
        }
        if (obj.isPlant && (obj.status == PlantStatus.THIRSTY || obj.status == PlantStatus.UNNOURISHED)) {
            submit(obj, RenderLayer.TOP_EFFECTS, depth) { drawStatusBubble(obj, base, unit) }
        }
        // A running sprinkler throws its water over the beds round it, on the garden's clock.
        if (obj.isSprinkler && obj.isOn && !obj.isWorking) {
            val frame = SprinklerSpray.frameAt(ambience.windMillis ?: 0L, running = true)
            val sprayArt = atlas[SprinklerSpray.spriteIdFor(frame.index)]
            submit(obj, RenderLayer.TOP_EFFECTS, depth) {
                if (sprayArt != null && atlasImage != null) {
                    drawSprite(atlasImage, sprayArt, base, spriteScale)
                } else {
                    drawSprinklerSpray(base, unit, frame)
                }
            }
        }
    }
}

/** How the earth of a composted bed is darkened (multiplied in). */
private val COMPOST_TINT = Color(0xFFB8A48E)

/** How opaque a greenhouse's picture is while a crop grows inside, so that the crop shows through. */
private const val GREENHOUSE_OVER_CROP_ALPHA = 0.55f

/** How long the unveiling of a finished building lasts. */
private const val BURST_MILLIS = 1100

// --- Work sites --------------------------------------------------------------------------------------

private val SCAFFOLD_POLE = Color(0xFFD9B36C)
private val SCAFFOLD_PLANK = Color(0xFFC2924F)
private val SCAFFOLD_LINE = Color(0xFF5D3A1A)
private val SCAFFOLD_ROPE = Color(0xFFF3E3B8)
private val PROGRESS_TRACK = Color(0xCC3E2723)
private val PROGRESS_BUILD = Color(0xFF8BC34A)
private val PROGRESS_TAKE_DOWN = Color(0xFFFFB74D)
private val DUST = Color(0xFFE8D9B5)
private val SPARKLE = Color(0xFFFFF3B0)

/** The corners of a work site on the ground and how tall it is; shared by the site and its burst. */
private class SiteBox(transform: IsometricTransform, obj: GridObject) {
    private val footprint = obj.footprint
    // A fence fills little of its tile; its site is drawn in closer.
    private val inset = if (StructureData.fromObjectId(obj.objectId) == null) 0.2f else 0.03f
    private val x0 = obj.gridX + inset
    private val y0 = obj.gridY + inset
    private val x1 = obj.gridX + footprint.width - inset
    private val y1 = obj.gridY + footprint.height - inset
    val height = TapTargets.workSiteHeight(obj, transform)
    val up = Offset(0f, -height)
    val back = transform.worldToScreen(x0, y0)
    val right = transform.worldToScreen(x1, y0)
    val front = transform.worldToScreen(x1, y1)
    val left = transform.worldToScreen(x0, y1)
    val centre = transform.worldToScreen((x0 + x1) / 2f, (y0 + y1) / 2f)
    val unit = transform.tileWidth
}

private val SHEET_LEFT = Color(0xFFDCEBE4)
private val SHEET_RIGHT = Color(0xFFBFD6CC)
private val SHEET_TOP = Color(0xFFEDF5F0)
private val SHEET_STRIPE = Color(0xFF7FB0A0)
private val SHEET_SEAM = Color(0xFF6C8F84)
private val BUBBLE = Color(0xFFFFFDF2)
private val BUBBLE_EDGE = Color(0xFFE2A93B)
private val CONFETTI = listOf(
    Color(0xFFFF8A80), Color(0xFFFFD54F), Color(0xFF81D4FA), Color(0xFFA5D6A7), Color(0xFFCE93D8), Color(0xFFFFFFFF),
)

/**
 * Draws a work site: a box of builders' sheeting on a wooden scaffold, which hides whatever is
 * inside it completely. Nothing of the finished building shows until it is unveiled.
 *
 * While the work goes on, dust puffs up at its foot and a bar above it fills ([progress], 0 to 1).
 * A building that is finished and waiting ([WorkKind.READY]) stands still and has a bouncing
 * "✨ ！" bubble over it instead, asking to be tapped.
 *
 * @param clock a running millisecond count, for the bits that move.
 */
private fun DrawScope.drawWorkSite(transform: IsometricTransform, obj: GridObject, progress: Float, clock: Long) {
    val box = SiteBox(transform, obj)
    val unit = box.unit
    val up = box.up
    val stroke = (unit * 0.022f).coerceAtLeast(1.5f)
    val ready = obj.workKind == WorkKind.READY

    // The scaffold's far sides, then the sheeting, which hides them up to its top edge.
    drawScaffoldSide(box.back, box.right, up, stroke, alpha = 0.8f)
    drawScaffoldSide(box.back, box.left, up, stroke, alpha = 0.8f)

    val sheetUp = up * 0.94f
    drawPath(quad(box.left, box.front, box.front + sheetUp, box.left + sheetUp), SHEET_LEFT)
    drawPath(quad(box.front, box.right, box.right + sheetUp, box.front + sheetUp), SHEET_RIGHT)
    drawPath(quad(box.back + sheetUp, box.right + sheetUp, box.front + sheetUp, box.left + sheetUp), SHEET_TOP)
    // Stripes down the two near faces, as on a builder's tarpaulin, and a hem along the bottom.
    for ((from, to) in listOf(box.left to box.front, box.front to box.right)) {
        for (i in 1..3) {
            val foot = from + (to - from) * (i / 4f)
            drawLine(SHEET_STRIPE.copy(alpha = 0.55f), foot, foot + sheetUp, stroke * 1.6f)
        }
        drawLine(SHEET_SEAM, from, to, stroke * 0.9f, StrokeCap.Round)
        drawLine(SHEET_SEAM.copy(alpha = 0.7f), from + sheetUp, to + sheetUp, stroke * 0.7f, StrokeCap.Round)
    }
    drawLine(SHEET_SEAM, box.front, box.front + sheetUp, stroke * 0.8f)

    // The near sides of the scaffold stand in front of the sheeting, with a rope across each.
    drawScaffoldSide(box.left, box.front, up, stroke, alpha = 1f)
    drawScaffoldSide(box.front, box.right, up, stroke, alpha = 1f)
    drawLine(SCAFFOLD_ROPE, box.left, box.front + up * 0.5f, stroke * 0.6f, StrokeCap.Round)
    drawLine(SCAFFOLD_ROPE, box.front, box.right + up * 0.5f, stroke * 0.6f, StrokeCap.Round)

    val above = box.centre + up * 1.2f
    if (ready) {
        drawReadyBubble(above, unit, clock)
        return
    }

    // Dust puffing up along the front, each puff in its own rhythm, and the sheeting shaking a
    // little where the work is going on behind it.
    for (i in 0 until 4) {
        val phase = ((clock + i * 430L) % 1400L) / 1400f
        val along = 0.15f + 0.23f * i
        val foot = if (i % 2 == 0) box.left + (box.front - box.left) * along else box.front + (box.right - box.front) * along
        drawCircle(
            color = DUST.copy(alpha = 0.6f * (1f - phase)),
            radius = unit * (0.022f + 0.05f * phase),
            center = foot + Offset(0f, -unit * 0.08f * phase),
        )
    }
    val hop = ((clock / 420L) % 4L).toInt()
    val beat = 1f - ((clock % 420L) / 420f)
    val knock = box.left + (box.right - box.left) * (0.2f + 0.2f * hop) + up * 0.55f
    drawSparkle(knock, unit * 0.05f * beat, SPARKLE.copy(alpha = beat))

    // The bar above it all.
    val barSize = Size(unit * 0.62f, unit * 0.075f)
    val barTopLeft = above - Offset(barSize.width / 2f, barSize.height + unit * 0.05f)
    val corner = CornerRadius(barSize.height / 2f, barSize.height / 2f)
    drawRoundRect(PROGRESS_TRACK, barTopLeft, barSize, corner)
    val pad = barSize.height * 0.18f
    val fill = (barSize.width - pad * 2f) * progress.coerceIn(0f, 1f)
    if (fill > 0f) {
        drawRoundRect(
            color = if (obj.workKind == WorkKind.BUILDING) PROGRESS_BUILD else PROGRESS_TAKE_DOWN,
            topLeft = barTopLeft + Offset(pad, pad),
            size = Size(fill, barSize.height - pad * 2f),
            cornerRadius = corner,
        )
    }
}

/**
 * The "✨ ！" over a finished building that waits to be unveiled: a round speech bubble with a
 * sparkle and an exclamation mark in it, bobbing up and down to catch the eye.
 *
 * @param tip where the bubble's tail points, just above the site.
 */
/**
 * The "the garden can grow" bubble over the symbol building: the same round speech bubble as the
 * "✨ ！" one, edged in leaf green, with arrows pointing out to its corners.
 */
private fun DrawScope.drawGrowBubble(tip: Offset, unit: Float, clock: Long) {
    val (centre, radius) = drawSpeechBubble(tip, unit, clock, GROW_EDGE)
    val reach = radius * 0.5f
    val head = radius * 0.2f
    val stroke = radius * 0.13f
    for ((dx, dy) in listOf(1f to -1f, -1f to 1f, 1f to 1f, -1f to -1f)) {
        val end = centre + Offset(dx * reach, dy * reach * 0.8f)
        drawLine(GROW_EDGE, centre, end, stroke, StrokeCap.Round)
        drawLine(GROW_EDGE, end, end - Offset(dx * head, 0f), stroke, StrokeCap.Round)
        drawLine(GROW_EDGE, end, end - Offset(0f, dy * head), stroke, StrokeCap.Round)
    }
}

private val GROW_EDGE = Color(0xFF6E9E3A)

/**
 * A round speech bubble bobbing over [tip], its tail pointing down at it, edged in [edge].
 * @return its middle and its radius, for what is drawn inside it.
 */
private fun DrawScope.drawSpeechBubble(tip: Offset, unit: Float, clock: Long, edge: Color): Pair<Offset, Float> {
    val bob = sin(clock / 260.0).toFloat() * unit * 0.035f
    val radius = unit * TapTargets.READY_BUBBLE_RADIUS
    val centre = tip + Offset(0f, -radius * TapTargets.READY_BUBBLE_LIFT + bob)
    val tail = Path().apply {
        moveTo(centre.x - radius * 0.35f, centre.y + radius * 0.8f)
        lineTo(tip.x, tip.y + bob * 0.4f)
        lineTo(centre.x + radius * 0.35f, centre.y + radius * 0.8f)
        close()
    }
    val width = unit * 0.022f
    drawPath(tail, edge)
    drawCircle(edge, radius + width, centre)
    drawCircle(BUBBLE, radius, centre)
    drawPath(
        Path().apply {
            moveTo(centre.x - radius * 0.22f, centre.y + radius * 0.75f)
            lineTo(tip.x, tip.y + bob * 0.4f - width * 2f)
            lineTo(centre.x + radius * 0.22f, centre.y + radius * 0.75f)
            close()
        },
        BUBBLE,
    )
    return centre to radius
}

private fun DrawScope.drawReadyBubble(tip: Offset, unit: Float, clock: Long) {
    val (centre, radius) = drawSpeechBubble(tip, unit, clock, BUBBLE_EDGE)
    // The sparkle twinkles; the exclamation mark is a bar and a dot.
    val twinkle = 0.8f + 0.2f * sin(clock / 140.0).toFloat()
    drawSparkle(centre + Offset(-radius * 0.34f, -radius * 0.05f), radius * 0.48f * twinkle, BUBBLE_EDGE)
    drawSparkle(centre + Offset(-radius * 0.02f, -radius * 0.5f), radius * 0.2f * twinkle, BUBBLE_EDGE)
    val markX = centre.x + radius * 0.42f
    drawLine(SCAFFOLD_LINE, Offset(markX, centre.y - radius * 0.5f), Offset(markX, centre.y + radius * 0.12f), radius * 0.2f, StrokeCap.Round)
    drawCircle(SCAFFOLD_LINE, radius * 0.11f, Offset(markX, centre.y + radius * 0.46f))
}

/** How big something that was just unveiled is drawn at [progress] (0 to 1): up past full size and back. */
internal fun popScale(progress: Float): Float {
    val t = (progress / 0.45f).coerceIn(0f, 1f)
    // From 0.55 up to 1.18 in the first third of the way, then easing back down to 1.
    return if (t < 0.6f) 0.55f + (1.18f - 0.55f) * (t / 0.6f) else 1.18f - 0.18f * ((t - 0.6f) / 0.4f)
}

/** One side of the scaffolding between two corners on the ground: two poles and two planks. */
private fun DrawScope.drawScaffoldSide(from: Offset, to: Offset, up: Offset, stroke: Float, alpha: Float) {
    val pole = SCAFFOLD_POLE.copy(alpha = alpha)
    val plank = SCAFFOLD_PLANK.copy(alpha = alpha)
    val outline = SCAFFOLD_LINE.copy(alpha = alpha * 0.8f)
    for (level in floatArrayOf(0.5f, 1f)) {
        drawLine(outline, from + up * level, to + up * level, stroke * 1.9f, StrokeCap.Round)
        drawLine(plank, from + up * level, to + up * level, stroke * 1.2f, StrokeCap.Round)
    }
    for (foot in arrayOf(from, to)) {
        drawLine(outline, foot, foot + up * 1.08f, stroke * 1.9f, StrokeCap.Round)
        drawLine(pole, foot, foot + up * 1.08f, stroke * 1.2f, StrokeCap.Round)
    }
}

/** A four-pointed star. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val waist = radius * 0.28f
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + waist, center.y - waist)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + waist, center.y + waist)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - waist, center.y + waist)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - waist, center.y - waist)
        close()
    }
    drawPath(star, color)
}

/**
 * The quiet sparkle the first steps put on a cell: three small stars round the middle of the
 * cell, a little above the ground, each fading in and out on its own beat, over a faint glow.
 * Kept small and soft: it points the way without calling out.
 */
private fun DrawScope.drawHintTwinkle(transform: IsometricTransform, cell: GridCell, clock: Long) {
    val unit = transform.tileWidth
    val centre = transform.worldToScreen(cell.x + 0.5f, cell.y + 0.5f) + Offset(0f, -unit * 0.18f)
    val breath = 0.5f + 0.5f * sin(clock / 700.0).toFloat()
    drawCircle(
        brush = Brush.radialGradient(
            listOf(HINT_GLOW.copy(alpha = 0.10f + 0.12f * breath), HINT_GLOW.copy(alpha = 0f)),
            center = centre,
            radius = unit * 0.32f,
        ),
        radius = unit * 0.32f,
        center = centre,
    )
    for ((index, spot) in HINT_SPOTS.withIndex()) {
        val beat = 0.5f + 0.5f * sin(clock / 380.0 + index * 2.1).toFloat()
        val at = centre + Offset(spot.x * unit, spot.y * unit)
        drawSparkle(at, unit * (0.035f + 0.035f * beat), HINT_STAR.copy(alpha = 0.35f + 0.6f * beat))
    }
}

private val HINT_GLOW = Color(0xFFFFF3B0)
private val HINT_STAR = Color(0xFFFFFDE7)

/** Where the hint's stars sit round the middle of the cell, in tile widths. */
private val HINT_SPOTS = listOf(Offset(-0.14f, -0.06f), Offset(0.12f, -0.16f), Offset(0.05f, 0.06f))

/**
 * The unveiling of a building, set off by the player's tap: a white flash, the scaffold's poles
 * and planks flung outwards, tumbling, the sheeting gone in a puff, a ring spreading over the
 * ground, and confetti thrown up that flutters down again. The building itself pops up through
 * it (see [popScale]).
 *
 * @param progress 0 (the tap) to 1 (over).
 */
private fun DrawScope.drawCompletionBurst(transform: IsometricTransform, obj: GridObject, progress: Float) {
    val box = SiteBox(transform, obj)
    val unit = box.unit
    val fade = (1f - progress).coerceIn(0f, 1f)
    val middle = box.centre + box.up * 0.5f

    // The flash: over almost as soon as it begins.
    val flash = (1f - progress / 0.22f).coerceIn(0f, 1f)
    if (flash > 0f) {
        val radius = (box.height + unit * 0.6f) * (0.4f + 0.8f * (1f - flash))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.95f * flash), Color.White.copy(alpha = 0f)),
                center = middle,
                radius = radius,
            ),
            radius = radius,
            center = middle,
        )
    }
    // Puffs of what was the sheeting.
    for (i in 0 until 6) {
        val angle = (i / 6f) * 2f * PI.toFloat() + 0.4f
        val out = unit * 0.5f * progress
        drawCircle(
            color = SHEET_TOP.copy(alpha = 0.7f * fade * fade),
            radius = unit * (0.1f + 0.16f * progress),
            center = middle + Offset(cos(angle) * out, sin(angle) * out * 0.5f),
        )
    }
    // The ring on the ground: twice as wide as it is tall, as it lies flat.
    val reach = unit * (0.3f + 0.6f * obj.footprint.width) * progress
    drawOval(
        color = SPARKLE.copy(alpha = 0.85f * fade),
        topLeft = box.centre - Offset(reach, reach / 2f),
        size = Size(reach * 2f, reach),
        style = Stroke(width = unit * 0.04f * fade),
    )
    // The scaffold comes apart: eight pieces thrown outwards, turning as they go and falling.
    val stroke = (unit * 0.022f).coerceAtLeast(1.5f)
    for (i in 0 until 8) {
        val angle = (i / 8f) * 2f * PI.toFloat() + 0.2f
        val out = unit * 1.1f * progress
        val drop = unit * 0.9f * progress * progress
        val at = middle + Offset(cos(angle) * out, sin(angle) * out * 0.45f - box.height * 0.3f * (1f - progress) + drop)
        val spin = angle * 3f + progress * 9f
        val half = Offset(cos(spin), sin(spin)) * (unit * 0.11f)
        drawLine(SCAFFOLD_LINE.copy(alpha = fade), at - half, at + half, stroke * 1.9f, StrokeCap.Round)
        drawLine((if (i % 2 == 0) SCAFFOLD_POLE else SCAFFOLD_PLANK).copy(alpha = fade), at - half, at + half, stroke * 1.2f, StrokeCap.Round)
    }
    // Confetti: thrown up and out, then fluttering down, each piece turning in its own time.
    for (i in 0 until 26) {
        val seed = i * 2654435761L
        val spread = ((seed ushr 5) % 1000L) / 1000f * 2f - 1f          // -1 .. 1, left to right
        val lift = 0.55f + ((seed ushr 13) % 1000L) / 1000f * 0.75f     // how high this piece goes
        val x = spread * unit * 0.95f * (0.25f + 0.75f * progress)
        // Up fast, down slowly: a parabola in the progress.
        val y = -(box.height + unit * 0.75f) * lift * (4f * progress * (1f - progress * 0.72f)) * 0.62f
        val at = middle + Offset(x, y)
        val turn = progress * 14f + i
        val width = unit * 0.045f * (0.35f + 0.65f * abs(cos(turn)))
        val height = unit * 0.03f
        drawRect(
            color = CONFETTI[i % CONFETTI.size].copy(alpha = (fade * 1.6f).coerceAtMost(1f)),
            topLeft = at - Offset(width / 2f, height / 2f),
            size = Size(width, height),
        )
    }
    // And a few sparkles hanging in the air at the end.
    for (i in 0 until 5) {
        val angle = (i / 5f) * 2f * PI.toFloat()
        val at = middle + Offset(cos(angle) * unit * 0.45f, -box.height * 0.5f + sin(angle) * unit * 0.2f)
        drawSparkle(at, unit * 0.07f * sin(PI.toFloat() * progress), SPARKLE.copy(alpha = fade))
    }
}

/** How long something that has been removed takes to disappear. */
private const val VANISH_MILLIS = 380

/** Something that is no longer in the garden but is still being shown on its way out. */
private class Ghost(val gridObject: GridObject) {
    val progress = Animatable(0f)
}

/**
 * Draws [draw] as it disappears: it swells a little, lifts off the ground and fades, like a puff.
 *
 * @param pivot the point on the ground it stood on.
 * @param progress 0 (still whole) to 1 (gone).
 */
/**
 * Where a visitor's feet are: on the ground in the middle of its cell, or up on what it sits on
 * (the seat of a bench, the ridge of a roof).
 */
private fun visitorBase(transform: IsometricTransform, visit: CreatureVisit): Offset {
    val host = visit.host ?: return transform.tileCenter(visit.cell.x, visit.cell.y)
    val footprint = host.footprint
    val centreX = host.gridX + footprint.width / 2f
    val centreY = host.gridY + footprint.height / 2f
    val lift = when {
        StructureData.fromObjectId(host.objectId) != null -> StructureData.fromObjectId(host.objectId)!!.heightSteps - 0.8f
        host.objectId == Decoration.BENCH.objectId -> 0.8f
        else -> 0f
    }
    return transform.worldToScreen(centreX, centreY, lift)
}

/** Draws [draw] at [alpha] as a whole, so overlapping parts do not show through one another. */
private fun DrawScope.drawFaded(alpha: Float, draw: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
        draw()
        canvas.restore()
    }
}

/** How see-through a faded building is. */
private const val FADED_ALPHA = 0.32f

/** How long a building takes to fade out or back in. */
private const val FADE_MILLIS = 260

private fun DrawScope.drawVanishing(pivot: Offset, progress: Float, tileHeight: Float, draw: DrawScope.() -> Unit) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val grow = 1f + 0.18f * progress
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
        translate(top = -tileHeight * 0.35f * progress) {
            scale(grow, grow, pivot) { draw() }
        }
        canvas.restore()
    }
}

// --- Pictures of single objects ---------------------------------------------------------------

/**
 * A small patch of lawn with one kind of object on it, enough of it to show what it looks like
 * in the garden: a stretch of river rather than one tile, a paved square rather than one stone.
 *
 * @property gridSize the patch is this many tiles each way.
 * @property headroom space to leave above the far corner of the patch, in tile widths, for
 *   whatever stands taller than that.
 */
private class PreviewScene(val gridSize: Int, val headroom: Float, val objects: List<GridObject>) {
    companion object {
        fun of(objectId: String): PreviewScene {
            fun at(x: Int, y: Int) = GridObject(
                instanceId = "preview_${x}_$y",
                gridX = x,
                gridY = y,
                objectId = objectId,
                growthProgress = 1f,
                lastUpdatedTimestamp = 0L,
            )
            return when {
                objectId == GridObject.RIVER_OBJECT_ID -> PreviewScene(3, 0.1f, listOf(at(0, 1), at(1, 1), at(2, 1)))
                objectId.startsWith(GridObject.FLOOR_ID_PREFIX) ->
                    PreviewScene(2, 0.1f, listOf(at(0, 0), at(1, 0), at(0, 1), at(1, 1)))
                objectId.startsWith(GridObject.PLANT_ID_PREFIX) -> PreviewScene(1, 1.05f, listOf(at(0, 0)))
                // A run with a corner in it, to show how fences join.
                objectId == GridObject.FENCE_OBJECT_ID ->
                    PreviewScene(3, 0.25f, listOf(at(0, 1), at(1, 1), at(2, 1), at(2, 0)))
                StructureData.fromObjectId(objectId) != null -> {
                    val footprint = StructureData.fromObjectId(objectId)!!.footprint
                    PreviewScene(maxOf(2, footprint.width, footprint.height), 0.45f, listOf(at(0, 0)))
                }
                else -> PreviewScene(1, 0.3f, listOf(at(0, 0)))
            }
        }
    }
}

/** The lawn of a preview sits on a slab of soil this thick, in tile heights (see drawTerrain). */
private const val PREVIEW_SOIL = 0.6f

/** Room left round a building that reaches past its patch of lawn, in tile widths. */
private const val PREVIEW_MARGIN = 0.08f

/**
 * How far the picture of a preview reaches on screen, in tile widths, from the patch's far
 * corner: its lawn and the soil under it, the [headroom] above, and the buildings standing on
 * it, roof, balcony and turning sails included. A building's picture is often taller and wider
 * than its patch (a windmill's sails reach well past its side), so the preview is fitted to this
 * rather than to the patch alone.
 *
 * @param sprites the sprite for an asset id, or null when the atlas has none.
 * @param atlasTileWidth atlas pixels per tile width.
 * @param heightStep one height step, in tile widths.
 */
internal data class PreviewExtent(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        fun of(
            gridSize: Int,
            headroom: Float,
            objects: List<GridObject>,
            sprites: (String) -> AssetManifest?,
            atlasTileWidth: Float,
            heightStep: Float,
        ): PreviewExtent {
            val n = gridSize.toFloat()
            var left = -n / 2f
            var right = n / 2f
            var top = -headroom
            val bottom = (n + PREVIEW_SOIL) / 2f
            val transform = IsometricTransform(tileWidth = 1f, tileHeight = 0.5f, heightStep = heightStep)
            val scale = 1f / atlasTileWidth
            fun reach(l: Float, t: Float, r: Float) {
                left = min(left, l - PREVIEW_MARGIN)
                right = max(right, r + PREVIEW_MARGIN)
                top = min(top, t - PREVIEW_MARGIN)
            }
            for (obj in objects) {
                val structure = StructureData.fromObjectId(obj.objectId) ?: continue
                val footprint = structure.footprint
                val base = transform.worldToScreen(obj.gridX + footprint.width / 2f, obj.gridY + footprint.height / 2f)
                val sprite = sprites(TileSetDefinition.assetIdFor(obj))
                if (sprite == null) {
                    // The drawn stand-in reaches no higher than the building is tall.
                    reach(base.x - footprint.width / 2f, base.y - structure.heightSteps * heightStep, base.x + footprint.width / 2f)
                    continue
                }
                val spriteLeft = sprite.drawLeft(base.x, scale)
                reach(spriteLeft, sprite.drawTop(base.y, scale), spriteLeft + sprite.drawnWidth(scale))
                val sails = if (obj.objectId == WindmillSails.MILL_OBJECT_ID) sprites(WindmillSails.SAILS_ASSET_ID) else null
                val hub = sprite.attachmentAt(base.x, base.y, scale, obj.isFlipped)
                if (sails != null && hub != null) {
                    // Turning, the sails sweep a circle round the hub as wide as their furthest corner.
                    val radius = hypot(
                        max(sails.anchorX, 1f - sails.anchorX) * sails.drawnWidth(scale),
                        max(sails.anchorY, 1f - sails.anchorY) * sails.drawnHeight(scale),
                    )
                    reach(hub.first - radius, hub.second - radius, hub.first + radius)
                }
            }
            return PreviewExtent(left, top, right, bottom)
        }
    }
}

/**
 * A picture of the object with id [objectId] as it appears in the garden, drawn by the very code
 * that draws the garden, so the two cannot drift apart. Fitted into whatever size it is given.
 */
@Composable
fun ObjectPreview(
    objectId: String,
    atlas: SpriteAtlas,
    terrain: TerrainTextures,
    modifier: Modifier = Modifier,
) {
    val scene = remember(objectId) { PreviewScene.of(objectId) }
    val map = remember(scene) { GardenMap.allGrass(scene.gridSize) }
    val grid = remember(scene) { GardenGrid(map, scene.objects) }
    val atlasImage = remember(atlas) { atlas.bitmap?.asImageBitmap() }
    val lawn = remember(atlas) { FeatheredLawn.from(atlas, GroundTile.GRASS.assetId) }
    val pipeline = remember { LayerPipeline() }
    val waterTick by rememberWaterTick(running = scene.objects.any { it.isTerrain })

    Canvas(modifier) {
        // Everything the picture shows, buildings included, fitted into the frame and centred
        // in it. Sizes are in tile widths; a tile is half as tall as it is wide.
        val extent = PreviewExtent.of(
            scene.gridSize, scene.headroom, scene.objects, { atlas[it] }, atlas.manifest.tileWidth.toFloat(), HEIGHT_STEP / TILE_WIDTH,
        )
        val tileWidth = min(size.width / extent.width, size.height / extent.height)
        val transform = IsometricTransform(
            tileWidth = tileWidth,
            tileHeight = tileWidth / 2f,
            heightStep = tileWidth * (HEIGHT_STEP / TILE_WIDTH),
            offsetX = size.width / 2f - (extent.left + extent.right) / 2f * tileWidth,
            offsetY = size.height / 2f - (extent.top + extent.bottom) / 2f * tileWidth,
        )
        val spriteScale = tileWidth / atlas.manifest.tileWidth
        pipeline.submit(RenderLayer.BASE_TERRAIN) {
            drawTerrain(transform, map, framed = false, Ambience.CALM, lawn) { tile, center, flip ->
                val sprite = atlas[tile.assetId]
                if (sprite != null && atlasImage != null) drawSprite(atlasImage, sprite, center, spriteScale * groundOverlap(tile), flipped = flip)
                sprite != null
            }
        }
        submitObjects(pipeline, scene.objects, grid, transform, atlas, atlasImage, terrain, waterTick, Ambience.CALM)
        pipeline.render(this)
    }
}

// --- Layer 0: terrain -------------------------------------------------------------------------

// Warm, slightly yellow greens with earth showing through here and there: ground that grass has
// grown over rather than a lawn laid on top, and close to the leaves of the plant sprites.
private val GRASS_LIGHT = Color(0xFF9CBB6C)
private val GRASS_DARK = Color(0xFF90B062)
private val GRASS_TUFT = Color(0xFF739E4D)
private val GRASS_BLADE_LIGHT = Color(0xFFA9C86F)
private val EARTH_PATCH = Color(0xFFBF9F6C)
private val PAVING_FALLBACK = Color(0xFFBDBDBD)
private val SOIL_LEFT = Color(0xFF8D6E63)
private val SOIL_RIGHT = Color(0xFF6D4C41)

// The wooden frame the garden sits in.
private val FRAME_TOP = Color(0xFFD2A66E)
private val FRAME_TOP_SHADE = Color(0xFFC09458)
private val FRAME_SIDE_LEFT = Color(0xFFA5733F)
private val FRAME_SIDE_RIGHT = Color(0xFF7F5530)
private val FRAME_LINE = Color(0xFF5D3A1A)
private val FRAME_HIGHLIGHT = Color(0xFFF1D4A3)

/** Width of the frame's top face, in tiles. */
const val FRAME_RIM = 0.34f

/** Height of the frame's side faces, in tile heights. */
private const val FRAME_DEPTH = 0.85f

/**
 * @param framed true for the garden itself: the ground sits in a wooden frame, like a diorama
 *   in its box. False draws the plain slab of soil that the small previews stand on.
 * @param drawTileSprite draws the sprite for a ground tile centered on the given point and
 *   returns true, or returns false when there is none and the flat-color tile should be used.
 */
private fun DrawScope.drawTerrain(
    transform: IsometricTransform,
    gardenMap: GardenMap,
    framed: Boolean,
    ambience: Ambience,
    lawn: FeatheredLawn?,
    wasteland: Bitmap? = null,
    drawTileSprite: DrawScope.(tile: GroundTile, center: Offset, flip: Boolean) -> Boolean,
) {
    val gridSize = gardenMap.size
    val max = gridSize.toFloat()
    if (framed) {
        drawFrame(transform, max)
    } else {
        val left = transform.worldToScreen(0f, max)
        val bottom = transform.worldToScreen(max, max)
        val right = transform.worldToScreen(max, 0f)
        val thickness = Offset(0f, transform.tileHeight * 0.6f)
        // The two visible side faces of the soil slab the patch sits on.
        drawPath(quad(left, bottom, bottom + thickness, left + thickness), SOIL_LEFT)
        drawPath(quad(bottom, right, right + thickness, bottom + thickness), SOIL_RIGHT)
    }

    // Untilled ground: the cracked earth of res/drawable/soil_wasteland.png, flattened
    // (FlatGround) and laid over the whole garden at once, like the beds of a field: one ground
    // with no line between cells, and none of the picture's rim or tufts.
    if (wasteland != null) drawFlatGround(wasteland, transform, gardenOutline(transform, max))

    // The lawn, feathered (#47, LawnShade.feather): one colour under all of it, then each cell
    // of grass drawn larger than itself and fading into its neighbours, so that no line runs
    // between two cells. Kept inside the garden, and laid before any water, which it must not
    // spill over. Where the wasteland is the bare ground, there is no lawn.
    val lawnPath = lawn?.takeIf { wasteland == null }?.let {
        Path().apply {
            val a = transform.worldToScreen(0f, 0f)
            val b = transform.worldToScreen(max, 0f)
            val c = transform.worldToScreen(max, max)
            val d = transform.worldToScreen(0f, max)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
    }
    if (lawn != null && lawnPath != null && wasteland == null) {
        drawPath(lawnPath, lawn.base)
        val spriteScale = transform.tileWidth / lawn.tileWidth * LawnShade.FEATHER_OVERLAP
        clipPath(lawnPath) {
            for (x in 0 until gridSize) for (y in 0 until gridSize) {
                val type = gardenMap.tileAt(x, y) ?: TileType.GRASS
                if (TileSetDefinition.groundTileFor(type, x, y) != GroundTile.GRASS) continue
                drawSprite(lawn.image, lawn.sprite, transform.tileCenter(x, y), spriteScale, flipped = LawnShade.mirrored(x, y))
            }
        }
    }
    // x-then-y order draws every tile before the two neighbours in front of it.
    for (x in 0 until gridSize) for (y in 0 until gridSize) {
        val type = gardenMap.tileAt(x, y) ?: TileType.GRASS
        val tile = TileSetDefinition.groundTileFor(type, x, y)
        val laid = (lawn != null || wasteland != null) && tile == GroundTile.GRASS
        // Cells of grass are mirrored at random, so that the one picture does not repeat in step.
        if (!laid && !drawTileSprite(tile, transform.tileCenter(x, y), LawnShade.mirrored(x, y))) {
            when (tile) {
                GroundTile.WATER -> drawWaterTile(transform, x, y, phase = 0)
                GroundTile.GRASS -> drawGroundTile(transform, x, y, GRASS_LIGHT, ambience.sway(x + 0.5f, y + 0.5f))
                else -> drawGroundTile(transform, x, y, GRASS_DARK, ambience.sway(x + 0.5f, y + 0.5f))
            }
        }
    }
    if (wasteland == null) drawLawnShade(transform, gridSize)
}

/** The outline of the whole garden, a diamond [max] cells a side. */
private fun gardenOutline(transform: IsometricTransform, max: Float): Path = Path().apply {
    val a = transform.worldToScreen(0f, 0f)
    val b = transform.worldToScreen(max, 0f)
    val c = transform.worldToScreen(max, max)
    val d = transform.worldToScreen(0f, max)
    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
}

/** What a cell with nothing laid on it looks like. */
enum class BareGround {
    /** Untilled earth (`soil_wasteland`): a garden being made. */
    WASTELAND,

    /** The feathered lawn: the title's garden in full swing, and where the wasteland is missing. */
    LAWN,
}

/** The pond's ring of stones fills its cell, just short of the edges. */
private const val POND_COVERAGE = 0.98f

/**
 * Where the garden box's rim meets the ground inside it. Drawn after everything that lies on the
 * ground (the lawn, the stream, paving, beds), so that all of it is tucked in under the rim
 * rather than laid over it: the two far boards cast a little shade onto the ground, and a dark
 * line runs where wood meets earth.
 */
private fun DrawScope.drawFrameLip(transform: IsometricTransform, max: Float) {
    val a = transform.worldToScreen(0f, 0f)
    val b = transform.worldToScreen(max, 0f)
    val c = transform.worldToScreen(max, max)
    val d = transform.worldToScreen(0f, max)
    // Shade under the far boards, which stand between the light and the ground below them.
    val reach = FRAME_LIP_SHADE
    for ((from, to, inward) in listOf(Triple(a, b, transform.worldToScreen(0f, reach) - a), Triple(a, d, transform.worldToScreen(reach, 0f) - a))) {
        // The fade runs square to the board on screen, so it is even all along it.
        val along = to - from
        val length = along.getDistance().coerceAtLeast(1e-3f)
        var normal = Offset(-along.y / length, along.x / length)
        if (normal.x * inward.x + normal.y * inward.y < 0f) normal = -normal
        val depth = normal.x * inward.x + normal.y * inward.y
        drawPath(
            quad(from, to, to + inward, from + inward),
            Brush.linearGradient(listOf(FRAME_LINE.copy(alpha = 0.26f), FRAME_LINE.copy(alpha = 0f)), start = from, end = from + normal * depth),
        )
    }
    val width = transform.tileWidth * 0.022f
    drawLine(FRAME_LINE.copy(alpha = 0.6f), a, b, width, StrokeCap.Round)
    drawLine(FRAME_LINE.copy(alpha = 0.6f), a, d, width, StrokeCap.Round)
    drawLine(FRAME_LINE.copy(alpha = 0.32f), b, c, width, StrokeCap.Round)
    drawLine(FRAME_LINE.copy(alpha = 0.32f), d, c, width, StrokeCap.Round)
}

/** How far the far boards' shade reaches over the ground, in tiles. */
private const val FRAME_LIP_SHADE = 0.22f

/** Grass cells are drawn a little larger than their diamonds, so that neighbours overlap at the seam. */
private fun groundOverlap(tile: GroundTile): Float = if (tile == GroundTile.WATER) 1f else LawnShade.CELL_OVERLAP

/**
 * Broad, faint light and shade over the whole lawn, crossing the cells' edges ([LawnShade]), so
 * that the lawn reads as one meadow rather than as tiles. Kept inside the garden.
 */
private fun DrawScope.drawLawnShade(transform: IsometricTransform, gridSize: Int) {
    val n = gridSize.toFloat()
    val lawn = Path().apply {
        val a = transform.worldToScreen(0f, 0f)
        val b = transform.worldToScreen(n, 0f)
        val c = transform.worldToScreen(n, n)
        val d = transform.worldToScreen(0f, n)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    clipPath(lawn) {
        for (patch in LawnShade.patches(gridSize)) {
            val at = transform.worldToScreen(patch.x, patch.y)
            val radius = transform.tileWidth * 0.5f * patch.radius
            val colour = if (patch.light) LAWN_LIGHT else LAWN_SHADE
            // Flattened into the ground's 2:1 slant.
            scale(scaleX = 1f, scaleY = 0.5f, pivot = at) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colour.copy(alpha = patch.strength), colour.copy(alpha = 0f)),
                        center = at,
                        radius = radius,
                    ),
                    radius = radius,
                    center = at,
                )
            }
        }
    }
}

private val LAWN_LIGHT = Color(0xFFF4F1C8)
private val LAWN_SHADE = Color(0xFF1F4A1C)

/**
 * One tile of open ground: grass in the given shade, with a worn patch or two of earth and a few
 * darker tufts. Where they fall depends only on the tile's position, so the ground looks the
 * same every time and does not shimmer as the camera moves. The tips of the tufts move with
 * the wind ([sway], see [Wind.sway]); their roots stay put.
 */
private fun DrawScope.drawGroundTile(transform: IsometricTransform, x: Int, y: Int, grass: Color, sway: Float) {
    drawPath(transform.tilePath(x, y), grass)
    val unit = transform.tileWidth
    var seed = x * 73856093 xor y * 19349663
    fun next(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 16) and 0x7FFF) / 32767f
    }
    // Earth showing through: flat ovals, kept away from the tile's corners.
    repeat(2) {
        val at = transform.worldToScreen(x + 0.25f + next() * 0.5f, y + 0.25f + next() * 0.5f)
        val width = unit * (0.08f + next() * 0.09f)
        drawOval(EARTH_PATCH.copy(alpha = 0.38f), at - Offset(width / 2f, width / 4f), Size(width, width / 2f))
    }
    // Tufts: two short blades each.
    repeat(3) {
        val at = transform.worldToScreen(x + 0.15f + next() * 0.7f, y + 0.15f + next() * 0.7f)
        val blade = unit * 0.035f
        val stroke = unit * 0.012f
        val bend = blade * 0.55f * sway
        drawLine(GRASS_TUFT, at, at + Offset(-blade * 0.5f + bend, -blade), stroke, StrokeCap.Round)
        drawLine(GRASS_TUFT, at, at + Offset(blade * 0.5f + bend, -blade * 0.8f), stroke, StrokeCap.Round)
    }
}

/** How far the top of a plant moves in the wind, as a share of its height, at full sway. */
private const val PLANT_LEAN = 0.09f

private val GUST_STREAK = Color(0xFFFFFFFF)

/**
 * The gust itself, made visible: a few pale streaks riding on its front as it sweeps over the
 * garden, strongest in the middle of the crossing and gone at either end.
 */
private fun DrawScope.drawGustStreaks(transform: IsometricTransform, gridSize: Int, gust: GustFront) {
    val unit = transform.tileWidth
    // Faint at the start and the end of the crossing, when the front is at the garden's edge.
    val across = gust.position / gridSize
    val strength = (1f - kotlin.math.abs(across * 2f - 1f)).coerceIn(0f, 1f)
    if (strength <= 0f) return
    for (lane in 0 until 5) {
        // Lanes are spread over the garden's width; each streak trails behind the front a little
        // differently, so that they do not form a ruled line.
        val side = (lane + 0.5f) * gridSize / 5f + (if (lane % 2 == 0) 0.3f else -0.25f)
        val lead = gust.position - (lane * 37 % 11) / 11f * 1.4f
        val length = 0.9f + (lane * 53 % 7) / 7f * 0.7f
        fun at(along: Float): Offset = when (gust.axis) {
            GustAxis.ALONG_X -> transform.worldToScreen(along, side)
            GustAxis.ALONG_Y -> transform.worldToScreen(side, along)
        }
        val head = lead.coerceIn(-0.5f, gridSize + 0.5f)
        val tail = (lead - length).coerceIn(-0.5f, gridSize + 0.5f)
        if (head - tail < 0.2f) continue
        // Lifted off the ground a little, and bowed, like a line drawn in the air.
        val lift = Offset(0f, -unit * (0.32f + 0.08f * lane))
        val from = at(tail) + lift
        val to = at(head) + lift
        val bow = Offset(0f, -unit * 0.06f)
        val streak = Path().apply {
            moveTo(from.x, from.y)
            quadraticTo((from.x + to.x) / 2f + bow.x, (from.y + to.y) / 2f + bow.y, to.x, to.y)
        }
        drawPath(
            path = streak,
            color = GUST_STREAK.copy(alpha = 0.42f * strength * gust.strength.coerceAtMost(1f)),
            style = Stroke(width = unit * 0.022f, cap = StrokeCap.Round),
        )
    }
}

/**
 * Blades of grass in front of the foot of something standing on open ground, so that it stands
 * in the grass and not on it.
 *
 * @param reach how far to either side of [base] the blades spread.
 */
private fun DrawScope.drawGrassAtFoot(base: Offset, unit: Float, reach: Float, sway: Float, seed: Int) {
    var state = seed * 7919 + 13
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7FFF) / 32767f
    }
    val stroke = unit * 0.014f
    repeat(5) { i ->
        val root = base + Offset((next() * 2f - 1f) * reach, unit * (0.012f + next() * 0.028f))
        val blade = unit * (0.04f + next() * 0.035f)
        val splay = (next() - 0.5f) * blade * 0.9f
        val bend = blade * 0.5f * sway
        drawLine(if (i % 2 == 0) GRASS_TUFT else GRASS_BLADE_LIGHT, root, root + Offset(splay + bend, -blade), stroke, StrokeCap.Round)
    }
}

/** Crumbs of earth heaped against the foot of something standing in a tilled bed. */
private fun DrawScope.drawEarthAtFoot(base: Offset, unit: Float, reach: Float, seed: Int) {
    var state = seed * 7919 + 29
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7FFF) / 32767f
    }
    repeat(6) { i ->
        val at = base + Offset((next() * 2f - 1f) * reach, unit * (0.005f + next() * 0.03f))
        drawCircle(if (i % 3 == 0) DIRT_RIDGE else DIRT_EDGE, unit * (0.012f + next() * 0.012f), at)
    }
}

/**
 * The dark patch right under something's foot: black in the middle, fading out, and flat on the
 * ground. It is what makes a thing look as if it touches the ground at all.
 *
 * @param width how wide the patch is on screen.
 */
private fun DrawScope.drawContactShadow(base: Offset, width: Float) {
    val radius = width / 2f
    if (radius <= 0f) return
    scale(scaleX = 1f, scaleY = 0.36f, pivot = base) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.34f),
                0.55f to Color.Black.copy(alpha = 0.2f),
                1f to Color.Transparent,
                center = base,
                radius = radius,
            ),
            radius = radius,
            center = base,
        )
    }
}

/**
 * The shadow of something round that has no sprite to take an outline from: an oval that
 * stretches away from the light.
 *
 * @param width of the thing on the ground, [height] how far it stands up; both on screen.
 */
private fun DrawScope.drawBlobShadow(base: Offset, width: Float, height: Float, light: SunLight) {
    if (light.alpha <= 0f) return
    val reach = Offset(light.dx * height, light.dy * height)
    val size = Size(width + abs(reach.x), width * 0.42f + abs(reach.y))
    val center = base + reach / 2f
    drawOval(Color.Black.copy(alpha = light.alpha), center - Offset(size.width / 2f, size.height / 2f), size)
}

/**
 * The shadow of a building: its footprint, and the same footprint pushed away from the light as
 * far as the building is tall, with everything in between.
 */
private fun DrawScope.drawStructureShadow(
    transform: IsometricTransform,
    obj: GridObject,
    structure: StructureData,
    light: SunLight,
) {
    if (light.alpha <= 0f) return
    val x0 = obj.gridX + STRUCTURE_INSET
    val y0 = obj.gridY + STRUCTURE_INSET
    val x1 = obj.gridX + structure.footprint.width - STRUCTURE_INSET
    val y1 = obj.gridY + structure.footprint.height - STRUCTURE_INSET
    val ground = obj.heightLevel.toFloat()
    // Height in steps, as in drawStructure; the hut's roof counts for half, as it tapers.
    val height = when (structure) {
        StructureData.HUT -> 5.5f
        StructureData.COTTAGE -> 6f
        StructureData.WINDMILL -> 8f
        StructureData.WELL -> 1.8f
    } * transform.heightStep
    val reach = Offset(light.dx * height, light.dy * height)
    val foot = listOf(
        transform.worldToScreen(x0, y0, ground),
        transform.worldToScreen(x1, y0, ground),
        transform.worldToScreen(x1, y1, ground),
        transform.worldToScreen(x0, y1, ground),
    )
    val outline = convexHull(foot + foot.map { it + reach })
    val path = Path().apply {
        outline.forEachIndexed { i, point -> if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        close()
    }
    drawPath(path, Color.Black.copy(alpha = light.alpha))
}

/** The smallest convex outline round [points], in order (Andrew's monotone chain). */
internal fun convexHull(points: List<Offset>): List<Offset> {
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size < 3) return sorted
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    fun half(input: List<Offset>): MutableList<Offset> {
        val chain = mutableListOf<Offset>()
        for (point in input) {
            while (chain.size >= 2 && cross(chain[chain.size - 2], chain.last(), point) <= 0f) chain.removeAt(chain.size - 1)
            chain += point
        }
        chain.removeAt(chain.size - 1)
        return chain
    }
    return half(sorted) + half(sorted.reversed())
}

// Tilled soil.
private val DIRT_DARK = Color(0xFF5B3D28)
private val DIRT_EDGE = Color(0xFF452C1C)
private val DIRT_RIDGE = Color(0xFF7B5536)
private val DIRT_DEEP = Color(0xFF3B2618)
private val DIRT_DRY = Color(0xFF9A7350)
private val DIRT_DUST = Color(0xFF7E5E40)
private val COMPOST_LEAF = Color(0xFF8FB25A)
private val COMPOST_STRAW = Color(0xFFE2C879)
private const val FURROWS = 4

/**
 * A tile the hoe has been over: dark, damp-looking earth with furrows running across it, set a
 * little in from the tile's edge so that a strip of grass still shows between two beds.
 * A [composted] bed is flecked with green and straw.
 */
/**
 * A bed of tilled soil on cell ([x], [y]). Its sides that [joins] carry on into the next bed run
 * straight along the cell's edge, with no rim, no spilled earth and no gap in the furrows, so that
 * beds side by side are drawn as one field (#47). Its open sides keep a ragged, dark rim with earth
 * spilled over onto the grass, and along the front (east and south) a low face of earth, the bed
 * standing a little proud of the lawn.
 *
 * The damp patches are placed over the ground as a whole ([SoilTexture.patches]); a bed draws
 * those of the cells round it too, clipped to itself, so a patch carries across the joins.
 */
private fun DrawScope.drawTilledSoil(
    transform: IsometricTransform,
    x: Int,
    y: Int,
    composted: Boolean,
    joins: SoilJoins = SoilJoins.NONE,
) {
    val unit = transform.tileWidth
    val inset = SoilTexture.INSET
    val x0 = x + if (joins.west) 0f else inset
    val y0 = y + if (joins.north) 0f else inset
    val x1 = x + 1 - if (joins.east) 0f else inset
    val y1 = y + 1 - if (joins.south) 0f else inset

    // Loose earth spilled over the open edges onto the grass: soft, flat smudges that thin out.
    for (speck in SoilTexture.drift(x, y, joins)) {
        val at = transform.worldToScreen(speck.x, speck.y)
        val radius = unit * speck.size
        scale(scaleX = 1f, scaleY = 0.5f, pivot = at) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(DIRT_DUST.copy(alpha = 0.5f * speck.shade), DIRT_DUST.copy(alpha = 0f)),
                    center = at,
                    radius = radius,
                ),
                radius = radius,
                center = at,
            )
        }
    }

    val ringWorld = SoilTexture.outline(x, y, joins)
    val ring = ringWorld.map { (wx, wy) -> transform.worldToScreen(wx, wy) }
    val bed = Path().apply {
        ring.forEachIndexed { i, point -> if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        close()
    }
    val pointsPerSide = ring.size / 4
    fun sidePoints(side: Int): List<Offset> = (0..pointsPerSide).map { ring[(side * pointsPerSide + it) % ring.size] }

    // The low face of earth along the open front sides (east and south): the bed stands proud.
    val lip = Offset(0f, unit * 0.03f)
    for (side in listOf(1, 2)) {
        if (joins.joined(side)) continue
        val edge = sidePoints(side)
        val face = Path().apply {
            edge.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            edge.asReversed().forEach { p -> lineTo(p.x + lip.x, p.y + lip.y) }
            close()
        }
        drawPath(face, if (side == 1) DIRT_DEEP else DIRT_EDGE)
    }

    // The earth: one colour shading smoothly over the field, ...
    val damp = SoilTexture.fieldDampness(x + 0.5f, y + 0.5f)
    val body = lerp(DIRT_DRY, DIRT_DARK, 0.5f + 0.35f * damp)
    drawPath(bed, body)
    // ... with soft damp and dry patches laid over the whole field, clipped to this bed.
    clipPath(bed) {
        for (dx in -1..1) for (dy in -1..1) {
            for (patch in SoilTexture.patches(x + dx, y + dy)) {
                val at = transform.worldToScreen(patch.x, patch.y)
                val radius = unit * 0.5f * patch.radius
                val colour = if (patch.damp) DIRT_DEEP else DIRT_DRY
                scale(scaleX = 1f, scaleY = 0.5f, pivot = at) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colour.copy(alpha = if (patch.damp) 0.32f else 0.22f), colour.copy(alpha = 0f)),
                            center = at,
                            radius = radius,
                        ),
                        radius = radius,
                        center = at,
                    )
                }
            }
        }

        // Furrows in rows that are the same in every bed, so that a field's furrows run on from
        // bed to bed. A row stops a little short of an open edge, as a hoe leaves it.
        val stroke = unit * 0.022f
        for (row in SoilTexture.furrowRows(FURROWS)) {
            val fy = y + row
            if (fy <= y0 + 0.02f || fy >= y1 - 0.02f) continue
            val from = transform.worldToScreen(if (joins.west) x.toFloat() else x0 + 0.07f, fy)
            val to = transform.worldToScreen(if (joins.east) x + 1f else x1 - 0.07f, fy)
            val behind = Offset(0f, -stroke)
            drawLine(DIRT_DEEP, from + behind, to + behind, stroke, if (joins.west && joins.east) StrokeCap.Butt else StrokeCap.Round)
            drawLine(DIRT_RIDGE, from, to, stroke, if (joins.west && joins.east) StrokeCap.Butt else StrokeCap.Round)
            drawLine(DIRT_DRY.copy(alpha = 0.55f), from + Offset(0f, stroke * 0.3f), to + Offset(0f, stroke * 0.3f), stroke * 0.35f, StrokeCap.Butt)
        }
    }

    // The rim where the turf was cut, along the open sides only.
    val rim = unit * 0.026f
    for (side in 0 until 4) {
        if (joins.joined(side)) continue
        val edge = sidePoints(side)
        for (i in 0 until edge.size - 1) drawLine(DIRT_EDGE, edge[i], edge[i + 1], rim, StrokeCap.Round)
    }

    // Grains: crumbs of dry earth and small dark hollows, out to the joined edges as well.
    for (grain in SoilTexture.grains(x, y, joins)) {
        val at = transform.worldToScreen(grain.x, grain.y)
        val colour = when {
            grain.shade < 0.4f -> DIRT_DRY
            grain.shade < 0.75f -> DIRT_RIDGE
            else -> DIRT_DEEP
        }
        drawOval(colour.copy(alpha = 0.85f), at - Offset(unit * grain.size, unit * grain.size * 0.6f), Size(unit * grain.size * 2f, unit * grain.size * 1.2f))
    }

    // The open edges are not ruled with a pen: clods lie on them and the grass grows over them.
    var edgeSeed = x * 19349663 xor y * 83492791
    fun nextEdge(): Float {
        edgeSeed = edgeSeed * 1103515245 + 12345
        return ((edgeSeed ushr 16) and 0x7FFF) / 32767f
    }
    repeat(12) { i ->
        val along = 0.08f + nextEdge() * 0.84f
        val off = (nextEdge() - 0.5f) * 0.08f
        val side = when (i % 4) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> 3
        }
        val extra = nextEdge()
        if (joins.joined(side)) return@repeat
        val (wx, wy) = when (side) {
            0 -> (x0 + along * (x1 - x0)) to (y0 + off)
            1 -> (x1 + off) to (y0 + along * (y1 - y0))
            2 -> (x0 + along * (x1 - x0)) to (y1 + off)
            else -> (x0 + off) to (y0 + along * (y1 - y0))
        }
        val at = transform.worldToScreen(wx, wy)
        if (i < 6) {
            val radius = unit * (0.014f + extra * 0.014f)
            drawCircle(DIRT_DEEP, radius, at + Offset(0f, radius * 0.35f))
            drawCircle(if (i % 2 == 0) DIRT_DARK else DIRT_RIDGE, radius, at)
        } else {
            val blade = unit * (0.035f + extra * 0.02f)
            drawLine(GRASS_TUFT, at, at + Offset(-blade * 0.4f, -blade), unit * 0.012f, StrokeCap.Round)
            drawLine(GRASS_BLADE_LIGHT, at, at + Offset(blade * 0.45f, -blade * 0.8f), unit * 0.012f, StrokeCap.Round)
        }
    }
    if (composted) {
        // Bits of leaf and straw worked into the bed.
        var seed = x * 73856093 xor y * 19349663
        repeat(7) { i ->
            seed = seed * 1103515245 + 12345
            val u = ((seed ushr 16) and 0x7FFF) / 32767f
            seed = seed * 1103515245 + 12345
            val v = ((seed ushr 16) and 0x7FFF) / 32767f
            val at = transform.worldToScreen(x0 + 0.12f + u * (x1 - x0 - 0.24f), y0 + 0.12f + v * (y1 - y0 - 0.24f))
            drawCircle(if (i % 2 == 0) COMPOST_LEAF else COMPOST_STRAW, transform.tileWidth * 0.016f, at)
        }
    }
}

/**
 * The wooden frame round a garden [max] tiles across: a rim of planks level with the ground, the
 * two side faces that can be seen from the front, and the shadow the whole box casts.
 */
private fun DrawScope.drawFrame(transform: IsometricTransform, max: Float) {
    val rim = FRAME_RIM
    val depth = Offset(0f, transform.tileHeight * FRAME_DEPTH)
    val unit = transform.tileWidth
    // Outer and inner corners, named by where they appear on screen.
    val top = transform.worldToScreen(-rim, -rim)
    val right = transform.worldToScreen(max + rim, -rim)
    val bottom = transform.worldToScreen(max + rim, max + rim)
    val left = transform.worldToScreen(-rim, max + rim)
    val innerTop = transform.worldToScreen(0f, 0f)
    val innerRight = transform.worldToScreen(max, 0f)
    val innerBottom = transform.worldToScreen(max, max)
    val innerLeft = transform.worldToScreen(0f, max)

    // Shadow on whatever the box stands on: its own outline, pushed down and softened.
    val drop = depth + Offset(0f, unit * 0.1f)
    drawPath(quad(left + drop, bottom + drop + Offset(0f, unit * 0.06f), right + drop, bottom + depth), Color.Black.copy(alpha = 0.16f))

    // Side faces.
    drawPath(quad(left, bottom, bottom + depth, left + depth), FRAME_SIDE_LEFT)
    drawPath(quad(bottom, right, right + depth, bottom + depth), FRAME_SIDE_RIGHT)
    // A board line halfway down each, and the dark foot of the box.
    val seam = depth * 0.5f
    val line = unit * 0.014f
    drawLine(FRAME_LINE.copy(alpha = 0.35f), left + seam, bottom + seam, line)
    drawLine(FRAME_LINE.copy(alpha = 0.35f), bottom + seam, right + seam, line)
    drawLine(FRAME_LINE, left + depth, bottom + depth, line * 1.6f, StrokeCap.Round)
    drawLine(FRAME_LINE, bottom + depth, right + depth, line * 1.6f, StrokeCap.Round)
    drawLine(FRAME_LINE.copy(alpha = 0.6f), bottom, bottom + depth, line)

    // The rim: the two far boards a shade darker than the two near ones.
    drawPath(quad(top, right, innerRight, innerTop), FRAME_TOP_SHADE)
    drawPath(quad(top, innerTop, innerLeft, left), FRAME_TOP_SHADE)
    drawPath(quad(innerLeft, innerBottom, bottom, left), FRAME_TOP)
    drawPath(quad(innerRight, right, bottom, innerBottom), FRAME_TOP)
    // Mitred corners.
    for ((outer, inner) in listOf(top to innerTop, right to innerRight, bottom to innerBottom, left to innerLeft)) {
        drawLine(FRAME_LINE.copy(alpha = 0.45f), outer, inner, line)
    }
    // Light catching the outer edge of the near boards.
    drawLine(FRAME_HIGHLIGHT, left, bottom, line * 1.4f, StrokeCap.Round)
    drawLine(FRAME_HIGHLIGHT, bottom, right, line * 1.4f, StrokeCap.Round)
    drawLine(FRAME_LINE.copy(alpha = 0.4f), top, right, line)
    drawLine(FRAME_LINE.copy(alpha = 0.4f), top, left, line)
}

// Pixel-art water. Each tile is cut into WATER_PIXELS x WATER_PIXELS little diamonds, coloured
// from a four-step palette: mid blue as the base, diagonal bands of light blue with white
// crests for the ripples, and scattered dark pixels for depth. The pattern is a fixed function
// of the pixel's position, so neighbouring tiles line up into one continuous stream.
private const val WATER_PIXELS = 8
private const val WATER_PHASES = 6

/** The water counter wraps here: a multiple of every frame count in use, so no loop ever jumps. */
private const val WATER_TICK_CYCLE = 12 * WATER_PHASES
private val WATER_DEEP = Color(0xFF2B6CA3)
private val WATER_MID = Color(0xFF3F8CCB)
private val WATER_LIGHT = Color(0xFF6BB5E6)
private val WATER_CREST = Color(0xFFD6F0FF)
private val WATER_EDGE = Color(0xFF1E4F7A)

/**
 * @param phase 0 until [WATER_PHASES]; each step moves the ripples one pixel downstream.
 * @param isWater whether the given cell is water too; banks are only drawn against land.
 */
private fun DrawScope.drawWaterTile(
    transform: IsometricTransform,
    x: Int,
    y: Int,
    phase: Int,
    isWater: (x: Int, y: Int) -> Boolean = { _, _ -> false },
) {
    val outline = transform.tilePath(x, y)
    drawPath(outline, WATER_MID)
    val step = 1f / WATER_PIXELS
    for (i in 0 until WATER_PIXELS) for (j in 0 until WATER_PIXELS) {
        // Position along the garden in pixels, so the pattern carries across tile borders.
        val px = x * WATER_PIXELS + i
        val py = y * WATER_PIXELS + j
        val band = Math.floorMod(px + py + phase, WATER_PHASES)
        val speckle = Math.floorMod(px * 7 + py * 13, 11)
        val color = when {
            band == 0 && speckle % 3 == 0 -> WATER_CREST
            band == 0 || (band == 1 && speckle % 2 == 0) -> WATER_LIGHT
            speckle == 0 || speckle == 5 -> WATER_DEEP
            else -> continue
        }
        val x0 = x + i * step
        val y0 = y + j * step
        drawPath(
            quad(
                transform.worldToScreen(x0, y0),
                transform.worldToScreen(x0 + step, y0),
                transform.worldToScreen(x0 + step, y0 + step),
                transform.worldToScreen(x0, y0 + step),
            ),
            color,
        )
    }
    // A dark bank line keeps the water from bleeding into the lawn. Sides shared with another
    // water tile get none, so a row of tiles reads as one stream.
    val bank = transform.tileWidth * 0.025f
    val top = transform.worldToScreen(x.toFloat(), y.toFloat())
    val right = transform.worldToScreen(x + 1f, y.toFloat())
    val bottom = transform.worldToScreen(x + 1f, y + 1f)
    val left = transform.worldToScreen(x.toFloat(), y + 1f)
    if (!isWater(x, y - 1)) drawLine(WATER_EDGE, top, right, bank, StrokeCap.Round)
    if (!isWater(x + 1, y)) drawLine(WATER_EDGE, right, bottom, bank, StrokeCap.Round)
    if (!isWater(x, y + 1)) drawLine(WATER_EDGE, bottom, left, bank, StrokeCap.Round)
    if (!isWater(x - 1, y)) drawLine(WATER_EDGE, left, top, bank, StrokeCap.Round)
}

private fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

// --- Layers 2-5: placeholder sprites ----------------------------------------------------------
// Procedural stand-ins until real artwork exists. Everything is sized relative to the on-screen
// tile width so it follows the zoom.

private val STEM_GREEN = Color(0xFF388E3C)
private val LEAF_GREEN = Color(0xFF66BB6A)
private val WITHERED_BROWN = Color(0xFF8D6E63)
private val BLOOM_COLORS = listOf(
    Color(0xFFE57373), Color(0xFFF06292), Color(0xFFFFB74D), Color(0xFFFFF176), Color(0xFFBA68C8),
)


private val DECK_BOARD = Color(0xFFC79A63)
private val DECK_BOARD_ALT = Color(0xFFBB8C55)
private val DECK_GAP = Color(0xFF7A5230)
private val DECK_NAIL = Color(0xFF5D3A1A)
private const val DECK_BOARDS = 4

/**
 * A tile of wooden decking: four boards running across it, alternately a shade lighter and
 * darker, with dark gaps between them and a nail at either end of each.
 */
private fun DrawScope.drawWoodDeck(transform: IsometricTransform, x: Int, y: Int) {
    drawPath(transform.tilePath(x, y), DECK_GAP)
    val gap = 0.025f
    for (i in 0 until DECK_BOARDS) {
        val y0 = y + i / DECK_BOARDS.toFloat() + gap / 2f
        val y1 = y + (i + 1) / DECK_BOARDS.toFloat() - gap / 2f
        drawPath(
            quad(
                transform.worldToScreen(x + gap, y0),
                transform.worldToScreen(x + 1 - gap, y0),
                transform.worldToScreen(x + 1 - gap, y1),
                transform.worldToScreen(x + gap, y1),
            ),
            if ((i + x + y) % 2 == 0) DECK_BOARD else DECK_BOARD_ALT,
        )
        val middle = (y0 + y1) / 2f
        val nail = transform.tileWidth * 0.008f
        drawCircle(DECK_NAIL, nail, transform.worldToScreen(x + 0.1f, middle))
        drawCircle(DECK_NAIL, nail, transform.worldToScreen(x + 0.9f, middle))
    }
}

private val FLOWER_PETALS = listOf(Color(0xFFFFFFFF), Color(0xFFFFE082), Color(0xFFF8BBD0), Color(0xFFB39DDB))
private val FLOWER_HEART = Color(0xFFFFB300)
private val FLOWER_STEM = Color(0xFF5E8C3A)

/** A scatter of small wild flowers over the tile: a short stem, four petals and a yellow heart each. */
private fun DrawScope.drawWildFlowers(obj: GridObject, base: Offset, unit: Float) {
    var state = obj.gridX * 73856093 xor obj.gridY * 19349663 xor 0x5F3759DF
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7FFF) / 32767f
    }
    repeat(7) { i ->
        // Spread over the tile's diamond: wide across, half as deep.
        val across = (next() - 0.5f) * 0.62f
        val deep = (next() - 0.5f) * 0.3f * (1f - kotlin.math.abs(across) * 1.4f)
        val foot = base + Offset(unit * across, unit * deep)
        val head = foot + Offset(0f, -unit * (0.035f + next() * 0.03f))
        drawLine(FLOWER_STEM, foot, head, unit * 0.011f, StrokeCap.Round)
        val petal = unit * 0.017f
        val colour = FLOWER_PETALS[(i + obj.gridX + obj.gridY) % FLOWER_PETALS.size]
        for ((dx, dy) in listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)) {
            drawCircle(colour, petal, head + Offset(dx * petal * 0.9f, dy * petal * 0.9f))
        }
        drawCircle(FLOWER_HEART, petal * 0.6f, head)
    }
}

private val BENCH_WOOD = Color(0xFFB98552)
private val BENCH_WOOD_DARK = Color(0xFF8A5F36)
private val BENCH_LEG = Color(0xFF5D3A1A)

/** A garden bench: a seat of two planks on four legs, and a back of two more. */
private fun DrawScope.drawBench(base: Offset, unit: Float) {
    val halfWidth = unit * 0.24f
    val seatHeight = unit * 0.1f
    val depth = Offset(unit * 0.07f, unit * 0.035f) // how far the front edge is from the back edge
    val stroke = unit * 0.02f
    val backLeft = base + Offset(-halfWidth, -seatHeight) - depth / 2f
    val backRight = base + Offset(halfWidth, -seatHeight) - depth / 2f
    val frontLeft = backLeft + depth
    val frontRight = backRight + depth
    // Legs first, back pair then front pair.
    for (top in listOf(backLeft, backRight, frontLeft, frontRight)) {
        drawLine(BENCH_LEG, top, top + Offset(0f, seatHeight), stroke, StrokeCap.Round)
    }
    // The back: two posts and two slats.
    val rise = Offset(0f, -unit * 0.13f)
    drawLine(BENCH_LEG, backLeft, backLeft + rise, stroke, StrokeCap.Round)
    drawLine(BENCH_LEG, backRight, backRight + rise, stroke, StrokeCap.Round)
    drawLine(BENCH_WOOD_DARK, backLeft + rise * 0.55f, backRight + rise * 0.55f, stroke * 1.5f, StrokeCap.Round)
    drawLine(BENCH_WOOD, backLeft + rise * 0.95f, backRight + rise * 0.95f, stroke * 1.5f, StrokeCap.Round)
    // The seat.
    drawPath(quad(backLeft, backRight, frontRight, frontLeft), BENCH_WOOD)
    drawLine(BENCH_WOOD_DARK, (backLeft + frontLeft) / 2f, (backRight + frontRight) / 2f, stroke * 0.5f)
    drawLine(BENCH_LEG, frontLeft, frontRight, stroke * 0.6f, StrokeCap.Round)
}

private val LANTERN_STONE = Color(0xFFB7B3A8)
private val LANTERN_STONE_DARK = Color(0xFF8E8A80)
private val LANTERN_MOSS = Color(0xFF8DAE6A)
private val LANTERN_LIGHT = Color(0xFFFFE9A8)

/**
 * A stone lantern: a wide foot, a post, the lamp chamber with its lit window, and a broad cap
 * with a knob on top. A warm glow hangs round the window.
 */
private val POND_WATER = Color(0xFF8FC3D9)
private val POND_WATER_DEEP = Color(0xFF5E9BB5)
private val POND_STONE = Color(0xFFD8D3C8)

private val POND_GLINT = Color(0xFFFFFFFF)
private val POND_RING = Color(0xFFEAF6FB)

/**
 * The pond's water moving ([PondWater]): glints of light and rings spreading over it, kept to
 * the open water inside the logs. Laid flat on the water, like everything on the ground.
 */
private fun DrawScope.drawPondWater(transform: IsometricTransform, x: Int, y: Int, frame: PondWater.Frame) {
    val from = PondWater.WATER_FROM
    val span = PondWater.WATER_TO - PondWater.WATER_FROM
    fun at(u: Float, v: Float) = transform.worldToScreen(x + from + span * u, y + from + span * v)
    val water = quad(at(0f, 0f), at(1f, 0f), at(1f, 1f), at(0f, 1f))
    // A width on the water, in screen pixels: the water is [span] of a cell across.
    val across = transform.tileWidth * span
    clipPath(water) {
        for (ripple in frame.ripples) {
            if (ripple.alpha <= 0f) continue
            val center = at(ripple.u, ripple.v)
            val radius = across * ripple.radius
            scale(scaleX = 1f, scaleY = 0.5f, pivot = center) {
                drawCircle(POND_RING.copy(alpha = ripple.alpha), radius, center, style = Stroke(width = across * 0.012f))
            }
        }
        for (glint in frame.glints) {
            if (glint.alpha <= 0.01f) continue
            val center = at(glint.u, glint.v)
            val radius = across * glint.size
            scale(scaleX = 1f, scaleY = 0.5f, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(POND_GLINT.copy(alpha = 0.85f * glint.alpha), POND_GLINT.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}

/** The pond when its picture cannot be read: a pool of water in a ring of pale stones. */
private fun DrawScope.drawPondInCode(transform: IsometricTransform, x: Int, y: Int) {
    fun diamond(inset: Float) = quad(
        transform.worldToScreen(x + inset, y + inset),
        transform.worldToScreen(x + 1 - inset, y + inset),
        transform.worldToScreen(x + 1 - inset, y + 1 - inset),
        transform.worldToScreen(x + inset, y + 1 - inset),
    )
    drawPath(diamond(0.04f), POND_STONE)
    drawPath(diamond(0.16f), POND_WATER_DEEP)
    drawPath(diamond(0.22f), POND_WATER)
}

private val SPRINKLER_METAL = Color(0xFF9AA5AD)
private val SPRINKLER_METAL_DARK = Color(0xFF6D7880)
private val SPRINKLER_BRASS = Color(0xFFC9A44C)
private val SPRINKLER_WATER = Color(0xFFB3E0F7)

/** The sprinkler: a short post on a round foot, with a brass head; a little blue light while it runs. */
private fun DrawScope.drawSprinkler(base: Offset, unit: Float, running: Boolean) {
    drawOval(SPRINKLER_METAL_DARK, base - Offset(unit * 0.09f, unit * 0.035f), Size(unit * 0.18f, unit * 0.07f))
    val top = base - Offset(0f, unit * 0.2f)
    drawLine(SPRINKLER_METAL, base - Offset(0f, unit * 0.02f), top, unit * 0.035f, StrokeCap.Round)
    drawOval(SPRINKLER_BRASS, top - Offset(unit * 0.06f, unit * 0.03f), Size(unit * 0.12f, unit * 0.05f))
    drawCircle(if (running) SPRINKLER_WATER else SPRINKLER_METAL_DARK, unit * 0.015f, top - Offset(0f, unit * 0.025f))
}

/**
 * The water of a running sprinkler, from [SprinklerSpray]: drops thrown out along its turning
 * jets over the ring of cells it waters, fading as they land.
 */
private fun DrawScope.drawSprinklerSpray(base: Offset, unit: Float, frame: SprinklerSpray.Frame) {
    val head = base - Offset(0f, unit * 0.23f)
    for (drop in frame.drops) {
        // Out over the ground in the garden's flattened view, rising and falling on its way.
        val out = drop.reach * unit * 0.9f
        val x = head.x + kotlin.math.cos(drop.angle) * out
        val y = head.y + kotlin.math.sin(drop.angle) * out * 0.5f - kotlin.math.sin(drop.reach * Math.PI.toFloat()) * unit * 0.18f + drop.reach * unit * 0.2f
        drawCircle(SPRINKLER_WATER.copy(alpha = 0.85f * drop.alpha), unit * (0.018f + 0.01f * (1f - drop.reach)), Offset(x, y))
    }
}

private fun DrawScope.drawLantern(base: Offset, unit: Float) {
    fun slab(centre: Offset, width: Float, height: Float, colour: Color) =
        drawRoundRect(colour, centre - Offset(width / 2f, height), Size(width, height), CornerRadius(height * 0.25f, height * 0.25f))

    val foot = unit * 0.05f
    val post = unit * 0.11f
    val chamber = unit * 0.1f
    slab(base, unit * 0.2f, foot, LANTERN_STONE_DARK)
    slab(base + Offset(0f, -foot), unit * 0.08f, post, LANTERN_STONE)
    val chamberBase = base + Offset(0f, -foot - post)
    slab(chamberBase, unit * 0.15f, chamber, LANTERN_STONE)
    // The glow, then the window it comes from.
    val window = chamberBase + Offset(0f, -chamber / 2f)
    drawCircle(
        brush = Brush.radialGradient(listOf(LANTERN_LIGHT.copy(alpha = 0.55f), LANTERN_LIGHT.copy(alpha = 0f)), center = window, radius = unit * 0.16f),
        radius = unit * 0.16f,
        center = window,
    )
    drawRoundRect(LANTERN_LIGHT, window - Offset(unit * 0.035f, chamber * 0.3f), Size(unit * 0.07f, chamber * 0.6f), CornerRadius(unit * 0.01f, unit * 0.01f))
    // The cap, wider than everything under it, with moss on one side and a knob on top.
    val capBase = chamberBase + Offset(0f, -chamber)
    val cap = Path().apply {
        moveTo(capBase.x - unit * 0.13f, capBase.y)
        lineTo(capBase.x + unit * 0.13f, capBase.y)
        lineTo(capBase.x + unit * 0.04f, capBase.y - unit * 0.06f)
        lineTo(capBase.x - unit * 0.04f, capBase.y - unit * 0.06f)
        close()
    }
    drawPath(cap, LANTERN_STONE_DARK)
    drawCircle(LANTERN_MOSS, unit * 0.022f, capBase + Offset(-unit * 0.07f, -unit * 0.018f))
    drawCircle(LANTERN_STONE, unit * 0.02f, capBase + Offset(0f, -unit * 0.075f))
}

private val CRATE_WOOD_LEFT = Color(0xFFC89A5E)
private val CRATE_WOOD_RIGHT = Color(0xFFA9773F)
private val CRATE_WOOD_TOP = Color(0xFFDDB57A)
private val CRATE_EDGE = Color(0xFF5D3A1A)
private val CRATE_BAND = Color(0xFF8BC34A)

/**
 * The starter crate: a small wooden box seen corner-on, its sides made of planks, a dark edge
 * round it, and a green band over the lid with two leaves for a bow.
 */
private fun DrawScope.drawStarterCrate(base: Offset, unit: Float) {
    val half = unit * 0.17f // half the box's width on screen
    val rise = unit * 0.15f // how tall it stands
    val left = base + Offset(-half, -half / 2f)
    val front = base
    val right = base + Offset(half, -half / 2f)
    val back = base + Offset(0f, -half)
    val up = Offset(0f, -rise)
    val stroke = unit * 0.012f

    drawPath(quad(left, front, front + up, left + up), CRATE_WOOD_LEFT)
    drawPath(quad(front, right, right + up, front + up), CRATE_WOOD_RIGHT)
    drawPath(quad(left + up, front + up, right + up, back + up), CRATE_WOOD_TOP)
    // Plank seams across both sides.
    for (level in floatArrayOf(0.34f, 0.67f)) {
        drawLine(CRATE_EDGE.copy(alpha = 0.45f), left + up * level, front + up * level, stroke * 0.8f)
        drawLine(CRATE_EDGE.copy(alpha = 0.45f), front + up * level, right + up * level, stroke * 0.8f)
    }
    // The band over the lid, from the middle of the front-left edge to the middle of the back-right one.
    val bandFrom = (left + front) / 2f + up
    val bandTo = (right + back) / 2f + up
    drawLine(CRATE_BAND, bandFrom, bandTo, stroke * 2.4f, StrokeCap.Round)
    drawLine(CRATE_BAND, (left + front) / 2f + up * 0.05f, bandFrom, stroke * 2.4f, StrokeCap.Round)
    // The outline.
    for ((a, b) in listOf(left to front, front to right, left to left + up, front to front + up, right to right + up)) {
        drawLine(CRATE_EDGE, a, b, stroke, StrokeCap.Round)
    }
    drawPath(quad(left + up, front + up, right + up, back + up), CRATE_EDGE, style = Stroke(width = stroke))
    // Two leaves for a bow, in the middle of the lid.
    val knot = (bandFrom + bandTo) / 2f
    drawOval(CRATE_BAND, knot + Offset(-unit * 0.005f, -unit * 0.05f), Size(unit * 0.06f, unit * 0.035f))
    drawOval(CRATE_BAND, knot + Offset(-unit * 0.055f, -unit * 0.045f), Size(unit * 0.05f, unit * 0.03f))
}

private fun DrawScope.drawGroundCover(obj: GridObject, base: Offset, unit: Float) {
    if (obj.objectId == GroundCover.FLOWERS.objectId) {
        drawWildFlowers(obj, base, unit)
        return
    }
    val color = if (obj.objectId.contains("grass")) Color(0xFF558B2F) else Color(0xFFBDBDBD)
    val r = unit * 0.035f
    drawCircle(color, r * 1.3f, base + Offset(-unit * 0.12f, unit * 0.01f))
    drawCircle(color, r, base + Offset(unit * 0.04f, -unit * 0.05f))
    drawCircle(color, r * 1.1f, base + Offset(unit * 0.13f, unit * 0.04f))
}

private fun DrawScope.drawPlant(obj: GridObject, base: Offset, unit: Float) {
    val withered = obj.status == PlantStatus.WITHERED
    // The crops with no sprites yet: each is drawn in its own shape, or else as a plain flower.
    val species = PlantSpecies.fromObjectId(obj.objectId)
    if (species != null && drawCropInCode(species, base, unit, obj.growthProgress, withered)) return
    val scale = plantScale(obj)
    val top = base - Offset(0f, unit * 0.55f * scale)
    val stemColor = if (withered) WITHERED_BROWN else STEM_GREEN

    drawLine(stemColor, base, top, strokeWidth = unit * 0.035f, cap = StrokeCap.Round)

    // One leaf on the right only, so a flipped plant is visibly mirrored.
    val leafSize = Size(unit * 0.16f * scale, unit * 0.08f * scale)
    val leafAnchor = base - Offset(0f, unit * 0.22f * scale)
    drawOval(
        color = if (withered) WITHERED_BROWN else LEAF_GREEN,
        topLeft = leafAnchor - Offset(0f, leafSize.height / 2f),
        size = leafSize,
    )

    val bloomColor = if (withered) Color(0xFFA1887F) else BLOOM_COLORS[obj.objectId.hashCode().mod(BLOOM_COLORS.size)]
    val bloomRadius = unit * 0.12f * scale * (if (withered) 0.7f else 1f)
    drawCircle(bloomColor, bloomRadius, top)
    if (!withered) drawCircle(Color(0xFFFFF8E1), bloomRadius * 0.35f, top)
}

private fun DrawScope.drawDecoration(base: Offset, unit: Float) {
    val width = unit * 0.36f
    val height = unit * 0.24f
    val topLeft = base - Offset(width / 2f, height * 0.85f)
    drawOval(Color(0xFF9E9E9E), topLeft, Size(width, height))
    drawOval(Color(0xFFBDBDBD), topLeft + Offset(width * 0.12f, height * 0.08f), Size(width * 0.5f, height * 0.4f))
}

private fun DrawScope.drawStatusBubble(obj: GridObject, base: Offset, unit: Float) {
    val center = TapTargets.statusBubbleCentre(base, obj, unit)
    val radius = unit * 0.085f
    val icon = if (obj.status == PlantStatus.THIRSTY) Color(0xFF42A5F5) else Color(0xFFFFA726)
    drawCircle(Color.White, radius, center)
    drawCircle(icon, radius * 0.55f, center)
}

/** Shape drawn for an object whose sprite is not in the atlas. */
private fun DrawScope.drawPlaceholder(obj: GridObject, base: Offset, unit: Float) {
    if (obj.objectId.startsWith(GROUND_OBJECT_ID_PREFIX)) {
        drawGroundCover(obj, base, unit)
        return
    }
    // isFlipped mirrors the shape around the vertical line through its tile center.
    scale(scaleX = if (obj.isFlipped) -1f else 1f, scaleY = 1f, pivot = base) {
        when {
            obj.isPlant -> drawPlant(obj, base, unit)
            obj.objectId == Decoration.BENCH.objectId -> drawBench(base, unit)
            obj.objectId == Decoration.LANTERN.objectId -> drawLantern(base, unit)
            obj.isSprinkler -> drawSprinkler(base, unit, obj.isOn)
            obj.isStarterCrate -> drawStarterCrate(base, unit)
            else -> drawDecoration(base, unit)
        }
    }
}

// --- Structures -------------------------------------------------------------------------------

private val HUT_WALL_LEFT = Color(0xFFD7B98E)
private val HUT_WALL_RIGHT = Color(0xFFB8956A)
private val HUT_ROOF_LIGHT = Color(0xFFC0563F)
private val HUT_ROOF_DARK = Color(0xFF9C3F2E)
private val WELL_WALL_LEFT = Color(0xFFBDBDBD)
private val WELL_WALL_RIGHT = Color(0xFF9E9E9E)
private val WELL_RIM = Color(0xFFE0E0E0)
private val WELL_WATER = Color(0xFF3F8FC4)

/** How far the walls stand back from the edge of the footprint, in tiles. */
private const val STRUCTURE_INSET = 0.12f

/**
 * How much of a code-drawn building's height is wall, the rest being the hut's roof; and how
 * high the well's rim stands, as a part of its height. Taken from [StructureData.heightSteps],
 * the height that decides what a building hides ([Occlusion]), so the stand-in is never taller
 * or shorter than what the garden makes see-through behind it.
 */
internal const val HUT_WALL_SHARE = 4f / 7f
internal const val WELL_RIM_SHARE = 0.75f

/**
 * Placeholder for a structure without a sprite: a box standing on its whole footprint, with a
 * pitched roof for the hut and an open, water-filled top for the well. Heights are in height
 * steps, like [GridObject.heightLevel].
 */
private fun DrawScope.drawStructure(transform: IsometricTransform, obj: GridObject, structure: StructureData) {
    val x0 = obj.gridX + STRUCTURE_INSET
    val y0 = obj.gridY + STRUCTURE_INSET
    val x1 = obj.gridX + structure.footprint.width - STRUCTURE_INSET
    val y1 = obj.gridY + structure.footprint.height - STRUCTURE_INSET
    val ground = obj.heightLevel.toFloat()
    if (structure == StructureData.COTTAGE) {
        drawCottage(transform, obj)
        return
    }
    // Without its picture, the windmill is drawn like the hut: a box with a pointed roof.
    val isHut = structure == StructureData.HUT || structure == StructureData.WINDMILL
    val top = ground + structure.heightSteps * if (isHut) HUT_WALL_SHARE else WELL_RIM_SHARE

    // Corners of the box, named by where they appear on screen.
    val leftLow = transform.worldToScreen(x0, y1, ground)
    val frontLow = transform.worldToScreen(x1, y1, ground)
    val rightLow = transform.worldToScreen(x1, y0, ground)
    val back = transform.worldToScreen(x0, y0, top)
    val left = transform.worldToScreen(x0, y1, top)
    val front = transform.worldToScreen(x1, y1, top)
    val right = transform.worldToScreen(x1, y0, top)

    drawPath(quad(leftLow, frontLow, front, left), if (isHut) HUT_WALL_LEFT else WELL_WALL_LEFT)
    drawPath(quad(frontLow, rightLow, right, front), if (isHut) HUT_WALL_RIGHT else WELL_WALL_RIGHT)

    if (isHut) {
        val apex = transform.worldToScreen((x0 + x1) / 2f, (y0 + y1) / 2f, ground + structure.heightSteps)
        // Back slopes first, then the two facing the viewer.
        drawPath(triangle(back, left, apex), HUT_ROOF_DARK)
        drawPath(triangle(back, right, apex), HUT_ROOF_DARK)
        drawPath(triangle(left, front, apex), HUT_ROOF_LIGHT)
        drawPath(triangle(front, right, apex), HUT_ROOF_DARK)
    } else {
        drawPath(quad(back, right, front, left), WELL_RIM)
        val rim = 0.14f
        drawPath(
            quad(
                transform.worldToScreen(x0 + rim, y0 + rim, top),
                transform.worldToScreen(x1 - rim, y0 + rim, top),
                transform.worldToScreen(x1 - rim, y1 - rim, top),
                transform.worldToScreen(x0 + rim, y1 - rim, top),
            ),
            WELL_WATER,
        )
    }
}

private val COTTAGE_WALL_LEFT = Color(0xFFF3E6CC)
private val COTTAGE_WALL_RIGHT = Color(0xFFD9C6A2)
private val COTTAGE_TIMBER = Color(0xFF8A5F36)
private val COTTAGE_ROOF_LIGHT = Color(0xFF6F9E8A)
private val COTTAGE_ROOF_DARK = Color(0xFF507A68)
private val COTTAGE_DOOR = Color(0xFF7A4A26)
private val COTTAGE_WINDOW = Color(0xFFFFE9A8)
private val COTTAGE_CHIMNEY = Color(0xFFB0695A)

/**
 * The cottage, the symbol building, drawn without a sprite: cream walls on its 3 x 3 footprint
 * with timber corners, a door and two lit windows on the side facing the viewer, a window on the
 * other, a steep green gabled roof running along gridX, and a brick chimney.
 */
private fun DrawScope.drawCottage(transform: IsometricTransform, obj: GridObject) {
    val footprint = StructureData.COTTAGE.footprint
    val inset = STRUCTURE_INSET
    val x0 = obj.gridX + inset
    val y0 = obj.gridY + inset
    val x1 = obj.gridX + footprint.width - inset
    val y1 = obj.gridY + footprint.height - inset
    val ground = obj.heightLevel.toFloat()
    val eaves = ground + 4.5f
    val ridge = eaves + 3.7f
    fun p(x: Float, y: Float, z: Float) = transform.worldToScreen(x, y, z)
    val stroke = transform.tileWidth * 0.012f

    // Walls: the left face looks along +y (towards the viewer's lower left), the right face along +x.
    drawPath(quad(p(x0, y1, ground), p(x1, y1, ground), p(x1, y1, eaves), p(x0, y1, eaves)), COTTAGE_WALL_LEFT)
    drawPath(quad(p(x1, y1, ground), p(x1, y0, ground), p(x1, y0, eaves), p(x1, y1, eaves)), COTTAGE_WALL_RIGHT)
    // The gable over the right face: the roof's ridge runs along x, so its end shows on this side.
    val midY = (y0 + y1) / 2f
    drawPath(triangle(p(x1, y1, eaves), p(x1, y0, eaves), p(x1, midY, ridge)), COTTAGE_WALL_RIGHT)
    // Timber at the corners and along the eaves.
    for (corner in listOf(p(x0, y1, ground) to p(x0, y1, eaves), p(x1, y1, ground) to p(x1, y1, eaves), p(x1, y0, ground) to p(x1, y0, eaves))) {
        drawLine(COTTAGE_TIMBER, corner.first, corner.second, stroke * 1.6f, StrokeCap.Round)
    }
    drawLine(COTTAGE_TIMBER, p(x0, y1, eaves), p(x1, y1, eaves), stroke * 1.4f)
    drawLine(COTTAGE_TIMBER, p(x1, y1, eaves), p(x1, y0, eaves), stroke * 1.4f)

    // Door in the middle of the front face, a window on each side of it.
    val doorX = (x0 + x1) / 2f
    drawPath(quad(p(doorX - 0.28f, y1, ground), p(doorX + 0.28f, y1, ground), p(doorX + 0.28f, y1, ground + 2.6f), p(doorX - 0.28f, y1, ground + 2.6f)), COTTAGE_DOOR)
    drawCircle(COTTAGE_WINDOW, stroke * 1.2f, p(doorX + 0.18f, y1, ground + 1.3f))
    for (wx in listOf(x0 + 0.45f, x1 - 0.85f)) {
        drawPath(quad(p(wx, y1, ground + 1.8f), p(wx + 0.4f, y1, ground + 1.8f), p(wx + 0.4f, y1, ground + 3.3f), p(wx, y1, ground + 3.3f)), COTTAGE_WINDOW)
        drawLine(COTTAGE_TIMBER, p(wx + 0.2f, y1, ground + 1.8f), p(wx + 0.2f, y1, ground + 3.3f), stroke)
    }
    // A round window in the gable.
    drawCircle(COTTAGE_WINDOW, transform.tileWidth * 0.05f, p(x1, midY, eaves + 1.4f))

    // Roof: the back slope first, then the chimney standing out of it, then the front slope.
    val over = 0.12f // the eaves overhang the walls a little
    val ridgeA = p(x0 - over, midY, ridge)
    val ridgeB = p(x1 + over, midY, ridge)
    drawPath(quad(p(x0 - over, y0 - over, eaves - 0.3f), p(x1 + over, y0 - over, eaves - 0.3f), ridgeB, ridgeA), COTTAGE_ROOF_DARK)
    val chimneyX = x0 + 0.7f
    val chimneyY = y0 + 0.6f
    drawPath(
        quad(p(chimneyX, chimneyY + 0.35f, eaves + 2f), p(chimneyX + 0.35f, chimneyY + 0.35f, eaves + 2f), p(chimneyX + 0.35f, chimneyY + 0.35f, ridge + 1.6f), p(chimneyX, chimneyY + 0.35f, ridge + 1.6f)),
        COTTAGE_CHIMNEY,
    )
    drawPath(quad(p(x0 - over, y1 + over, eaves - 0.3f), p(x1 + over, y1 + over, eaves - 0.3f), ridgeB, ridgeA), COTTAGE_ROOF_LIGHT)
    drawLine(COTTAGE_ROOF_DARK, ridgeA, ridgeB, stroke * 1.5f, StrokeCap.Round)
}
