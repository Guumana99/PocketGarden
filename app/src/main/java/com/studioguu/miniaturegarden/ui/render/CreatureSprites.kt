package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.studioguu.miniaturegarden.domain.model.Creature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

// Stand-in pictures of the visitors, drawn in code until their artwork comes (see ROADMAP).
// Every one is small and moves gently: they are there to be noticed, not to be watched.

/**
 * Draws [creature] standing (or hovering) at [base], a point on the ground, at a size fitting a
 * tile [unit] wide. [clock] (millis) drives its little movements; a fixed value gives a still
 * picture, as in the nature notes.
 */
fun DrawScope.drawCreature(creature: Creature, base: Offset, unit: Float, clock: Long) {
    when (creature) {
        Creature.BUTTERFLY -> drawButterfly(base, unit, clock)
        Creature.LADYBUG -> drawLadybug(base, unit, clock)
        Creature.BIRD -> drawBird(base, unit, clock)
        Creature.SQUIRREL -> drawSquirrel(base, unit, clock)
        Creature.FROG -> drawFrog(base, unit, clock)
        Creature.DRAGONFLY -> drawDragonfly(base, unit, clock)
    }
}

/** Whether [creature] keeps to the air: it casts no shadow at its feet. */
fun Creature.flies(): Boolean = this == Creature.BUTTERFLY || this == Creature.DRAGONFLY

/** A small soft shadow under a creature on the ground. */
fun DrawScope.drawCreatureShadow(base: Offset, unit: Float, width: Float) {
    drawOval(Color.Black.copy(alpha = 0.18f), base - Offset(unit * width / 2f, unit * width * 0.18f), Size(unit * width, unit * width * 0.36f))
}

/** 0 to 1 and back, [periodMillis] for the whole way, starting at [phase]. */
private fun wave(clock: Long, periodMillis: Double, phase: Double = 0.0): Float =
    (0.5 + 0.5 * sin(clock / periodMillis * 2 * Math.PI + phase)).toFloat()

private val WING_ORANGE = Color(0xFFF6B25A)
private val WING_PALE = Color(0xFFFFE7B8)
private val BODY_DARK = Color(0xFF4E342E)

/** A butterfly hovering over the spot, drifting in a slow loop, its wings beating. */
private fun DrawScope.drawButterfly(base: Offset, unit: Float, clock: Long) {
    val drift = Offset(
        x = sin(clock / 1700.0).toFloat() * unit * 0.09f,
        y = sin(clock / 850.0).toFloat() * unit * 0.04f,
    )
    val centre = base + Offset(0f, -unit * 0.42f) + drift
    // The wings fold up and open again: drawn narrower as they close.
    val open = 0.25f + 0.75f * abs(sin(clock / 160.0).toFloat())
    val upper = Size(unit * 0.11f * open, unit * 0.09f)
    val lower = Size(unit * 0.08f * open, unit * 0.065f)
    for (side in floatArrayOf(-1f, 1f)) {
        scale(scaleX = side, scaleY = 1f, pivot = centre) {
            drawOval(WING_ORANGE, centre + Offset(0f, -upper.height * 0.9f), upper)
            drawOval(WING_PALE, centre + Offset(upper.width * 0.25f, -upper.height * 0.7f), upper * 0.45f)
            drawOval(WING_ORANGE, centre + Offset(0f, -lower.height * 0.1f), lower)
        }
    }
    drawLine(BODY_DARK, centre + Offset(0f, -unit * 0.045f), centre + Offset(0f, unit * 0.045f), unit * 0.014f, StrokeCap.Round)
}

private val LADYBUG_RED = Color(0xFFE0453A)

/** A ladybird at the foot of a crop, pottering a little to and fro. */
private fun DrawScope.drawLadybug(base: Offset, unit: Float, clock: Long) {
    val walk = (wave(clock, 5200.0) - 0.5f) * unit * 0.1f
    val at = base + Offset(unit * 0.08f + walk, -unit * 0.035f)
    val r = unit * 0.035f
    drawCreatureShadow(at + Offset(0f, r * 0.9f), unit, 0.08f)
    drawCircle(BODY_DARK, r * 0.55f, at + Offset(r * (if (walk >= 0f) 0.9f else -0.9f), 0f))
    drawCircle(LADYBUG_RED, r, at)
    drawLine(BODY_DARK, at + Offset(0f, -r), at + Offset(0f, r), r * 0.18f)
    for (spot in listOf(Offset(-0.45f, -0.35f), Offset(0.45f, -0.35f), Offset(-0.4f, 0.4f), Offset(0.4f, 0.4f))) {
        drawCircle(BODY_DARK, r * 0.2f, at + spot * r)
    }
}

