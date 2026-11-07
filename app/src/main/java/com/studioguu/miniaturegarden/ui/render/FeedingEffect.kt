package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.studioguu.miniaturegarden.domain.model.Soil
import kotlin.math.PI
import kotlin.math.sin

/**
 * How fertilizer shows on its cell, by the time since it was given ([Soil.SOAK_MILLIS], then
 * [Soil.fillMillis] while the food rises a percent a second), all drawn in code:
 *
 * - while it soaks in, granules patter down onto the soil and sink into it;
 * - while the food fills, however long that takes, the soil glows softly and small bubbles rise
 *   from it, and a little gauge over the cell fills with the food (unless the panel's own bar is showing);
 * - when it is full, a small sparkle.
 *
 * @property granules whether the granules are falling.
 * @property glow how strongly the soil glows, 0 to 1.
 * @property bubbles whether bubbles rise.
 * @property gauge how much the gauge shows, 0 (not at all) to 1.
 * @property sparkle how far the closing sparkle is, 0 to 1; null outside it.
 */
data class FeedingLook(
    val granules: Boolean,
    val glow: Float,
    val bubbles: Boolean,
    val gauge: Float,
    val sparkle: Float?,
) {
    companion object {
        /** How long the closing sparkle lasts. */
        const val SPARKLE_MILLIS = 700L

        /** How long the glow and the gauge take to come and to go. */
        private const val FADE_MILLIS = 400f

        /**
         * The look [since] millis after fertilizer was given, whose food is full [feeding] millis
         * after it ([Soil.feedingMillis]); null when there is nothing to show.
         */
        fun at(since: Long, feeding: Long): FeedingLook? {
            if (since < 0L || since >= feeding + SPARKLE_MILLIS) return null
            val filling = since >= Soil.SOAK_MILLIS && since < feeding
            val inFill = (since - Soil.SOAK_MILLIS).toFloat()
            val glow = when {
                since < Soil.SOAK_MILLIS -> 0f
                since < feeding -> (inFill / FADE_MILLIS).coerceAtMost(1f)
                else -> (1f - (since - feeding) / FADE_MILLIS).coerceAtLeast(0f)
            }
            val gauge = when {
                since < feeding -> (since / FADE_MILLIS).coerceAtMost(1f)
                else -> (1f - (since - feeding) / FADE_MILLIS).coerceAtLeast(0f)
            }
            return FeedingLook(
                granules = since < Soil.SOAK_MILLIS,
                glow = glow,
                bubbles = filling,
                gauge = gauge,
                sparkle = if (since >= feeding) (since - feeding).toFloat() / SPARKLE_MILLIS else null,
            )
        }
    }
}

/** A number in [0, 1) fixed by [i], [k] and [salt]: the same granule falls the same way every frame. */
private fun scatter(i: Int, k: Int, salt: Int): Float {
    var h = i * 0x27D4EB2D + k * 0x165667B1 + salt * 0x61C88647
    h = (h xor (h ushr 15)) * 0x2C1B3C6D
    h = h xor (h ushr 12)
    return (h and 0xFFFF) / 65536f
}

/**
 * The soil of the cell glowing as it takes the food in: [diamond] is the cell's outline on
 * screen, [look]'s glow how strong, breathing a little with [since].
 */
fun DrawScope.drawFeedingGlow(diamond: Path, look: FeedingLook, since: Long) {
    if (look.glow <= 0f) return
    val breath = 0.75f + 0.25f * sin(since / 420.0 * PI).toFloat()
    drawPath(diamond, FEED_GLOW.copy(alpha = 0.3f * look.glow * breath))
}

/**
 * Granules falling onto the cell whose middle is [ground] and sinking into it, and then bubbles
 * rising from it, [since] millis after the fertilizer was given; [unit] is a tile's width.
 */
