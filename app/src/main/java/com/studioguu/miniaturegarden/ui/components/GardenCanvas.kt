package com.studioguu.miniaturegarden.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

private val TILE_WIDTH = 128.dp
private val TILE_HEIGHT = 64.dp
private val HEIGHT_STEP = 16.dp

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
    // Null until the first layout pass tells us how far to zoom out to fit the grid.
    var camera by remember { mutableStateOf<GardenCamera?>(null) }
    val atlasImage = remember(atlas) { atlas.bitmap?.asImageBitmap() }
    val pipeline = remember { LayerPipeline() }
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

private val GRASS_LIGHT = Color(0xFF9CCC65)
private val GRASS_DARK = Color(0xFF8BC34A)
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
            GroundTile.WATER -> drawWaterTile(transform, x, y)
            GroundTile.GRASS -> drawPath(transform.tilePath(x, y), GRASS_LIGHT)
            else -> drawPath(transform.tilePath(x, y), GRASS_DARK)
        }
    }
}

private val WATER = Color(0xFF4FB3E8)
private val WATER_RIPPLE = Color(0xFFB3E5FC)

/** Flat water with two short ripples running along the tile. */
private fun DrawScope.drawWaterTile(transform: IsometricTransform, x: Int, y: Int) {
    drawPath(transform.tilePath(x, y), WATER)
    val stroke = transform.tileWidth * 0.02f
    for ((offsetX, offsetY) in listOf(0.2f to 0.35f, 0.5f to 0.7f)) {
        drawLine(
            color = WATER_RIPPLE,
            start = transform.worldToScreen(x + offsetX, y + offsetY),
            end = transform.worldToScreen(x + offsetX + 0.3f, y + offsetY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
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