private val BIRD_BROWN = Color(0xFF9C7A54)
private val BIRD_BELLY = Color(0xFFF1E1C6)
private val BIRD_BEAK = Color(0xFFF2A93B)

/** A small bird perched there: now and then a little hop, and its head turns. */
private fun DrawScope.drawBird(base: Offset, unit: Float, clock: Long) {
    // One short hop every few seconds.
    val beat = (clock % 3600L) / 3600f
    val hop = if (beat < 0.12f) sin(beat / 0.12f * Math.PI).toFloat() * unit * 0.04f else 0f
    val facing = if ((clock / 5300L) % 2L == 0L) 1f else -1f
    val body = base + Offset(0f, -unit * 0.07f - hop)
    if (hop == 0f) drawCreatureShadow(base, unit, 0.12f)
    scale(scaleX = facing, scaleY = 1f, pivot = body) {
        // Tail, body, belly, head, beak, eye.
        val tail = Path().apply {
            moveTo(body.x - unit * 0.05f, body.y - unit * 0.005f)
            lineTo(body.x - unit * 0.12f, body.y - unit * 0.045f)
            lineTo(body.x - unit * 0.11f, body.y + unit * 0.01f)
            close()
        }
        drawPath(tail, BIRD_BROWN)
        drawOval(BIRD_BROWN, body - Offset(unit * 0.065f, unit * 0.045f), Size(unit * 0.13f, unit * 0.09f))
        drawOval(BIRD_BELLY, body - Offset(unit * 0.02f, unit * 0.005f), Size(unit * 0.08f, unit * 0.045f))
        val head = body + Offset(unit * 0.05f, -unit * 0.05f)
        drawCircle(BIRD_BROWN, unit * 0.038f, head)
        val beak = Path().apply {
            moveTo(head.x + unit * 0.03f, head.y - unit * 0.008f)
            lineTo(head.x + unit * 0.065f, head.y + unit * 0.002f)
            lineTo(head.x + unit * 0.03f, head.y + unit * 0.012f)
            close()
        }
        drawPath(beak, BIRD_BEAK)
        drawCircle(BODY_DARK, unit * 0.008f, head + Offset(unit * 0.012f, -unit * 0.008f))
        // Legs.
        drawLine(BIRD_BEAK, body + Offset(-unit * 0.01f, unit * 0.04f), body + Offset(-unit * 0.01f, unit * 0.07f + hop), unit * 0.008f)
        drawLine(BIRD_BEAK, body + Offset(unit * 0.015f, unit * 0.04f), body + Offset(unit * 0.015f, unit * 0.07f + hop), unit * 0.008f)
    }
}

private val SQUIRREL_RUST = Color(0xFFB8673A)
private val SQUIRREL_LIGHT = Color(0xFFE8B98A)

/** A squirrel sitting up on the lawn, its big tail swaying. */
private fun DrawScope.drawSquirrel(base: Offset, unit: Float, clock: Long) {
    drawCreatureShadow(base, unit, 0.16f)
    val body = base + Offset(0f, -unit * 0.08f)
    // The tail: a thick curl behind the body, swaying slowly.
    val sway = (wave(clock, 2600.0) - 0.5f) * 14f
    rotate(degrees = sway, pivot = body + Offset(-unit * 0.04f, unit * 0.05f)) {
        drawOval(SQUIRREL_RUST, body + Offset(-unit * 0.13f, -unit * 0.12f), Size(unit * 0.1f, unit * 0.17f))
        drawOval(SQUIRREL_LIGHT, body + Offset(-unit * 0.11f, -unit * 0.1f), Size(unit * 0.05f, unit * 0.09f))
    }
    drawOval(SQUIRREL_RUST, body - Offset(unit * 0.045f, unit * 0.055f), Size(unit * 0.09f, unit * 0.12f))
    drawOval(SQUIRREL_LIGHT, body - Offset(unit * 0.015f, unit * 0.025f), Size(unit * 0.045f, unit * 0.075f))
    val head = body + Offset(unit * 0.02f, -unit * 0.08f)
    drawCircle(SQUIRREL_RUST, unit * 0.04f, head)
    drawCircle(SQUIRREL_RUST, unit * 0.014f, head + Offset(-unit * 0.012f, -unit * 0.038f))
    drawCircle(BODY_DARK, unit * 0.007f, head + Offset(unit * 0.017f, -unit * 0.005f))
    // A seed held in its paws, nibbled now and then.
    val nibble = if ((clock / 900L) % 3L == 0L) unit * 0.006f else 0f
    drawOval(Color(0xFF5D4A3A), body + Offset(unit * 0.02f, -unit * 0.035f - nibble), Size(unit * 0.02f, unit * 0.028f))
}

