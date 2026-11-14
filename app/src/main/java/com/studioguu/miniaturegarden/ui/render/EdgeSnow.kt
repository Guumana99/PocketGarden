package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
        // The ridge as it was (#fb-1113 2). Not the gable's sloping verges: snow slides off those.
        // FB1114: no heap along the near roof's eave any more: it stood out in front of the roof as
        // a big lump; the snow over the roof comes to the eave's edge instead (PictureSnow). The hut
        // and the well have no heaps along their edges either, only the snow over their roofs
        // (PictureSnow, #fb-1113 3).
        StructureData.COTTAGE.objectId to listOf(
            line(-0.65f to 1.965f, 0.71f to 1.49f),
        ),
    )

    /**
     * The roof faces the viewer sees, as outlines measured on each picture (#fb-1110): snow lies
     * over the whole of them, not only along their edges. Same offsets as the lines.
     */
    // FB1114: the cottage's and the hut's out to the outer edges of their roofs, the verges' boards
    // and the ends of the eaves' tiles (a course of tiles was left bare along each). The cottage's
    // eave bends with the slant: a point in its middle.
    private val FACES: Map<String, List<Line>> = mapOf(
        StructureData.COTTAGE.objectId to listOf(line(-0.65f to 1.977f, 0.764f to 1.455f, 0.179f to 0.562f, -0.513f to 0.85f, -1.118f to 0.983f)),
        StructureData.HUT.objectId to listOf(line(-0.844f to 1.333f, -0.037f to 1.66f, 0.84f to 0.94f, 0.033f to 0.633f)),
        StructureData.WELL.objectId to listOf(line(-0.4f to 0.597f, 0.107f to 0.831f, 0.26f to 0.629f, -0.2125f to 0.494f)),
    )

    /** The roof faces of a building of [objectId], turned as it is; null for anything without. */
    fun facesOf(objectId: String, flipped: Boolean): List<Line>? {
        val faces = FACES[objectId] ?: return null
        return if (!flipped) faces else faces.map { Line(it.points.map { (across, up) -> -across to up }) }
    }

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

    /** The shade under a heap of snow and on its lower side: blue-grey, as snow's shadows are. */
    private val SNOW_SHADE = Color(0xFF8FA3C2)

    /** The lit top of a heap. */
    private val SNOW_LIGHT = Color(0xFFFFFFFF)

    /** How much of the roof a solid cover hides: the rows of tiles only just show through (device feedback 11/11). */
    const val SOLID_ALPHA = 0.95f

    /** A solid cover's eave: white going a little blue-grey in its own shade, not the see-through grey. */
    private val SOLID_EAVE = Color(0xFFDCE4F0)

    /** The roofs whose snow lies as a solid cover. */
    val SOLID_ROOFS = setOf(StructureData.COTTAGE.objectId)

    /**
     * How thick a heap of snow is at its fullest, in tile widths (#fb-1110: it read as a thin white
     * line before; now it has body, a lumpy top, rounded ends and a shadow under it).
     */
    private const val BAND_WIDTH = 0.05f

    /** How far apart the lumps along a heap are, in tile widths. */
    private const val LUMP_SPACING = 0.045f

    /** Draws [lines] about [base] as [depth] of snow, [unit] being a tile's width on screen. */
    fun DrawScope.drawEdgeSnow(base: Offset, unit: Float, lines: List<Line>, depth: Float) {
        if (depth <= 0f) return
        lines.forEachIndexed { i, line ->
            drawBand(line.points.map { (across, up) -> base + Offset(across * unit, -up * unit) }, unit, depth, seed = i * 7)
        }
    }

    /**
     * Snow over the whole of each roof face in [faces] (#fb-1110: the well's near face had none):
     * a soft white cover, lighter at the top and blue-grey towards the eave, thicker as it lies.
     * A [solid] cover (the cottage's, device feedback 11/11: its red tiles showed through pink, like a
     * sheet laid over them) lets only a trace of the roof under it through, enough to make out the
     * rows of tiles; the others stay as they were.
     */
    fun DrawScope.drawRoofSnow(base: Offset, unit: Float, faces: List<Line>, depth: Float, solid: Boolean = false) {
        if (depth <= 0f) return
        for (face in faces) {
            if (face.points.size < 3) continue
            val points = face.points.map { (across, up) -> base + Offset(across * unit, -up * unit) }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (p in points.drop(1)) lineTo(p.x, p.y)
                close()
            }
            val top = points.minOf { it.y }
            val bottom = points.maxOf { it.y }
            drawPath(
                path,
                if (solid) {
                    Brush.verticalGradient(
                        0f to SNOW.copy(alpha = SOLID_ALPHA * depth),
                        0.7f to SNOW.copy(alpha = SOLID_ALPHA * depth),
                        1f to SOLID_EAVE.copy(alpha = SOLID_ALPHA * depth),
                        startY = top, endY = bottom,
                    )
                } else {
                    Brush.verticalGradient(
                        0f to SNOW.copy(alpha = 0.92f * depth),
                        0.7f to SNOW.copy(alpha = 0.86f * depth),
                        1f to SNOW_SHADE.copy(alpha = 0.55f * depth),
                        startY = top, endY = bottom,
                    )
                },
            )
        }
    }

    /**
     * Draws a heap of [depth] of snow lying along [points] on screen: a body with a lumpy top and
     * rounded ends, a blue-grey shadow just under it and a lit line along its top. One path for
     * the body (the shadow is the same path, lower), so it costs about what the old band did.
     */
    fun DrawScope.drawBand(points: List<Offset>, unit: Float, depth: Float, seed: Int = 0) {
        if (depth <= 0f || points.size < 2) return
        val thick = unit * BAND_WIDTH * (0.4f + 0.6f * depth)
        val samples = sample(points, unit * LUMP_SPACING)
        val total = samples.last().second
        val tops = ArrayList<Offset>(samples.size)
        val bottoms = ArrayList<Offset>(samples.size)
        samples.forEachIndexed { i, (p, along) ->
            // Rounded ends: the heap thins to nothing over about its own thickness at each end.
            val fromEnd = minOf(along, total - along)
            val end = kotlin.math.sqrt((fromEnd / (thick * 1.3f)).coerceIn(0f, 1f))
            val lump = 0.78f + 0.44f * noise(i + seed)
            tops += p - Offset(0f, thick * lump * end)
            bottoms += p + Offset(0f, thick * 0.28f * end)
        }
        val body = Path().apply {
            moveTo(tops[0].x, tops[0].y)
            // Soft lumps: through the middles, bending at each sample.
            for (i in 1 until tops.size) {
                val mid = (tops[i - 1] + tops[i]) / 2f
                quadraticTo(tops[i - 1].x, tops[i - 1].y, mid.x, mid.y)
            }
            lineTo(tops.last().x, tops.last().y)
            for (i in bottoms.indices.reversed()) lineTo(bottoms[i].x, bottoms[i].y)
            close()
        }
        // The shadow it throws on what it lies on, then the snow, then the light on its top.
        withLift(-thick * 0.45f) { drawPath(body, SNOW_SHADE.copy(alpha = 0.5f * depth)) }
        drawPath(body, SNOW.copy(alpha = 0.96f * depth))
        drawPath(body, SNOW_SHADE.copy(alpha = 0.18f * depth), style = Stroke(thick * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val ridge = Path().apply {
            moveTo(tops[0].x, tops[0].y)
            for (i in 1 until tops.size) lineTo(tops[i].x, tops[i].y + thick * 0.18f)
        }
        drawPath(ridge, SNOW_LIGHT.copy(alpha = 0.75f * depth), style = Stroke(thick * 0.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** Points along [line] about [step] apart (at least a few), each with how far along it is. */
    private fun sample(line: List<Offset>, step: Float): List<Pair<Offset, Float>> {
        val out = ArrayList<Pair<Offset, Float>>()
        var along = 0f
        out += line[0] to 0f
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val length = (b - a).getDistance()
            val count = maxOf(MIN_SAMPLES_PER_PIECE, (length / step).toInt())
            for (k in 1..count) {
                val t = k.toFloat() / count
                out += (a + (b - a) * t) to (along + length * t)
            }
            along += length
        }
        return out
    }

    private const val MIN_SAMPLES_PER_PIECE = 4

    /** A steady 0..1 for [i], the same every frame, so the lumps hold still. */
    private fun noise(i: Int): Float {
        val x = kotlin.math.sin(i * 12.9898f + 4.1414f) * 43758.547f
        return x - kotlin.math.floor(x)
    }

    private inline fun DrawScope.withLift(by: Float, block: DrawScope.() -> Unit) {
        drawContext.transform.translate(0f, -by)
        block()
        drawContext.transform.translate(0f, by)
    }
}
