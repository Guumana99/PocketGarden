package com.studioguu.miniaturegarden.ui.render

/**
 * The colours of tilled soil (#54), matched to the wasteland it is dug out of
 * (`soil_wasteland.png`: dark, slightly cool grey-black earth, about #29282A on average, its
 * lightest crumbs about #4D4644).
 *
 * The beds were drawn in the red-brown of the old soil tile, much lighter and redder than the
 * earth round them: on the wasteland they looked laid on top of it. Here a bed is the same earth
 * turned over: a little warmer, [DARK] where it is damp and close to the wasteland's own tone,
 * [DRY] crumbs on the ridges, [DEEP] in the furrows. The bed still reads at a glance by its
 * furrows and its warmer body, not by a different colour of earth.
 *
 * Plain ARGB values, so that tests can hold them against the picture.
 */
object SoilPalette {
    /** Damp earth: the body of a bed. */
    const val DARK = 0xFF3A3029L

    /** The rim where the bed was cut, and the shade of its front face. */
    const val EDGE = 0xFF231C18L

    /** The tops of the furrows, catching the light. */
    const val RIDGE = 0xFF564840L

    /** The bottoms of the furrows and the damp patches. */
    const val DEEP = 0xFF181210L

    /** Dried crumbs on the surface. */
    const val DRY = 0xFF6E6158L

    /** Loose earth spilled over the edge. */
    const val DUST = 0xFF4A3F38L

    /**
     * How far a bed's body goes from [DRY] towards [DARK] for its [dampness] (-1 to 1,
     * SoilTexture.fieldDampness): mostly damp-dark, a little paler where the field is drier.
     */
    fun bodyMix(dampness: Float): Float = (0.78f + 0.2f * dampness).coerceIn(0f, 1f)

    /**
     * How strongly a bed's colour is laid over the wasteland's own earth where it is drawn on it
     * (#55): enough to read as turned, damp soil, little enough that the earth's grain and cracks
     * still show through, so that the bed is plainly the same ground.
     */
    const val TURNED_OVER = 0.55f

    /** The red, green and blue of an ARGB [colour]. */
    fun rgb(colour: Long): Triple<Int, Int, Int> =
        Triple(((colour shr 16) and 0xFF).toInt(), ((colour shr 8) and 0xFF).toInt(), (colour and 0xFF).toInt())

    /** How light an ARGB [colour] is (Rec. 601 luma, 0 to 255). */
    fun luma(colour: Long): Float {
        val (r, g, b) = rgb(colour)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** [from] and [to] mixed [t] of the way, channel by channel, opaque. */
    fun mix(from: Long, to: Long, t: Float): Long {
        val (r0, g0, b0) = rgb(from)
        val (r1, g1, b1) = rgb(to)
        fun m(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (m(r0, r1) shl 16) or (m(g0, g1) shl 8) or m(b0, b1)
    }
}