private val FROG_GREEN = Color(0xFF7DB356)
private val FROG_LIGHT = Color(0xFFC9E3A5)

/** A frog sitting by the water, its throat swelling as it breathes. */
private fun DrawScope.drawFrog(base: Offset, unit: Float, clock: Long) {
    drawCreatureShadow(base, unit, 0.15f)
    val body = base + Offset(0f, -unit * 0.04f)
    drawOval(FROG_GREEN, body - Offset(unit * 0.075f, unit * 0.045f), Size(unit * 0.15f, unit * 0.08f))
    val breath = wave(clock, 1400.0)
    val throat = unit * (0.025f + 0.012f * breath)
    drawOval(FROG_LIGHT, body + Offset(-throat, -unit * 0.005f), Size(throat * 2f, max(throat * 1.1f, 1f)))
    for (side in floatArrayOf(-1f, 1f)) {
        val eye = body + Offset(side * unit * 0.04f, -unit * 0.05f)
        drawCircle(FROG_GREEN, unit * 0.022f, eye)
        drawCircle(Color.White, unit * 0.013f, eye)
        drawCircle(BODY_DARK, unit * 0.007f, eye)
    }
    // Front feet.
    for (side in floatArrayOf(-1f, 1f)) {
        drawOval(FROG_GREEN, body + Offset(side * unit * 0.055f - unit * 0.02f, unit * 0.02f), Size(unit * 0.04f, unit * 0.02f))
    }
    drawArc(BODY_DARK.copy(alpha = 0.6f), 20f, 140f, false, body + Offset(-unit * 0.03f, -unit * 0.035f), Size(unit * 0.06f, unit * 0.03f), style = Stroke(unit * 0.005f))
}

private val DRAGONFLY_BODY = Color(0xFFD9573B)
private val DRAGONFLY_WING = Color(0x88E3F2FA)
private val DRAGONFLY_WING_EDGE = Color(0xAA9FB8C4)

/**
 * A red dragonfly hovering over the water: it holds still, darts a little way and holds again,
 * its four clear wings shimmering.
 */
private fun DrawScope.drawDragonfly(base: Offset, unit: Float, clock: Long) {
    // Holds for most of each beat, then darts to the next spot of a slow figure.
    val beat = clock / 2400L
    val within = (clock % 2400L) / 2400f
    fun spot(n: Long) = Offset(sin(n * 1.7).toFloat() * unit * 0.08f, sin(n * 2.3).toFloat() * unit * 0.03f)
    val dart = ((within - 0.8f) / 0.2f).coerceIn(0f, 1f)
    val drift = spot(beat) + (spot(beat + 1) - spot(beat)) * dart
    val centre = base + Offset(0f, -unit * 0.38f) + drift
    val shimmer = 0.6f + 0.4f * abs(sin(clock / 60.0).toFloat())
    // Two pairs of long wings, spread flat.
    for (side in floatArrayOf(-1f, 1f)) {
        scale(scaleX = side, scaleY = 1f, pivot = centre) {
            for ((dy, length) in listOf(-0.012f to 0.12f, 0.012f to 0.1f)) {
                val wing = Size(unit * length, unit * 0.028f * shimmer)
                val at = centre + Offset(unit * 0.008f, unit * dy - wing.height / 2f)
                drawOval(DRAGONFLY_WING, at, wing)
                drawOval(DRAGONFLY_WING_EDGE, at, wing, style = Stroke(unit * 0.003f))
            }
        }
    }
    // A long thin tail, the body and big eyes.
    drawLine(DRAGONFLY_BODY, centre + Offset(0f, -unit * 0.01f), centre + Offset(0f, unit * 0.11f), unit * 0.012f, StrokeCap.Round)
    drawOval(DRAGONFLY_BODY, centre - Offset(unit * 0.012f, unit * 0.03f), Size(unit * 0.024f, unit * 0.045f))
    drawCircle(BODY_DARK, unit * 0.012f, centre + Offset(0f, -unit * 0.035f))
}

/** Pictures in the nature notes are still: the same moment every time. */
const val STILL_CLOCK = 400L
