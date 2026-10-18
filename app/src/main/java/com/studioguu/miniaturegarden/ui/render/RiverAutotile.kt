package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Picks the river picture for a tile from its neighbours, so that tiles laid side by side join
 * into one stream.
 *
 * The four neighbours give a mask: [NORTH] = 1, [EAST] = 2, [SOUTH] = 4, [WEST] = 8, each bit
 * set when that neighbour is river as well. North is the cell at gridY - 1, east at gridX + 1.
 * The picture for a mask sits at column `mask % 4`, row `mask / 4` of a 4 x 4 block of square
 * cells. The atlas holds one such block per animation frame, stacked top to bottom; played in a
 * loop they make the water flow.
 */
object RiverAutotile {
    const val NORTH = 1
    const val EAST = 2
    const val SOUTH = 4
    const val WEST = 8

    const val COLUMNS = 4
    const val ROWS = 4

    /** @param isRiver whether the cell at the given grid position is river. */
    inline fun maskAt(x: Int, y: Int, isRiver: (x: Int, y: Int) -> Boolean): Int =
        (if (isRiver(x, y - 1)) NORTH else 0) or
            (if (isRiver(x + 1, y)) EAST else 0) or
            (if (isRiver(x, y + 1)) SOUTH else 0) or
            (if (isRiver(x - 1, y)) WEST else 0)

    fun column(mask: Int): Int = mask % COLUMNS

    fun row(mask: Int): Int = mask / COLUMNS

    /** Milliseconds each animation frame is shown for. */
    const val FRAME_MILLIS = 260L

    /**
     * Which animation frame the tile at ([x], [y]) shows when the garden's water clock stands at
     * [tick].
     *
     * The stream flows towards the east and the south of the grid, which is down the screen, and
     * each tile runs one frame behind the tile upstream of it. So the tiles do not all blink
     * together: a ripple that is in one tile now is in the next one a frame later, and is seen
     * travelling down the river. The atlas is drawn for exactly this offset (see
     * tools/build_terrain_assets.py): with it, the waves of neighbouring tiles join without a
     * seam.
     *
     * The result may be any whole number, negative too; it wraps round when used.
     */
    fun frameFor(tick: Int, x: Int, y: Int): Int = tick - (x + y)

    /** Animation frames in an atlas of the given size: one per square block, at least one. */
    fun frameCount(atlasWidth: Int, atlasHeight: Int): Int =
        if (atlasWidth <= 0) 1 else (atlasHeight / atlasWidth).coerceAtLeast(1)

    // --- Banks: the stream lies a little below the lawn ---
    //
    // The pictures are flat, seen from above, with an earth bank round the water. Laid on the
    // lawn as they are, the stream read as a strip stuck on top of the grass. So the water is
    // shaded under the two far banks (north and west, whose sides face the viewer and would cast
    // shade on water lying lower than they do), and catches a little light at the two near ones.

    /** Where the water begins, inward from a cell's side, as a fraction of the cell: just inside the bank. */
    const val BANK_EDGE = 0.215f

    /** How far out over the water the shade under a far bank reaches, as a fraction of the cell. */
    const val BANK_SHADE_WIDTH = 0.17f

    /** The far sides of a tile with [mask] that are bank, not more river: the water is shaded under them. */
    fun shadedSides(mask: Int): List<Int> = listOf(NORTH, WEST).filter { mask and it == 0 }

    /** The near sides of a tile with [mask] that are bank: the water's edge catches the light there. */
    fun litSides(mask: Int): List<Int> = listOf(SOUTH, EAST).filter { mask and it == 0 }

    /** Top edge, in atlas pixels, of the cell for [mask] in animation frame [frame] (wraps round). */
    fun cellTop(mask: Int, frame: Int, atlasWidth: Int, atlasHeight: Int): Int {
        val frames = frameCount(atlasWidth, atlasHeight)
        val blockHeight = atlasHeight / frames
        return Math.floorMod(frame, frames) * blockHeight + row(mask) * (blockHeight / ROWS)
    }
}

// Reused between draws; all drawing happens on the main thread.
private val groundMatrix = Matrix()
private val groundPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
private val groundSource = FloatArray(8)
private val groundTarget = FloatArray(8)
private val groundSourceRect = Rect()
private val groundDestination = RectF()

/**
 * Draws the river tile for [mask] out of [atlas] onto the ground of tile (x, y).
 *
 * @param frame which animation frame to show; any number, it wraps round.
 */