fun DrawScope.drawFeedingSoil(ground: Offset, unit: Float, look: FeedingLook, since: Long, seed: Int) {
    if (look.granules) {
        for (i in 0 until GRANULES) {
            val startAt = scatter(i, seed, 1) * GRANULE_SPREAD_MILLIS
            val t = since - startAt
            if (t < 0f) continue
            val land = ground + Offset((scatter(i, seed, 2) - 0.5f) * unit * 0.5f, (scatter(i, seed, 3) - 0.5f) * unit * 0.22f)
            val r = unit * (0.02f + 0.012f * scatter(i, seed, 4))
            val colour = if (scatter(i, seed, 5) < 0.6f) GRANULE_LIGHT else GRANULE_DARK
            if (t < GRANULE_FALL_MILLIS) {
                val f = t / GRANULE_FALL_MILLIS
                val height = unit * (0.55f + 0.2f * scatter(i, seed, 6)) * (1f - f * f)
                drawCircle(colour, r, land - Offset(0f, height))
            } else {
                // Sinking in: smaller and fainter, with a damp patch where it went.
                val s = ((t - GRANULE_FALL_MILLIS) / GRANULE_SOAK_MILLIS).coerceIn(0f, 1f)
                if (s >= 1f) continue
                drawOval(DAMP.copy(alpha = 0.22f * (1f - s)), Offset(land.x - r * 2f, land.y - r), Size(r * 4f, r * 2f))
                drawCircle(colour.copy(alpha = 1f - s), r * (1f - 0.6f * s), land)
            }
        }
    }
    if (look.bubbles) {
        val inFill = since - Soil.SOAK_MILLIS
        for (i in 0 until BUBBLES) {
            val phase = ((inFill + scatter(i, seed, 7) * BUBBLE_MILLIS) % BUBBLE_MILLIS) / BUBBLE_MILLIS
            val at = ground + Offset((scatter(i, seed, 8) - 0.5f) * unit * 0.46f, (scatter(i, seed, 9) - 0.5f) * unit * 0.2f)
            val rise = unit * 0.3f * phase
            val r = unit * (0.016f + 0.02f * phase)
            val alpha = sin(phase * PI).toFloat() * 0.85f
            drawCircle(BUBBLE.copy(alpha = alpha * 0.45f), r, at - Offset(0f, rise))
            drawCircle(BUBBLE.copy(alpha = alpha), r, at - Offset(0f, rise), style = Stroke(width = unit * 0.006f))
            // A glint on its top.
            drawCircle(Color.White.copy(alpha = alpha), r * 0.28f, at - Offset(r * 0.35f, rise + r * 0.35f))
        }
    }
}

/**
 * The small gauge over the cell, [level] full, its middle at [at]; and the sparkle there once the
 * food is full.
 */
fun DrawScope.drawFeedingGauge(at: Offset, unit: Float, look: FeedingLook, level: Float, showGauge: Boolean) {
    if (showGauge && look.gauge > 0f) {
        val w = unit * 0.42f
        val h = unit * 0.055f
        val topLeft = at - Offset(w / 2f, h / 2f)
        val corner = CornerRadius(h / 2f)
        drawRoundRect(GAUGE_BACK.copy(alpha = 0.55f * look.gauge), topLeft - Offset(unit * 0.008f, unit * 0.008f), Size(w + unit * 0.016f, h + unit * 0.016f), CornerRadius(h))
        drawRoundRect(GAUGE_EMPTY.copy(alpha = look.gauge), topLeft, Size(w, h), corner)
        val filled = w * level.coerceIn(0f, 1f)
        if (filled > 0f) drawRoundRect(GAUGE_FULL.copy(alpha = look.gauge), topLeft, Size(filled, h), corner)
    }
    val sparkle = look.sparkle ?: return
    val grow = if (sparkle < 0.4f) sparkle / 0.4f else 1f
    val fade = if (sparkle < 0.4f) 1f else 1f - (sparkle - 0.4f) / 0.6f
    val r = unit * 0.17f * grow
    val thin = r * 0.2f
    val star = Path().apply {
        moveTo(at.x, at.y - r); lineTo(at.x + thin, at.y - thin); lineTo(at.x + r, at.y); lineTo(at.x + thin, at.y + thin)
        lineTo(at.x, at.y + r); lineTo(at.x - thin, at.y + thin); lineTo(at.x - r, at.y); lineTo(at.x - thin, at.y - thin); close()
    }
    drawCircle(SPARKLE.copy(alpha = 0.45f * fade), r * 0.6f, at)
    drawPath(star, SPARKLE.copy(alpha = fade))
    drawCircle(Color.White.copy(alpha = fade), r * 0.16f, at)
}

private const val GRANULES = 16
private const val GRANULE_SPREAD_MILLIS = 1_150f
private const val GRANULE_FALL_MILLIS = 420f
private const val GRANULE_SOAK_MILLIS = 380f
private const val BUBBLES = 7
private const val BUBBLE_MILLIS = 1_500f

private val GRANULE_LIGHT = Color(0xFFF1E2B8)
private val GRANULE_DARK = Color(0xFFB98A52)
private val DAMP = Color(0xFF3B2614)
private val FEED_GLOW = Color(0xFFFFF0A0)
private val BUBBLE = Color(0xFFFDFBE8)
private val GAUGE_BACK = Color(0xFF3B2A1A)
private val GAUGE_EMPTY = Color(0xFFE9DFC6)
private val GAUGE_FULL = Color(0xFF8BC34A)
private val SPARKLE = Color(0xFFFFE680)
