package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WeatherType

/**
 * A little snow lying along the top edges of the roofs (the cottage's, the hut's, the well's) and
 * of the fences' rails while it snows (#fb-1107): a thin white band drawn in code over the
 * pictures, no picture of its own. The ground's thin cover is something else ([WeatherType.SNOWY]
 * alone decides that one).
 *
 * The lines are set by hand for each building, like the points visitors settle on
 * ([com.studioguu.miniaturegarden.domain.model.CreaturePerches]): offsets from the ground point
 * under the middle of the footprint, in tile widths, across (to the right, as the picture faces)
 * and up. A building turned the other way has them mirrored. A fence has its band along the top
 * of each rail from post to post, and a cap on each post, so that a run of it has no gaps.
 *
 * It lies thicker the longer it has snowed and goes again once it stops ([depthAt]).
 */
object EdgeSnow {

    /** One line of snow: points as offsets from the base, (across, up) in tile widths. */
    data class Line(val points: List<Pair<Float, Float>>)

    private fun line(vararg points: Pair<Float, Float>) = Line(points.toList())

    /**
     * Set against each picture with tools/ (an overlay of the lines on the picture in the
     * garden's slant): the ridge and the verges of the roof, not the whole of it.
     */
    private val BY_HAND: Map<String, List<Line>> = mapOf(
        // The ridge, the near roof's two verges, and the gable's far verge.
        StructureData.COTTAGE.objectId to listOf(
            line(-0.65f to 1.965f, 0.71f to 1.49f),
            line(-0.655f to 1.90f, -1.10f to 1.0f),
            line(0.70f to 1.42f, 0.15f to 0.66f),
            line(0.74f to 1.43f, 1.215f to 0.99f),
        ),
        // The one-sloped roof: its high back edge and the two verges running down from it.
        StructureData.HUT.objectId to listOf(
            line(-0.81f to 1.34f, -0.06f to 1.635f),
            line(-0.81f to 1.33f, -0.005f to 0.66f),
            line(-0.05f to 1.635f, 0.81f to 0.96f),
        ),
        // The little roof over the well: its ridge, the far verge, and the eave at the left.
        StructureData.WELL.objectId to listOf(
            line(-0.404f to 0.597f, -0.279f to 0.675f, 0.112f to 0.83f),
            line(0.119f to 0.823f, 0.258f to 0.627f),
        ),
    )

    /** The lines on a building of [objectId], turned as it is; null for anything without. */
    fun linesOf(objectId: String, flipped: Boolean): List<Line>? {
        val lines = BY_HAND[objectId] ?: return null
        return if (!flipped) lines else lines.map { Line(it.points.map { (across, up) -> -across to up }) }
    }

    /** Whether [objectId] has snow on it while it lies: the buildings above. */
    fun hasLines(objectId: String): Boolean = objectId in BY_HAND

    /** How high the top of a fence's rail is, in tile widths, measured on its picture; null for the kerb. */
    fun railTop(kind: RunKind): Float? = when (kind) {
        RunKind.WOOD_FENCE -> 0.254f
        RunKind.BAMBOO_FENCE -> 0.244f
        RunKind.WHITE_FENCE -> 0.215f
        RunKind.BRICK_EDGING -> null
    }

    /** How high the top of a fence's post is (every kind's is the same picture height). */
    const val POST_TOP = 0.282f

    /** The top of a fence standing alone (its whole picture, seen square on): (half its width, height). */
    fun aloneTop(kind: RunKind): Pair<Float, Float>? = when (kind) {
        RunKind.WOOD_FENCE -> 0.15f to 0.245f
        RunKind.BAMBOO_FENCE -> 0.17f to 0.255f
        RunKind.WHITE_FENCE -> 0.15f to 0.247f
        RunKind.BRICK_EDGING -> null
    }

    /** How long it has to snow for the snow to lie at its fullest. */
    const val SETTLE_MILLIS = 40L * 60 * 1000

    /** How long, once it stops, the fullest snow takes to go. */
    const val MELT_MILLIS = 90L * 60 * 1000

    private const val STEP_MILLIS = 60L * 1000

    /**
     * How much snow lies on the edges at [now], 0 (none) to 1 (all there is): it builds up over
     * [SETTLE_MILLIS] of snowing and goes over [MELT_MILLIS] of anything else, read off [forecast]
     * minute by minute over the time that can still show.
     */
    fun depthAt(forecast: WeatherForecast, now: Long): Float {
        var depth = 0f
        var t = now - SETTLE_MILLIS - MELT_MILLIS
        while (t < now) {
            depth = if (forecast.at(t) == WeatherType.SNOWY) {
                (depth + STEP_MILLIS.toFloat() / SETTLE_MILLIS).coerceAtMost(1f)
            } else {
                (depth - STEP_MILLIS.toFloat() / MELT_MILLIS).coerceAtLeast(0f)
            }
            t += STEP_MILLIS
        }
        return depth
    }

    /** The snow's white, a touch blue. */
    private val SNOW = Color(0xFFF6F9FF)

    /** How wide the band is at its fullest, in tile widths: thin. */
    private const val BAND_WIDTH = 0.03f

    /** Draws [lines] about [base] as [depth] of snow, [unit] being a tile's width on screen. */
    fun DrawScope.drawEdgeSnow(base: Offset, unit: Float, lines: List<Line>, depth: Float) {
        if (depth <= 0f) return
        for (line in lines) {
            drawBand(line.points.map { (across, up) -> base + Offset(across * unit, -up * unit) }, unit, depth)
        }
    }

    /** Draws a band of [depth] of snow along [points] on screen. */
    fun DrawScope.drawBand(points: List<Offset>, unit: Float, depth: Float) {
        if (depth <= 0f || points.size < 2) return
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
        }
        val width = unit * BAND_WIDTH * (0.45f + 0.55f * depth)
        // A soft edge, then the white; lifted by half its width, to lie on the edge, not across it.
        withLift(width * 0.4f) {
            drawPath(path, SNOW.copy(alpha = 0.35f * depth), style = Stroke(width * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, SNOW.copy(alpha = 0.92f * depth), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    private inline fun DrawScope.withLift(by: Float, block: DrawScope.() -> Unit) {
        drawContext.transform.translate(0f, -by)
        block()
        drawContext.transform.translate(0f, by)
    }
}
