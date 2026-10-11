package com.studioguu.miniaturegarden.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.studioguu.miniaturegarden.data.asset.GroundTile
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TileType
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import com.studioguu.miniaturegarden.ui.render.IsometricMetrics
import com.studioguu.miniaturegarden.ui.render.IsometricTransform
import com.studioguu.miniaturegarden.ui.render.LayerPipeline
import com.studioguu.miniaturegarden.ui.render.RenderLayer
import com.studioguu.miniaturegarden.ui.render.TileCoord
import com.studioguu.miniaturegarden.ui.render.TilePicker
import com.studioguu.miniaturegarden.ui.render.drawSprite
import kotlinx.coroutines.delay

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
 * @param gardenMap the terrain under the objects.
 * @param onTileTapped receives the tapped tile, or null for a tap outside the grid.
 */
@Composable
fun GardenCanvas(
    gridObjects: List<GridObject>,
    selectedTile: TileCoord?,
    onTileTapped: (TileCoord?) -> Unit,
    modifier: Modifier = Modifier,
    atlas: SpriteAtlas = SpriteAtlas.EMPTY,
    gardenMap: GardenMap = GardenMap.allGrass(),
    showGrid: Boolean = true,
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
    // The water's ripples step along a few times a second, and only while there is water to show.
    val hasWater = gridObjects.any { it.isTerrain }
    val waterPhase by produceState(0, hasWater) {
        while (hasWater) {
            delay(WATER_FRAME_MILLIS)
            value = (value + 1) % WATER_PHASES
        }
    }
    val grid = remember(gardenMap, gridObjects) { GardenGrid(gardenMap, gridObjects) }
    val currentOnTileTapped by rememberUpdatedState(onTileTapped)

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

        pipeline.submit(RenderLayer.BASE_TERRAIN) {
            drawTerrain(transform, gardenMap) { tile, center ->
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
        for (obj in gridObjects) {
            if (obj.isTerrain) {
                // Part of the ground: drawn with the terrain, right after the lawn it replaces.
                val terrainSprite = atlas[TileSetDefinition.assetIdFor(obj)]
                pipeline.submit(RenderLayer.BASE_TERRAIN, depth = 1f) {
                    if (terrainSprite != null && atlasImage != null) {
                        drawSprite(atlasImage, terrainSprite, transform.tileCenter(obj.gridX, obj.gridY), spriteScale)
                    } else {
                        drawWaterTile(transform, obj.gridX, obj.gridY, waterPhase) { x, y ->
                            grid.objectAt(x, y)?.isTerrain == true
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

            if (!isGroundCover && structure == null) {
                pipeline.submit(RenderLayer.SHADOWS, depth) { drawShadow(obj, base, unit) }
            }
            pipeline.submit(if (isGroundCover) RenderLayer.BOTTOM_OBJECTS else RenderLayer.GRID_OBJECTS, depth) {
                if (sprite != null && atlasImage != null) {
                    drawSprite(atlasImage, sprite, base, spriteScale, flipped = obj.isFlipped)
                } else if (structure != null) {
                    drawStructure(transform, obj, structure)
                } else {
                    drawPlaceholder(obj, base, unit)
                }
            }
            if (obj.isPlant && (obj.status == PlantStatus.THIRSTY || obj.status == PlantStatus.UNNOURISHED)) {
                pipeline.submit(RenderLayer.TOP_EFFECTS, depth) { drawStatusBubble(obj, base, unit) }
            }
        }
        pipeline.render(this)
    }
}

// --- Layer 0: terrain -------------------------------------------------------------------------

// Muted, slightly olive greens: closer to the leaves of the plant sprites than a bright lime,
// so the plants read as growing out of the lawn rather than sitting on top of it.
private val GRASS_LIGHT = Color(0xFF86B26B)
private val GRASS_DARK = Color(0xFF7AA661)
private val SOIL_LEFT = Color(0xFF8D6E63)
private val SOIL_RIGHT = Color(0xFF6D4C41)

/**
 * @param drawTileSprite draws the sprite for a ground tile centered on the given point and
 *   returns true, or returns false when there is none and the flat-color tile should be used.
 */
private fun DrawScope.drawTerrain(
    transform: IsometricTransform,
    gardenMap: GardenMap,
    drawTileSprite: DrawScope.(tile: GroundTile, center: Offset) -> Boolean,
) {
    val gridSize = gardenMap.size
    val max = gridSize.toFloat()
    val left = transform.worldToScreen(0f, max)
    val bottom = transform.worldToScreen(max, max)
    val right = transform.worldToScreen(max, 0f)
    val thickness = Offset(0f, transform.tileHeight * 0.6f)

    // The two visible side faces of the soil slab the garden sits on.
    drawPath(quad(left, bottom, bottom + thickness, left + thickness), SOIL_LEFT)
    drawPath(quad(bottom, right, right + thickness, bottom + thickness), SOIL_RIGHT)

    // x-then-y order draws every tile before the two neighbours in front of it.
    for (x in 0 until gridSize) for (y in 0 until gridSize) {
        val type = gardenMap.tileAt(x, y) ?: TileType.GRASS
        val tile = TileSetDefinition.groundTileFor(type, x, y)
        if (drawTileSprite(tile, transform.tileCenter(x, y))) continue
        when (tile) {
            GroundTile.WATER -> drawWaterTile(transform, x, y, phase = 0)
            GroundTile.GRASS -> drawPath(transform.tilePath(x, y), GRASS_LIGHT)
            else -> drawPath(transform.tilePath(x, y), GRASS_DARK)
        }
    }
}

// Pixel-art water. Each tile is cut into WATER_PIXELS x WATER_PIXELS little diamonds, coloured
// from a four-step palette: mid blue as the base, diagonal bands of light blue with white
// crests for the ripples, and scattered dark pixels for depth. The pattern is a fixed function
// of the pixel's position, so neighbouring tiles line up into one continuous stream.
private const val WATER_PIXELS = 8
private const val WATER_PHASES = 6
private const val WATER_FRAME_MILLIS = 450L
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

private const val SHADOW_ALPHA = 0.3f
private val STEM_GREEN = Color(0xFF388E3C)
private val LEAF_GREEN = Color(0xFF66BB6A)
private val WITHERED_BROWN = Color(0xFF8D6E63)
private val BLOOM_COLORS = listOf(
    Color(0xFFE57373), Color(0xFFF06292), Color(0xFFFFB74D), Color(0xFFFFF176), Color(0xFFBA68C8),
)

/** Seedlings start at 35% of full size so a freshly planted tile is not empty. */
private fun plantScale(obj: GridObject): Float = 0.35f + 0.65f * obj.growthProgress.coerceIn(0f, 1f)

private fun DrawScope.drawShadow(obj: GridObject, base: Offset, unit: Float) {
    val size = if (obj.isPlant) plantScale(obj) else 1f
    val width = unit * 0.42f * size
    val height = width / 2f
    val center = base + Offset(unit * 0.05f, unit * 0.025f)
    drawOval(
        color = Color.Black.copy(alpha = SHADOW_ALPHA),
        topLeft = center - Offset(width / 2f, height / 2f),
        size = Size(width, height),
    )
}

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
