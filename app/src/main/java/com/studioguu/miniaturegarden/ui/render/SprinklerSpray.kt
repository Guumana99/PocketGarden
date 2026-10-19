package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import kotlin.math.PI

/**
 * The running sprinkler's animation, as data the garden draws from: where its head points, and
 * where each drop of its jets is, at a moment of the garden's clock. A stopped sprinkler stands
 * still on its first frame, with no water.
 *
 * Artwork can take over frame by frame: a picture packed as [spriteIdFor] (frame 0 to
 * [FRAME_COUNT] - 1) is drawn for that frame, and only without it is the spray drawn in code.
 */
object SprinklerSpray {
    /** Frames in one turn of the head, for artwork drawn frame by frame. */
    const val FRAME_COUNT = TileSetDefinition.SPRINKLER_SPRAY_FRAMES

    /** How long the head takes to go once round. */
    const val TURN_MILLIS = 2_400L

    /** Jets of water, evenly round the head. */
    const val JETS = 3

    /** Drops along each jet, from the head out to the edge of its reach. */
    const val DROPS_PER_JET = 4

    /** One drop of a jet: which way it flies (radians), how far out it is (0 head, 1 edge of its reach) and how clear it is. */
    data class Drop(val angle: Float, val reach: Float, val alpha: Float)

    /** The picture at one moment: the frame, the head's angle and the drops in the air. */
    data class Frame(val index: Int, val headAngle: Float, val drops: List<Drop>)

    private val STILL = Frame(index = 0, headAngle = 0f, drops = emptyList())

    /** The animation at [clockMillis]; [running] false gives the still, dry first frame. */
    fun frameAt(clockMillis: Long, running: Boolean): Frame {
        if (!running) return STILL
        val turn = (clockMillis.mod(TURN_MILLIS)).toFloat() / TURN_MILLIS
        val head = (turn * 2 * PI).toFloat()
        val drops = ArrayList<Drop>(JETS * DROPS_PER_JET)
        for (jet in 0 until JETS) {
            val angle = head + (jet * 2 * PI / JETS).toFloat()
            for (k in 0 until DROPS_PER_JET) {
                // Each drop flies out and fades; the next one follows a step behind it.
                val reach = ((turn * JETS + k.toFloat() / DROPS_PER_JET) % 1f)
                // The jet curls back a little as it goes out, the way a turning head throws it.
                drops += Drop(angle = angle - reach * 0.6f, reach = reach, alpha = 1f - reach * reach)
            }
        }
        return Frame(index = (turn * FRAME_COUNT).toInt().coerceIn(0, FRAME_COUNT - 1), headAngle = head, drops = drops)
    }

    /** The sprite for one frame, when artwork for it has been packed. */
    fun spriteIdFor(frame: Int): String = TileSetDefinition.sprinklerSprayId(frame)
}
