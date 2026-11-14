package com.studioguu.miniaturegarden.domain.model

import kotlin.math.max

/**
 * Moss growing in over a cell (FB1114), the way a lawn comes up ([LawnGrowth]): from a few thin
 * patches when it is laid, spreading and running together, to the whole cell of moss when it has
 * grown. Each cell has its own patches, always the same ones: where they lie, how wide each gets,
 * and when it first shows.
 *
 * @property u across the cell, 0 to 1 (along gridX), and [v] down it (along gridY): its middle.
 * @property radius how far it reaches, in cells.
 */
data class MossPatch(val u: Float, val v: Float, val radius: Float)

object MossPatches {
    /** How many patches a cell has: on a grid of [SIDE] by [SIDE], each shaken a little off its point. */
    const val SIDE = 4

    /** How far a patch reaches when it has grown, at the least (in cells), and how far it spreads at the start. */
    const val GROWN_RADIUS = 0.38f
    const val FIRST_RADIUS = 0.07f

    /** How thick the moss is: thin at first ([THIN]), the whole picture when grown. */
    const val THIN = 0.55f

    /** The patches of cell ([x], [y]) at [growth] (0 laid to 1 grown); those not come up yet are left out. */
    fun at(x: Int, y: Int, growth: Float): List<MossPatch> {
        val g = growth.coerceIn(0f, 1f)
        val out = ArrayList<MossPatch>(SIDE * SIDE)
        for (i in 0 until SIDE) for (j in 0 until SIDE) {
            val k = i * SIDE + j
            val r0 = rand(x, y, k, 0)
            // A third of them show from the start; the rest come up over the first half.
            val from = if (r0 < 0.34f) 0f else (r0 - 0.34f) / 0.66f * 0.5f
            if (g < from) continue
            val along = ((g - from) / (1f - from)).coerceIn(0f, 1f)
            val size = 0.85f + 0.4f * rand(x, y, k, 1)
            val spread = along * along * (3f - 2f * along)
            val u = (i + 0.5f) / SIDE + (rand(x, y, k, 2) - 0.5f) * 0.18f
            val v = (j + 0.5f) / SIDE + (rand(x, y, k, 3) - 0.5f) * 0.18f
            out += MossPatch(u, v, size * (FIRST_RADIUS + (GROWN_RADIUS - FIRST_RADIUS) * spread))
        }
        return out
    }

    /** How strongly the moss is drawn at [growth]: thin at first, all of it when grown. */
    fun thickness(growth: Float): Float = THIN + (1f - THIN) * growth.coerceIn(0f, 1f)

    private fun rand(x: Int, y: Int, k: Int, salt: Int): Float {
        var h = x.toLong() * 73856093L xor y.toLong() * 19349663L xor k.toLong() * 83492791L xor salt.toLong() * 2654435761L
        h = (h xor (h ushr 33)) * -49064778989728563L
        h = (h xor (h ushr 33)) * -4265267296055464877L
        h = h xor (h ushr 33)
        return max(0f, ((h ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat())
    }
}
