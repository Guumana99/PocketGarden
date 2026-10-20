package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import kotlin.math.PI
import kotlin.math.floor

/**
 * The running sprinkler's animation, as data the garden draws from: where its head points, and
 * where each drop of its jets is, at a moment of the garden's clock. A stopped sprinkler stands
 * still on its first frame, with no water.
 *
 * The water moves as water does. A drop leaves the head in the direction the nozzle points at
 * that moment and keeps that direction: it does not turn with the head. It goes out at an even
 * pace and up and down along an arc ([Drop.lift]), and where it comes down it makes a small
 * splash ([Splash]). Because the head turns while drops are on their way, the drops of a jet
 * trail behind its nozzle in a gentle curve of their own, rather than being drawn curled.
 * (Before, every drop was swung round with the head and bent back by a fixed amount: the jets
 * looked like stiff spirals turning on the spot.)
 *
 * Drops are launched one after another at a steady beat, each from a fixed moment, so they move
 * smoothly and never jump from frame to frame; how far each one carries varies a little, from a
 * number fixed by its place in the turn, so the same moment a turn later is the same picture.
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
    const val JETS = 2

    /** Drops of a jet in the air at once, from the one just launched to the one about to land. */
    const val DROPS_PER_JET = 6

    /** How long a drop is in the air, from the head to the ground. */
    const val FLIGHT_MILLIS = 900L

    /** The beat at which a jet launches its drops. */
    const val LAUNCH_MILLIS = FLIGHT_MILLIS / DROPS_PER_JET

    /** How long the splash of a landed drop lasts. */
    const val SPLASH_MILLIS = 300L

    /** How far a drop can carry short of the full reach: from 1 - this up to 1. */
    const val REACH_SPREAD = 0.22f

    /** Launches in one turn: what a drop's own variation is keyed to, so that turns repeat. */
    private val LAUNCHES_PER_TURN = (TURN_MILLIS / LAUNCH_MILLIS).toInt()

    init {
        // Whole launches in a flight and in a turn, or the picture would not repeat each turn.
        check(LAUNCH_MILLIS * DROPS_PER_JET == FLIGHT_MILLIS && TURN_MILLIS % LAUNCH_MILLIS == 0L)
    }

    /**
     * One drop of a jet in the air.
     *
     * @property angle which way it flies over the ground (radians): the nozzle's direction when it left.
     * @property reach how far out it is (0 head, 1 edge of the sprinkler's reach).
     * @property lift how high it is, as a share of the top of its arc (0 at the head and on landing).
     * @property alpha how clear it is: it fades in leaving the nozzle and thins out as it lands.
     * @property flight how far along its flight it is (0 just launched, 1 landing), for drawing its streak.
     */
    data class Drop(val angle: Float, val reach: Float, val alpha: Float, val lift: Float = 0f, val flight: Float = 0f)

    /** Where a drop came down: [progress] 0 as it lands, 1 as its ring has spread and gone. */
    data class Splash(val angle: Float, val reach: Float, val progress: Float)

    /** The picture at one moment: the frame, the head's angle, the drops in the air and the splashes. */
    data class Frame(val index: Int, val headAngle: Float, val drops: List<Drop>, val splashes: List<Splash> = emptyList())

    private val STILL = Frame(index = 0, headAngle = 0f, drops = emptyList())

    /** The direction the nozzle of [jet] points at [clockMillis]. */
    fun nozzleAngle(clockMillis: Long, jet: Int): Float =
        (clockMillis.mod(TURN_MILLIS).toDouble() / TURN_MILLIS * 2 * PI + jet * 2 * PI / JETS).toFloat()

    /** The height of a drop [flight] of the way along its arc, as a share of the top: a parabola. */
    fun arc(flight: Float): Float = 4f * flight * (1f - flight)

    /** How far drop number [launch] of [jet] carries, as a share of the full reach. */
    fun carry(launch: Long, jet: Int): Float {
        val key = launch.mod(LAUNCHES_PER_TURN.toLong()).toInt() * 7 + jet * 131
        // A small fixed hash: some drops carry the whole way, some fall a little short.
        val h = ((key * 2_654_435_761L) ushr 7 and 0xFFFF).toFloat() / 0xFFFF
        return 1f - REACH_SPREAD * h
    }

    /** The animation at [clockMillis]; [running] false gives the still, dry first frame. */
    fun frameAt(clockMillis: Long, running: Boolean): Frame {
        if (!running) return STILL
        val turn = (clockMillis.mod(TURN_MILLIS)).toFloat() / TURN_MILLIS
        val head = (turn * 2 * PI).toFloat()
        val drops = ArrayList<Drop>(JETS * DROPS_PER_JET)
        val splashes = ArrayList<Splash>()
        // The newest launch so far; those still in the air or splashing are the ones before it.
        val newest = floor(clockMillis.toDouble() / LAUNCH_MILLIS).toLong()
        val splashLaunches = (SPLASH_MILLIS / LAUNCH_MILLIS).toInt()
        for (jet in 0 until JETS) {
            for (back in 0 until DROPS_PER_JET + splashLaunches) {
                val launch = newest - back
                val launchedAt = launch * LAUNCH_MILLIS
                val age = clockMillis - launchedAt
                val angle = nozzleAngle(launchedAt, jet)
                val carry = carry(launch, jet)
                if (age < FLIGHT_MILLIS) {
                    val flight = age.toFloat() / FLIGHT_MILLIS
                    // Fades in as it leaves the nozzle, and thins as it nears the ground.
                    val alpha = (flight / 0.12f).coerceAtMost(1f) * (1f - ((flight - 0.75f) / 0.25f).coerceIn(0f, 1f) * 0.6f)
                    drops += Drop(angle = angle, reach = flight * carry, alpha = alpha, lift = arc(flight), flight = flight)
                } else if (age < FLIGHT_MILLIS + SPLASH_MILLIS) {
                    splashes += Splash(angle = angle, reach = carry, progress = (age - FLIGHT_MILLIS).toFloat() / SPLASH_MILLIS)
                }
            }
        }
        return Frame(index = (turn * FRAME_COUNT).toInt().coerceIn(0, FRAME_COUNT - 1), headAngle = head, drops = drops, splashes = splashes)
    }

    /** The sprite for one frame, when artwork for it has been packed. */
    fun spriteIdFor(frame: Int): String = TileSetDefinition.sprinklerSprayId(frame)
}
