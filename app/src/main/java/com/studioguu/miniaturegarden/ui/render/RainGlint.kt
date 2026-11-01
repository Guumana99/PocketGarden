package com.studioguu.miniaturegarden.ui.render

import kotlin.math.PI
import kotlin.math.sin

/** The glint on the crops as a blessed rain ends. */
object RainGlint {
    /** How much of the whole a crop's own glint takes; the rest is how far apart they start. */
    private const val OWN_SHARE = 0.7f

    /** How strong the glint of the crop with [seed] is at [progress] of the whole, 0 to 1. */
    fun strengthAt(progress: Float, seed: Int): Float {
        val start = (1f - OWN_SHARE) * (Math.floorMod(seed, 7) / 6f)
        val own = (progress - start) / OWN_SHARE
        if (own <= 0f || own >= 1f) return 0f
        return sin(PI * own).toFloat()
    }
}
