package com.studioguu.miniaturegarden.ui.render

/** A soft patch of lighter or deeper green laid over the lawn, in grid coordinates. */
data class ShadePatch(val x: Float, val y: Float, val radius: Float, val light: Boolean, val strength: Float)

/**
 * Soft light and shade over the whole lawn (#47).
 *
 * The lawn is laid cell by cell from one picture of grass, which has had its own lighting taken
 * out (tools/prepare_building_art.py `flatten_light`), so the cells no longer show as a chequer.
 * What makes it read as one meadow again is light that ignores the cells: broad, faint patches,
 * each a cell or two across, placed over the garden as a whole and crossing the cells' edges.
 *
 * Worked out from the garden's size alone, so the same garden always looks the same.
 */
object LawnShade {
    /** Patches per cell of lawn, on average. */
    const val PER_CELL = 0.55f

    const val MIN_RADIUS = 0.8f
    const val MAX_RADIUS = 2.2f

    /** The faintest and strongest a patch is drawn (its alpha at the middle). */
    const val MIN_STRENGTH = 0.07f
    const val MAX_STRENGTH = 0.18f

    fun patches(gridSize: Int): List<ShadePatch> {
        var state = gridSize * 2654435761.toInt() xor 0x5bd1e995
        fun next(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32768f
        }
        val count = (gridSize * gridSize * PER_CELL).toInt()
        return List(count) {
            ShadePatch(
                x = next() * gridSize,
                y = next() * gridSize,
                radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * next(),
                light = next() < 0.45f,
                strength = MIN_STRENGTH + (MAX_STRENGTH - MIN_STRENGTH) * next(),
            )
        }
    }

    /** Whether the cell of grass at ([x], [y]) is drawn mirrored: about half of them, in no pattern. */
    fun mirrored(x: Int, y: Int): Boolean {
        var h = x * 73856093 xor (y * 19349663) xor 0x2545F491
        h = h xor (h ushr 15)
        h *= 0x2C1B3C6D
        h = h xor (h ushr 12)
        return (h and 1) == 1
    }

    /**
     * How much larger than its diamond each cell of grass is drawn, so neighbours overlap at the
     * seam. For the picture as it is; the feathered picture ([feather]) is drawn at [FEATHER_OVERLAP].
     */
    const val CELL_OVERLAP = 1.04f

    // --- Feathering: cells that fade into each other instead of meeting at a line ---
    //
    // The picture of grass is a slab: a diamond of lawn with a darker rim and a dark side face
    // below it. Laid cell by cell, every rim showed, and the lawn read as a grid of tiles. So the
    // picture is cut to its top face and faded out towards the face's edge, and each cell is
    // drawn that much larger: whole where it is its own, shading off into its neighbours round
    // its edges. The rims vanish into the fade, and no line is left between two cells.

    /** How much larger than its diamond each feathered cell of grass is drawn. */
    const val FEATHER_OVERLAP = 1.32f

    /**
     * How far out from the middle of the picture's top face (0 the middle, 1 its edge) the fade
     * begins. Drawn at [FEATHER_OVERLAP], that is just inside the cell's own edge, so a cell is
     * whole all over itself and its fade lies over its neighbours.
     */
    const val FEATHER_START = 0.74f

    /** How solid the feathered picture is at [norm], the distance out from its middle ([diamondNorm]). */
    fun featherAlpha(norm: Float): Float {
        if (norm <= FEATHER_START) return 1f
        if (norm >= 1f) return 0f
        val t = (norm - FEATHER_START) / (1f - FEATHER_START)
        // Smooth at both ends, so that the fade has no edge of its own.
        return 1f - t * t * (3f - 2f * t)
    }

    /**
     * How far ([px], [py]) is from ([centerX], [centerY]) in a diamond [halfWidth] by [halfHeight]
     * across: 0 at the middle, 1 on its edge, more outside it.
     */
    fun diamondNorm(px: Float, py: Float, centerX: Float, centerY: Float, halfWidth: Float, halfHeight: Float): Float =
        kotlin.math.abs(px - centerX) / halfWidth + kotlin.math.abs(py - centerY) / halfHeight

    /**
     * The grass picture's ARGB [pixels] ([width] wide) cut to its top face, a diamond [halfWidth]
     * by [halfHeight] round ([centerX], [centerY]), and faded out towards that face's edge
     * ([featherAlpha]). Everything outside the face, the side of the slab, is left out.
     */
    fun feather(
        pixels: IntArray,
        width: Int,
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
    ): IntArray = IntArray(pixels.size) { i ->
        val argb = pixels[i]
        val norm = diamondNorm(i % width + 0.5f, i / width + 0.5f, centerX, centerY, halfWidth, halfHeight)
        val alpha = ((argb ushr 24) * featherAlpha(norm)).toInt().coerceIn(0, 255)
        (alpha shl 24) or (argb and 0xFFFFFF)
    }

    /**
     * The picture's average colour over the middle of its top face, opaque: laid under the whole
     * lawn first, so that wherever the fades meet thinly nothing else shows through.
     */
    fun meanColour(
        pixels: IntArray,
        width: Int,
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
    ): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (i in pixels.indices) {
            val argb = pixels[i]
            if ((argb ushr 24) < 200) continue
            if (diamondNorm(i % width + 0.5f, i / width + 0.5f, centerX, centerY, halfWidth, halfHeight) > FEATHER_START) continue
            r += (argb shr 16) and 0xFF
            g += (argb shr 8) and 0xFF
            b += argb and 0xFF
            n++
        }
        if (n == 0L) return 0xFF9CBB6C.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}