fun DrawScope.drawRiverTile(atlas: Bitmap, mask: Int, frame: Int, transform: IsometricTransform, x: Int, y: Int) {
    val cellWidth = atlas.width / RiverAutotile.COLUMNS
    val cellHeight = atlas.height / RiverAutotile.frameCount(atlas.width, atlas.height) / RiverAutotile.ROWS
    val left = RiverAutotile.column(mask) * cellWidth
    val top = RiverAutotile.cellTop(mask, frame, atlas.width, atlas.height)
    drawGroundTexture(atlas, transform, x, y, left, top, left + cellWidth, top + cellHeight) { canvas, cell ->
        drawBanks(canvas, cell, mask)
    }
}

// The shade under a far bank and the light at a near one, drawn for the north side of a cell one
// unit across (rotated round for the others). Made once; all drawing happens on the main thread.
private val bankShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = LinearGradient(
        0f, RiverAutotile.BANK_EDGE, 0f, RiverAutotile.BANK_EDGE + RiverAutotile.BANK_SHADE_WIDTH,
        intArrayOf(0x5A1B3A4A, 0x221B3A4A, 0x001B3A4A), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
    )
    // Over the picture only: the water and its bank, never the grass round them.
    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
}
private val bankLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = LinearGradient(
        0f, RiverAutotile.BANK_EDGE, 0f, RiverAutotile.BANK_EDGE + 0.05f,
        intArrayOf(0x66FFF6DC, 0x00FFF6DC), null, Shader.TileMode.CLAMP,
    )
    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
}

/** Shade and light along the banks of a river picture drawn into [cell] (picture space). */
private fun drawBanks(canvas: Canvas, cell: RectF, mask: Int) {
    fun side(side: Int, paint: Paint) {
        canvas.save()
        canvas.translate(cell.left, cell.top)
        canvas.scale(cell.width(), cell.height())
        // Turn the north side's strip round to the side asked for: east a quarter turn, and on.
        val turns = when (side) {
            RiverAutotile.NORTH -> 0f
            RiverAutotile.EAST -> 90f
            RiverAutotile.SOUTH -> 180f
            else -> 270f
        }
        canvas.rotate(turns, 0.5f, 0.5f)
        canvas.drawRect(0f, RiverAutotile.BANK_EDGE - 0.01f, 1f, RiverAutotile.BANK_EDGE + RiverAutotile.BANK_SHADE_WIDTH, paint)
        canvas.restore()
    }
    for (s in RiverAutotile.shadedSides(mask)) side(s, bankShadePaint)
    for (s in RiverAutotile.litSides(mask)) side(s, bankLightPaint)
}

/**
 * Lays a flat, seen-from-above picture onto the ground of tile (x, y): the given part of
 * [texture] (all of it by default) is stretched so that its top edge runs along the tile's
 * north side, its right edge along the east side, and so on round.
 */
fun DrawScope.drawGroundTexture(
    texture: Bitmap,
    transform: IsometricTransform,
    x: Int,
    y: Int,
    srcLeft: Int = 0,
    srcTop: Int = 0,
    srcRight: Int = texture.width,
    srcBottom: Int = texture.height,
    overlay: ((canvas: Canvas, cell: RectF) -> Unit)? = null,
) {
    val top = transform.worldToScreen(x.toFloat(), y.toFloat())
    val right = transform.worldToScreen(x + 1f, y.toFloat())
    val bottom = transform.worldToScreen(x + 1f, y + 1f)
    val left = transform.worldToScreen(x.toFloat(), y + 1f)

    // Picture corners, clockwise from top-left ...
    groundSource[0] = srcLeft.toFloat(); groundSource[1] = srcTop.toFloat()
    groundSource[2] = srcRight.toFloat(); groundSource[3] = srcTop.toFloat()
    groundSource[4] = srcRight.toFloat(); groundSource[5] = srcBottom.toFloat()
    groundSource[6] = srcLeft.toFloat(); groundSource[7] = srcBottom.toFloat()
    // ... onto the tile's corners, clockwise from the far one.
    groundTarget[0] = top.x; groundTarget[1] = top.y
    groundTarget[2] = right.x; groundTarget[3] = right.y
    groundTarget[4] = bottom.x; groundTarget[5] = bottom.y
    groundTarget[6] = left.x; groundTarget[7] = left.y
    groundMatrix.setPolyToPoly(groundSource, 0, groundTarget, 0, 4)
    groundSourceRect.set(srcLeft, srcTop, srcRight, srcBottom)
    groundDestination.set(groundSourceRect)

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.concat(groundMatrix)
        if (overlay == null) {
            native.drawBitmap(texture, groundSourceRect, groundDestination, groundPaint)
        } else {
            // A layer of its own, so that what is laid over the picture can keep to it.
            native.saveLayer(groundDestination, null)
            native.drawBitmap(texture, groundSourceRect, groundDestination, groundPaint)
            overlay(native, groundDestination)
            native.restore()
        }
        native.restore()
    }
}
