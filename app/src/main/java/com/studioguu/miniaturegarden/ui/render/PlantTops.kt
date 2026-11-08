package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantTop

/**
 * Where a visitor settles on a plant: the top of what its picture draws, found from the picture
 * itself ([topOf]), with a nudge by hand for the pictures where that point is off ([BY_HAND]).
 */
object PlantTops {
    /** A pixel at least this opaque (0..255) is part of the picture. */
    private const val OPAQUE = 128

    /** How far below the very top a visitor's feet go, as a share of the picture's height: onto it, not over it. */
    private const val SINK = 0.04f

    /** Columns of nothing a stretch may have in it and still be one stretch. */
    private const val GAP = 2

    /**
     * Nudges by hand, by sprite: (across, height) in tile widths, added to the point found. Only for
     * pictures whose top is not a place to stand (none so far: see the debug menu's view of the points).
     */
    val BY_HAND: Map<String, PlantTop> = emptyMap()

    /**
     * The top of the picture in [argb] ([width] x [height], row by row): in the first rows that
     * have any opaque pixels to speak of, a little below the very top, the middle of the widest
     * stretch of them, as fractions of the width and the height like a sprite's anchor; null for
     * an empty picture. The widest stretch, not the middle of them all: with two flowers side by
     * side that middle was the air between them, and a ladybird sat on nothing (2026-11-08).
     */
    fun topOf(argb: IntArray, width: Int, height: Int): Pair<Float, Float>? {
        if (width <= 0 || height <= 0) return null
        val enough = maxOf(2, width / 40)
        val band = maxOf(1, (height * SINK).toInt())
        for (row in 0 until height) {
            if (opaqueIn(argb, width, row) < enough) continue
            val solid = BooleanArray(width)
            for (r in row until minOf(height, row + band)) for (x in 0 until width) {
                if ((argb[r * width + x] ushr 24) >= OPAQUE) solid[x] = true
            }
            // The widest run of columns with something in them; small gaps (a leaf's ragged edge) do not split it.
            var bestFrom = 0
            var bestTo = -1
            var from = -1
            var gap = 0
            for (x in 0..width) {
                if (x < width && solid[x]) {
                    if (from < 0) from = x
                    gap = 0
                } else if (from >= 0) {
                    gap++
                    if (gap > GAP || x == width) {
                        val to = x - gap
                        if (to - from > bestTo - bestFrom) {
                            bestFrom = from
                            bestTo = to
                        }
                        from = -1
                        gap = 0
                    }
                }
            }
            val x = ((bestFrom + bestTo) / 2f + 0.5f) / width
            return x to ((row + band).toFloat() / height).coerceAtMost(1f)
        }
        return null
    }

    private fun opaqueIn(argb: IntArray, width: Int, row: Int): Int {
        var n = 0
        for (x in 0 until width) if ((argb[row * width + x] ushr 24) >= OPAQUE) n++
        return n
    }
}

/** [PlantTops] for the pictures of [atlas], each read once. */
class PlantTopFinder(private val atlas: SpriteAtlas) {
    private val tops = HashMap<String, Pair<Float, Float>?>()

    /**
     * Where the top of [plant]'s picture stands, as drawn at [scale] times its usual size (a crop
     * under glass is smaller); null without a picture.
     */
    fun topOf(plant: GridObject, scale: Float = 1f): PlantTop? {
        val sprite = atlas[TileSetDefinition.assetIdFor(plant)] ?: return null
        val (fx, fy) = tops.getOrPut(sprite.assetId) {
            if (sprite.page !in atlas.pages.indices) return@getOrPut null
            val pixels = IntArray(sprite.width * sprite.height)
            atlas.pages[sprite.page].getPixels(pixels, 0, sprite.width, sprite.atlasX, sprite.atlasY, sprite.width, sprite.height)
            PlantTops.topOf(pixels, sprite.width, sprite.height)
        } ?: return null
        val unit = scale * sprite.drawScale / atlas.manifest.tileWidth
        val across = (fx - sprite.anchorX) * sprite.width * unit * (if (plant.isFlipped) -1f else 1f)
        val height = (sprite.anchorY - fy) * sprite.height * unit
        val nudge = PlantTops.BY_HAND[sprite.assetId] ?: PlantTop(0f, 0f)
        return PlantTop(across + nudge.across, (height + nudge.height).coerceAtLeast(0f))
    }
}
