package com.studioguu.miniaturegarden.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.studioguu.miniaturegarden.data.asset.GroundTile
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TileType
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.Daylight
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import com.studioguu.miniaturegarden.ui.render.IsometricMetrics
import com.studioguu.miniaturegarden.ui.render.IsometricTransform
import com.studioguu.miniaturegarden.ui.render.LayerPipeline
import com.studioguu.miniaturegarden.ui.render.RenderLayer
import com.studioguu.miniaturegarden.ui.render.RiverAutotile
import com.studioguu.miniaturegarden.ui.render.SunLight
import com.studioguu.miniaturegarden.ui.render.TileCoord
import com.studioguu.miniaturegarden.ui.render.TilePicker
import com.studioguu.miniaturegarden.ui.render.Wind
import com.studioguu.miniaturegarden.ui.render.drawGroundTexture
import com.studioguu.miniaturegarden.ui.render.drawRiverTile
import com.studioguu.miniaturegarden.ui.render.drawSprite
import com.studioguu.miniaturegarden.ui.render.drawSpriteShadow
import com.studioguu.miniaturegarden.ui.render.drawSwayingSprite
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TILE_WIDTH = 128.dp
private val TILE_HEIGHT = 64.dp
private val HEIGHT_STEP = 16.dp

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
 * @param onTileTapped receives the tapped tile, or null for a tap outside the grid.
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
) {
    val density = LocalDensity.current
    val metrics = remember(density) {
        with(density) {
            IsometricMetrics(TILE_WIDTH.toPx(), TILE_HEIGHT.toPx(), HEIGHT_STEP.toPx(), GridObject.GRID_SIZE)
        }
    }
    // Null until the first layout pass tells us how far to zoom out to fit the grid. Saved, so a
    // rotation or a return from the background comes back to the same view.
    var camera by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<GardenCamera?>(null) }
    val atlasImage = remember(atlas) { atlas.bitmap?.asImageBitmap() }
    val pipeline = remember { LayerPipeline() }
    val waterTick by rememberWaterTick(running = gridObjects.any { it.isTerrain })
    val grid = remember(gardenMap, gridObjects) { GardenGrid(gardenMap, gridObjects) }
    val currentOnTileTapped by rememberUpdatedState(onTileTapped)
    // The wind's clock. It is read while drawing only, so a new value redraws the garden without
    // recomposing anything. A breeze is slow: a modest frame rate is plenty and saves battery.
    val windMillis by produceState(0L) {
        while (true) {
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
    val lastSeen = remember { arrayOf(gridObjects) }
    val ghostScope = rememberCoroutineScope()
    LaunchedEffect(gridObjects) {
        val present = gridObjects.mapTo(HashSet()) { it.instanceId }
        val gone = lastSeen[0].filter { it.instanceId !in present }
        lastSeen[0] = gridObjects
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
                camera = camera?.clamped(metrics) ?: GardenCamera.fitting(size.toSize(), metrics)
            }
            .pointerInput(metrics) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera = camera?.transformedBy(centroid, pan, zoom, size.toSize(), metrics)
                }
            }
            .pointerInput(metrics) {
                detectTapGestures { position ->
                    val transform = camera?.toTransform(metrics, size.toSize()) ?: return@detectTapGestures
                    currentOnTileTapped(TilePicker.pick(position, transform, metrics.gridSize))
                }
            }
    ) {
        val transform = camera?.toTransform(metrics, size) ?: return@Canvas
        val spriteScale = transform.tileWidth / atlas.manifest.tileWidth
        val ambience = Ambience(Daylight.at(hourOfDay, weather), weather, windMillis)

        pipeline.submit(RenderLayer.BASE_TERRAIN) {
            drawTerrain(transform, gardenMap, framed = true, ambience) { tile, center ->
                val sprite = atlas[tile.assetId]
                if (sprite != null && atlasImage != null) drawSprite(atlasImage, sprite, center, spriteScale)
                sprite != null
            }
        }
        if (showGrid) {
            pipeline.submit(RenderLayer.GRID_OVERLAY) { drawIsometricGrid(transform, metrics.gridSize) }
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
        submitObjects(pipeline, gridObjects, grid, transform, atlas, atlasImage, terrain, waterTick, ambience)
        for (ghost in ghosts) {
            submitObjects(
                pipeline, listOf(ghost.gridObject), grid, transform, atlas, atlasImage, terrain, waterTick, ambience,
                vanish = ghost.progress.value,
            )
        }
        pipeline.render(this)
    }
}

/** How long the garden waits between two frames of wind. */
private const val WIND_FRAME_MILLIS = 50L

/** Local time as hours since midnight, minutes included as a fraction. */
private fun currentHourOfDay(): Float = LocalTime.now().let { it.hour + it.minute / 60f }

/**
 * The light and the air a scene is drawn in.
 *
 * @property windMillis the wind's clock, or null for still air (the pictures in the encyclopedia).
 */
private class Ambience(val light: SunLight, val weather: WeatherType, val windMillis: Long?) {
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
) {
    val spriteScale = transform.tileWidth / atlas.manifest.tileWidth
    // Everything is queued through here, so that objects on their way out can be drawn fading.
    fun submit(obj: GridObject, layer: RenderLayer, depth: Float, draw: DrawScope.() -> Unit) {
        if (vanish == null) {
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
                    drawRiverTile(autotile, mask, waterTick, transform, obj.gridX, obj.gridY)
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
            submit(obj, RenderLayer.BASE_TERRAIN, 1f) {
                if (obj.isTilledSoil) {
                    drawTilledSoil(transform, obj.gridX, obj.gridY, composted = obj.isComposted)
                } else if (paving != null) {
                    drawGroundTexture(paving, transform, obj.gridX, obj.gridY)
                } else {
                    drawPath(transform.tilePath(obj.gridX, obj.gridY), PAVING_FALLBACK)
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
        // Two shadows tie a thing to the ground: a dark, tight one right under its foot, which
        // is there in any light, and the one the sun throws, which turns with the time of day.
        if (!isGroundCover) {
            submit(obj, RenderLayer.SHADOWS, depth) {
                if (sprite != null && atlasImage != null) {
                    drawContactShadow(base, min(sprite.width * spriteScale * 0.62f, unit * 0.7f))
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
            } else if (structure != null) {
                drawStructure(transform, obj, structure)
            } else {
                drawPlaceholder(obj, base, unit)
            }
            // What it stands in comes up around its foot: blades of grass on open ground, crumbs
            // of earth on a bed. Paving is hard and shows nothing.
            if (!isGroundCover && structure == null) {
                val floor = grid.floorAt(obj.gridX, obj.gridY)
                val reach = if (sprite != null) min(sprite.width * spriteScale * 0.3f, unit * 0.3f) else unit * 0.16f
                when {
                    floor == null -> drawGrassAtFoot(base, unit, reach, sway, seed = obj.gridX * 31 + obj.gridY * 17)
                    floor.isTilledSoil -> drawEarthAtFoot(base, unit, reach, seed = obj.gridX * 31 + obj.gridY * 17)
                }
            }
        }
        if (obj.isPlant && (obj.status == PlantStatus.THIRSTY || obj.status == PlantStatus.UNNOURISHED)) {
            submit(obj, RenderLayer.TOP_EFFECTS, depth) { drawStatusBubble(obj, base, unit) }
        }
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
                StructureData.fromObjectId(objectId) != null -> PreviewScene(2, 0.45f, listOf(at(0, 0)))
                else -> PreviewScene(1, 0.3f, listOf(at(0, 0)))
            }
        }
    }
}

/** The lawn of a preview sits on a slab of soil this thick, in tile heights (see drawTerrain). */
private const val PREVIEW_SOIL = 0.6f

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
    val pipeline = remember { LayerPipeline() }
    val waterTick by rememberWaterTick(running = scene.objects.any { it.isTerrain })

    Canvas(modifier) {
        val n = scene.gridSize
        // On screen the patch is n tiles wide and n half-tiles tall, with the soil under it and
        // the headroom above. Heights are in tile widths; a tile is half as tall as it is wide.
        val heightInTiles = scene.headroom + (n + PREVIEW_SOIL) / 2f
        val tileWidth = min(size.width / n, size.height / heightInTiles)
        val transform = IsometricTransform(
            tileWidth = tileWidth,
            tileHeight = tileWidth / 2f,
            heightStep = tileWidth * (HEIGHT_STEP / TILE_WIDTH),
            offsetX = size.width / 2f,
            offsetY = (size.height - heightInTiles * tileWidth) / 2f + scene.headroom * tileWidth,
        )
        val spriteScale = tileWidth / atlas.manifest.tileWidth
        pipeline.submit(RenderLayer.BASE_TERRAIN) {
            drawTerrain(transform, map, framed = false, Ambience.CALM) { tile, center ->
                val sprite = atlas[tile.assetId]
                if (sprite != null && atlasImage != null) drawSprite(atlasImage, sprite, center, spriteScale)
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
    drawTileSprite: DrawScope.(tile: GroundTile, center: Offset) -> Boolean,
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

    // x-then-y order draws every tile before the two neighbours in front of it.
    for (x in 0 until gridSize) for (y in 0 until gridSize) {
        val type = gardenMap.tileAt(x, y) ?: TileType.GRASS
        val tile = TileSetDefinition.groundTileFor(type, x, y)
        if (drawTileSprite(tile, transform.tileCenter(x, y))) continue
        when (tile) {
            GroundTile.WATER -> drawWaterTile(transform, x, y, phase = 0)
            GroundTile.GRASS -> drawGroundTile(transform, x, y, GRASS_LIGHT, ambience.sway(x + 0.5f, y + 0.5f))
            else -> drawGroundTile(transform, x, y, GRASS_DARK, ambience.sway(x + 0.5f, y + 0.5f))
        }
    }
    if (framed) {
        // The frame's inner edge, drawn over the outermost tiles so the ground reads as set into it.
        val a = transform.worldToScreen(0f, 0f)
        val b = transform.worldToScreen(max, 0f)
        val c = transform.worldToScreen(max, max)
        val d = transform.worldToScreen(0f, max)
        val width = transform.tileWidth * 0.022f
        drawLine(FRAME_LINE.copy(alpha = 0.55f), a, b, width, StrokeCap.Round)
        drawLine(FRAME_LINE.copy(alpha = 0.55f), a, d, width, StrokeCap.Round)
        drawLine(FRAME_LINE.copy(alpha = 0.3f), b, c, width, StrokeCap.Round)
        drawLine(FRAME_LINE.copy(alpha = 0.3f), d, c, width, StrokeCap.Round)
    }
}

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
private const val PLANT_LEAN = 0.05f

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
    val height = (if (structure == StructureData.HUT) 5.5f else 1.8f) * transform.heightStep
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
private val COMPOST_LEAF = Color(0xFF8FB25A)
private val COMPOST_STRAW = Color(0xFFE2C879)
private const val FURROWS = 4

/**
 * A tile the hoe has been over: dark, damp-looking earth with furrows running across it, set a
 * little in from the tile's edge so that a strip of grass still shows between two beds.
 * A [composted] bed is flecked with green and straw.
 */
private fun DrawScope.drawTilledSoil(transform: IsometricTransform, x: Int, y: Int, composted: Boolean) {
    val inset = 0.05f
    val x0 = x + inset
    val y0 = y + inset
    val x1 = x + 1 - inset
    val y1 = y + 1 - inset
    val bed = quad(
        transform.worldToScreen(x0, y0),
        transform.worldToScreen(x1, y0),
        transform.worldToScreen(x1, y1),
        transform.worldToScreen(x0, y1),
    )
    drawPath(bed, DIRT_EDGE)
    val lip = 0.03f
    drawPath(
        quad(
            transform.worldToScreen(x0 + lip, y0 + lip),
            transform.worldToScreen(x1 - lip, y0 + lip),
            transform.worldToScreen(x1 - lip, y1 - lip),
            transform.worldToScreen(x0 + lip, y1 - lip),
        ),
        DIRT_DARK,
    )
    // Furrows: a lit ridge with its shadow just behind it.
    val stroke = transform.tileWidth * 0.022f
    for (i in 0 until FURROWS) {
        val fy = y0 + (y1 - y0) * (i + 0.5f) / FURROWS
        val from = transform.worldToScreen(x0 + 0.08f, fy)
        val to = transform.worldToScreen(x1 - 0.08f, fy)
        val behind = Offset(0f, -stroke)
        drawLine(DIRT_EDGE, from + behind, to + behind, stroke, StrokeCap.Round)
        drawLine(DIRT_RIDGE, from, to, stroke, StrokeCap.Round)
    }
    // The edge of a bed is not ruled with a pen: clods lie on it and the grass grows over it.
    var edge = x * 19349663 xor y * 83492791
    fun nextEdge(): Float {
        edge = edge * 1103515245 + 12345
        return ((edge ushr 16) and 0x7FFF) / 32767f
    }
    val unit = transform.tileWidth
    repeat(10) { i ->
        // A point somewhere along one of the four sides, a little to either side of the line.
        val along = 0.08f + nextEdge() * 0.84f
        val off = (nextEdge() - 0.5f) * 0.07f
        val (wx, wy) = when (i % 4) {
            0 -> (x0 + along * (x1 - x0)) to (y0 + off)
            1 -> (x1 + off) to (y0 + along * (y1 - y0))
            2 -> (x0 + along * (x1 - x0)) to (y1 + off)
            else -> (x0 + off) to (y0 + along * (y1 - y0))
        }
        val at = transform.worldToScreen(wx, wy)
        if (i < 6) {
            drawCircle(if (i % 2 == 0) DIRT_DARK else DIRT_RIDGE, unit * (0.014f + nextEdge() * 0.012f), at)
        } else {
            val blade = unit * 0.04f
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

/** Seedlings start at 35% of full size so a freshly planted tile is not empty. */
private fun plantScale(obj: GridObject): Float = 0.35f + 0.65f * obj.growthProgress.coerceIn(0f, 1f)

private fun DrawScope.drawGroundCover(obj: GridObject, base: Offset, unit: Float) {
    val color = if (obj.objectId.contains("grass")) Color(0xFF558B2F) else Color(0xFFBDBDBD)
    val r = unit * 0.035f
    drawCircle(color, r * 1.3f, base + Offset(-unit * 0.12f, unit * 0.01f))
    drawCircle(color, r, base + Offset(unit * 0.04f, -unit * 0.05f))
    drawCircle(color, r * 1.1f, base + Offset(unit * 0.13f, unit * 0.04f))
}

private fun DrawScope.drawPlant(obj: GridObject, base: Offset, unit: Float) {
    val withered = obj.status == PlantStatus.WITHERED
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
    val center = base - Offset(0f, unit * (0.55f * plantScale(obj) + 0.3f))
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
        if (obj.isPlant) drawPlant(obj, base, unit) else drawDecoration(base, unit)
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
    val isHut = structure == StructureData.HUT
    val top = ground + if (isHut) 4f else 1.8f

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
        val apex = transform.worldToScreen((x0 + x1) / 2f, (y0 + y1) / 2f, top + 3f)
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
