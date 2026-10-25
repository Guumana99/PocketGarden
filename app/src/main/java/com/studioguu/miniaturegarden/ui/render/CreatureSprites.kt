package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureAction
import com.studioguu.miniaturegarden.domain.model.CreaturePose
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.Facing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// The visitors, as they go about the garden. Their pictures are stand-ins drawn in code until
// their artwork comes (see ROADMAP); whoever draws a visitor calls [drawVisitor], which takes the
// picture from the creatures' atlas ([SpriteAtlas.creatures]) as soon as it has one, so artwork
// can be packed in later without touching any drawing code.
//
// Every movement is made by moving one still picture as a whole: turned to face the other way,
// stretched, tilted, lifted, and for the fliers, each wing opened and closed on its own (the
// squirrel's tail swung). Nothing is drawn differently from moment to moment, so the artwork,
// one picture to a creature facing left, moves just as the stand-ins do (see ASSETS.md).

/** How high a visitor that flies keeps above its spot, in tile widths; 0 for those on the ground. */
val Creature.hoverHeight: Float get() = CreatureRoutes.cruiseAltitude(this)

/** Whether [creature] keeps to the air. */
fun Creature.flies(): Boolean = hoverHeight > 0f

/**
 * How a visitor's picture is moved at one moment: all of it about its feet, and its wings or
 * tail on their own.
 *
 * @property altitude how high it flies, in tile widths.
 * @property lift how far it is off the ground for a moment (a hop), in tile widths.
 * @property facing which way it faces; the pictures face left.
 * @property scaleX stretch across, about its feet: a pop, a jump.
 * @property tilt degrees it leans, about its feet, as the picture faces: less than 0 leans forward.
 * @property wingOpen how far a flier's wings are open, 0 (folded) to 1 (spread as drawn).
 * @property tail degrees the squirrel's tail is swung back from where it is drawn.
 * @property submerged a frog in a pond: the lower half of it is under the water.
 * @property shadow how dark its shadow is, 0 to 1.
 * @property whole drawn from its whole picture, not from its parts: the nature notes.
 */
data class VisitorLook(
    val altitude: Float = 0f,
    val lift: Float = 0f,
    val facing: Facing = Facing.LEFT,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val tilt: Float = 0f,
    val wingOpen: Float = 1f,
    val tail: Float = 0f,
    val submerged: Boolean = false,
    val shadow: Float = 1f,
    val whole: Boolean = false,
) {
    companion object {
        /** Standing (or hovering) still, as the nature notes show it. */
        fun still(creature: Creature) = VisitorLook(altitude = creature.hoverHeight, whole = true)

        /**
         * How [creature] looks in [pose], at [now] (millis; drives the wings and the little
         * movements). [flowerTop], how tall the flower it came for stands (tile widths), brings a
         * butterfly settling on it down onto its bloom rather than to the route's
         * [CreatureRoutes.PERCH_ALTITUDE]; its flight is bent to meet it on the way down.
         */
        fun of(creature: Creature, pose: CreaturePose, now: Long, flowerTop: Float? = null): VisitorLook {
            val base = VisitorLook(altitude = altitudeOf(creature, pose.altitude, flowerTop), facing = pose.facing)
            val p = pose.progress
            val look = when (creature) {
                Creature.SQUIRREL -> squirrel(base, pose, p, now)
                Creature.BIRD -> bird(base, pose, p)
                Creature.FROG -> frog(base, pose, p, now)
                Creature.LADYBUG -> ladybug(base, pose, now)
                Creature.BUTTERFLY -> butterfly(base, pose, now)
                Creature.DRAGONFLY -> dragonfly(base, pose, now)
            }
            return when (pose.action) {
                CreatureAction.APPEAR -> look.popped(popIn(p))
                CreatureAction.VANISH -> look.popped(popIn(1f - p))
                else -> look
            }
        }

        private fun squirrel(base: VisitorLook, pose: CreaturePose, p: Float, now: Long): VisitorLook = when (pose.action) {
            // Bounds from cell to cell, stretching out as it goes, its tail streaming behind.
            CreatureAction.STEP -> arc(p).let { a ->
                base.copy(lift = a * 0.07f, scaleX = 1f - 0.06f * a, scaleY = 1f + 0.1f * a, tilt = -8f * a, tail = 18f * a, shadow = 1f - 0.4f * a)
            }
            // Sits up, its tail swaying a little.
            else -> base.copy(tail = (wave(now, 2600.0) - 0.5f) * 14f)
        }

        private fun bird(base: VisitorLook, pose: CreaturePose, p: Float): VisitorLook = when (pose.action) {
            // Two little hops to a cell.
            CreatureAction.STEP -> arc((p * 2f) % 1f).let { a -> base.copy(lift = a * 0.035f, scaleY = 1f + 0.05f * a, shadow = 1f - 0.3f * a) }
            // Now and then it bends forward to peck.
            CreatureAction.STAND -> {
                val beat = pose.elapsed % PECK_EVERY
                val peck = if (beat in PECK_FROM until PECK_FROM + PECK_MILLIS) sin((beat - PECK_FROM).toFloat() / PECK_MILLIS * PI).toFloat() else 0f
                base.copy(tilt = -24f * peck)
            }
            else -> base
        }

        private fun frog(base: VisitorLook, pose: CreaturePose, p: Float, now: Long): VisitorLook = when (pose.action) {
            // Crouches, springs long and thin, lands squashed.
            CreatureAction.STEP -> {
                val a = arc(p)
                val squash = when {
                    p < 0.15f -> -(p / 0.15f)
                    p > 0.85f -> -((1f - p) / 0.15f)
                    else -> a
                }
                base.copy(lift = a * 0.08f, scaleX = 1f - 0.1f * squash, scaleY = 1f + 0.16f * squash, submerged = pose.inPond && a < 0.3f, shadow = 1f - 0.5f * a)
            }
            // Its throat swells as it breathes.
            else -> base.copy(scaleX = 1f + 0.02f * wave(now, 1400.0), scaleY = 1f - 0.015f * wave(now, 1400.0), submerged = pose.inPond)
        }

        private fun ladybug(base: VisitorLook, pose: CreaturePose, now: Long): VisitorLook = when (pose.action) {
            // Rocks a little from leg to leg.
            CreatureAction.STEP -> base.copy(tilt = (wave(now, 420.0) - 0.5f) * 8f)
            else -> base
        }

        private fun butterfly(base: VisitorLook, pose: CreaturePose, now: Long): VisitorLook = when (pose.action) {
            // Resting on a flower: the wings open and close, slowly.
            CreatureAction.PERCH -> base.copy(wingOpen = 0.35f + 0.65f * wave(pose.elapsed, 1800.0, PI / 2), shadow = 0.6f)
            // In the air: the wings beat, and it bobs as it goes.
            else -> base.copy(
                lift = sin(now / 430.0).toFloat() * 0.03f,
                wingOpen = 0.2f + 0.8f * abs(sin(now / 160.0).toFloat()),
                shadow = airShadow(Creature.BUTTERFLY, pose.altitude),
            )
        }

        private fun dragonfly(base: VisitorLook, pose: CreaturePose, now: Long): VisitorLook {
            val shimmer = 0.7f + 0.3f * abs(sin(now / 45.0).toFloat())
            return when (pose.action) {
                // A dart: leans into it.
                CreatureAction.FLY -> base.copy(wingOpen = shimmer, tilt = -10f * arc(pose.progress), shadow = airShadow(Creature.DRAGONFLY, pose.altitude))
                // Holding still in the air, all but a slight rise and fall.
                else -> base.copy(wingOpen = shimmer, lift = sin(now / 900.0).toFloat() * 0.012f, shadow = airShadow(Creature.DRAGONFLY, pose.altitude))
            }
        }

        private fun altitudeOf(creature: Creature, altitude: Float, flowerTop: Float?): Float {
            if (flowerTop == null) return altitude
            val cruise = creature.hoverHeight
            val near = ((cruise - altitude) / (cruise - CreatureRoutes.PERCH_ALTITUDE)).coerceIn(0f, 1f)
            return altitude + (flowerTop - CreatureRoutes.PERCH_ALTITUDE) * near
        }

        /** A flier's shadow: full over its flowers, fading as it climbs into the sky. */
        private fun airShadow(creature: Creature, altitude: Float): Float {
            val cruise = creature.hoverHeight
            return (1f - (altitude - cruise) / (CreatureRoutes.SKY_ALTITUDE - cruise)).coerceIn(0f, 1f)
        }

        private fun VisitorLook.popped(size: Float) = copy(scaleX = scaleX * size, scaleY = scaleY * size, shadow = shadow * size.coerceIn(0f, 1f))

        /** Up and down again over 0 to 1: the height of a hop. */
        private fun arc(p: Float): Float = sin(p.coerceIn(0f, 1f) * PI).toFloat()

        /** A pop: from nothing to a little more than its size, and back to its size, over 0 to 1. */
        internal fun popIn(p: Float): Float {
            if (p <= 0f) return 0f
            if (p >= 1f) return 1f
            val c1 = 2.2f
            val q = p - 1f
            return 1f + (c1 + 1f) * q * q * q + c1 * q * q
        }

        private const val PECK_EVERY = 1500L
        private const val PECK_FROM = 500L
        private const val PECK_MILLIS = 380L
    }
}

/**
 * Draws [creature] at [base], a point on the ground (or on what it sits on), moved as [look]
 * says: from its pictures in [atlas] (the creatures' atlas) when it has them
 * ([TileSetDefinition.assetIdFor], [TileSetDefinition.creaturePartIds]), and in code until then.
 * A picture stands with its anchor (its feet) on [base]; a flier is held [VisitorLook.altitude]
 * above it, its shadow left on the ground unless [shadow] is false.
 */
fun DrawScope.drawVisitor(
    creature: Creature,
    base: Offset,
    unit: Float,
    look: VisitorLook,
    atlas: SpriteAtlas,
    atlasImage: ImageBitmap?,
    shadow: Boolean = true,
) {
    if (shadow && look.shadow > 0f) {
        val size = min(abs(look.scaleX), 1.1f)
        drawCreatureShadow(base, unit, SHADOW_WIDTH.getValue(creature) * size, alpha = look.shadow)
    }
    val feet = base - Offset(0f, (look.altitude + look.lift) * unit)
    val picture: DrawScope.() -> Unit = {
        withTransform({
            translate(feet.x, feet.y)
            scale(if (look.facing == Facing.RIGHT) -look.scaleX else look.scaleX, look.scaleY, Offset.Zero)
            rotate(look.tilt, Offset.Zero)
        }) {
            if (atlasImage == null || !drawFromAtlas(creature, unit, look, atlas, atlasImage)) drawCreature(creature, unit, look)
        }
    }
    if (look.submerged) {
        // Half under the water, rings spreading round it.
        val waterline = base.y - unit * 0.035f
        clipRect(bottom = waterline, block = picture)
        drawRipples(Offset(base.x, waterline), unit, (System.currentTimeMillis() % RIPPLE_MILLIS) / RIPPLE_MILLIS.toFloat())
    } else {
        picture()
    }
}

/**
 * Draws [creature] from the atlas, its feet at the origin: from its parts when it has them all
 * (a flier's body and wings, the squirrel and its tail), or else its whole picture. A flier in
 * the nature notes ([VisitorLook.whole]) is its whole picture, wings spread, when there is one.
 * False when the atlas has neither.
 */
private fun DrawScope.drawFromAtlas(creature: Creature, unit: Float, look: VisitorLook, atlas: SpriteAtlas, image: ImageBitmap): Boolean {
    val scale = unit / atlas.manifest.tileWidth
    val whole = atlas[TileSetDefinition.assetIdFor(creature)]
    val body = atlas[TileSetDefinition.creatureBodyId(creature)]
    val parts = TileSetDefinition.creaturePartIds(creature).mapNotNull { atlas[it] }
    val useParts = body != null && parts.isNotEmpty() && parts.size == TileSetDefinition.creaturePartIds(creature).size &&
        !(look.whole && creature.flies() && whole != null)
    if (useParts) {
        // Where the parts join the body; they turn about that point.
        val hinge = body!!.attachmentAt(0f, 0f, scale, flipped = false)?.let { Offset(it.first, it.second) } ?: Offset.Zero
        if (creature.flies()) {
            // The far wing, the body, the near wing: each opens and closes about where it joins the body.
            scale(look.wingOpen, 1f, hinge) { drawSprite(image, parts[1], hinge, scale) }
            drawSprite(image, body, Offset.Zero, scale)
            scale(look.wingOpen, 1f, hinge) { drawSprite(image, parts[0], hinge, scale) }
        } else {
            // The tail, swung about where it joins the body, behind it.
            rotate(look.tail, hinge) { drawSprite(image, parts[0], hinge, scale) }
            drawSprite(image, body, Offset.Zero, scale)
        }
        return true
    }
    if (whole == null) return false
    drawSprite(image, whole, Offset.Zero, scale)
    return true
}

/**
 * Draws [creature]'s stand-in, drawn in code, with its feet at the origin, facing left, at a size
 * fitting a tile [unit] wide; [look] opens its wings or swings its tail.
 */
private fun DrawScope.drawCreature(creature: Creature, unit: Float, look: VisitorLook) {
    when (creature) {
        Creature.BUTTERFLY -> drawButterfly(unit, look.wingOpen)
        Creature.LADYBUG -> drawLadybug(unit)
        Creature.BIRD -> drawBird(unit)
        Creature.SQUIRREL -> drawSquirrel(unit, look.tail)
        Creature.FROG -> drawFrog(unit)
        Creature.DRAGONFLY -> drawDragonfly(unit, look.wingOpen)
    }
}

/** How wide each visitor's shadow is, in tile widths. */
private val SHADOW_WIDTH: Map<Creature, Float> = mapOf(
    Creature.BUTTERFLY to 0.12f,
    Creature.LADYBUG to 0.08f,
    Creature.BIRD to 0.12f,
    Creature.SQUIRREL to 0.16f,
    Creature.FROG to 0.15f,
    Creature.DRAGONFLY to 0.12f,
)

/** A small soft shadow under a creature on the ground. */
fun DrawScope.drawCreatureShadow(base: Offset, unit: Float, width: Float, alpha: Float = 1f) {
    drawOval(Color.Black.copy(alpha = 0.18f * alpha), base - Offset(unit * width / 2f, unit * width * 0.18f), Size(unit * width, unit * width * 0.36f))
}

/** Rings spreading on the water round [at], [phase] 0 to 1 through their spreading. */
private fun DrawScope.drawRipples(at: Offset, unit: Float, phase: Float) {
    for (ring in 0..1) {
        val f = (phase + ring * 0.5f) % 1f
        val width = unit * (0.1f + 0.16f * f)
        drawOval(
            Color.White.copy(alpha = 0.55f * (1f - f)),
            at - Offset(width / 2f, width * 0.2f),
            Size(width, width * 0.4f),
            style = Stroke(unit * 0.006f),
        )
    }
}

/** How long a ring on the water takes to spread. */
private const val RIPPLE_MILLIS = 2400L

/** 0 to 1 and back, [periodMillis] for the whole way, starting at [phase]. */
private fun wave(clock: Long, periodMillis: Double, phase: Double = 0.0): Float =
    (0.5 + 0.5 * sin(clock / periodMillis * 2 * PI + phase)).toFloat()

private val WING_ORANGE = Color(0xFFF6B25A)
private val WING_PALE = Color(0xFFFFE7B8)
private val BODY_DARK = Color(0xFF4E342E)

/** A butterfly, its wings spread [open] of the way; its feet are the foot of its body. */
private fun DrawScope.drawButterfly(unit: Float, open: Float) {
    val centre = Offset(0f, -unit * 0.045f)
    val upper = Size(unit * 0.11f, unit * 0.09f)
    val lower = Size(unit * 0.08f, unit * 0.065f)
    // The far wing a little smaller than the near one: it is seen from the front and the left.
    for ((side, size) in listOf(1f to 0.85f, -1f to 1f)) {
        scale(scaleX = side * open * size, scaleY = size, pivot = centre) {
            drawOval(WING_ORANGE, centre + Offset(0f, -upper.height * 0.9f), upper)
            drawOval(WING_PALE, centre + Offset(upper.width * 0.25f, -upper.height * 0.7f), upper * 0.45f)
            drawOval(WING_ORANGE, centre + Offset(0f, -lower.height * 0.1f), lower)
        }
    }
    drawLine(BODY_DARK, centre + Offset(0f, -unit * 0.045f), centre + Offset(0f, unit * 0.045f), unit * 0.014f, StrokeCap.Round)
}

private val LADYBUG_RED = Color(0xFFE0453A)

/** A ladybird, its head to the left. */
private fun DrawScope.drawLadybug(unit: Float) {
    val r = unit * 0.035f
    val at = Offset(0f, -r)
    drawCircle(BODY_DARK, r * 0.55f, at + Offset(-r * 0.9f, 0f))
    drawCircle(LADYBUG_RED, r, at)
    drawLine(BODY_DARK, at + Offset(0f, -r), at + Offset(0f, r), r * 0.18f)
    for (spot in listOf(Offset(-0.45f, -0.35f), Offset(0.45f, -0.35f), Offset(-0.4f, 0.4f), Offset(0.4f, 0.4f))) {
        drawCircle(BODY_DARK, r * 0.2f, at + spot * r)
    }
}

private val BIRD_BROWN = Color(0xFF9C7A54)
private val BIRD_BELLY = Color(0xFFF1E1C6)
private val BIRD_BEAK = Color(0xFFF2A93B)

/** A small bird, facing left; it pecks by leaning forward about its feet. */
private fun DrawScope.drawBird(unit: Float) {
    val body = Offset(0f, -unit * 0.07f)
    // Tail, body, belly, head, beak, eye.
    val tail = Path().apply {
        moveTo(body.x + unit * 0.05f, body.y - unit * 0.005f)
        lineTo(body.x + unit * 0.12f, body.y - unit * 0.045f)
        lineTo(body.x + unit * 0.11f, body.y + unit * 0.01f)
        close()
    }
    drawPath(tail, BIRD_BROWN)
    drawOval(BIRD_BROWN, body - Offset(unit * 0.065f, unit * 0.045f), Size(unit * 0.13f, unit * 0.09f))
    drawOval(BIRD_BELLY, body - Offset(unit * 0.06f, unit * 0.005f), Size(unit * 0.08f, unit * 0.045f))
    val head = body + Offset(-unit * 0.05f, -unit * 0.05f)
    drawCircle(BIRD_BROWN, unit * 0.038f, head)
    val beak = Path().apply {
        moveTo(head.x - unit * 0.03f, head.y - unit * 0.008f)
        lineTo(head.x - unit * 0.065f, head.y + unit * 0.002f)
        lineTo(head.x - unit * 0.03f, head.y + unit * 0.012f)
        close()
    }
    drawPath(beak, BIRD_BEAK)
    drawCircle(BODY_DARK, unit * 0.008f, head + Offset(-unit * 0.012f, -unit * 0.008f))
    // Legs.
    drawLine(BIRD_BEAK, body + Offset(unit * 0.01f, unit * 0.04f), body + Offset(unit * 0.01f, unit * 0.07f), unit * 0.008f)
    drawLine(BIRD_BEAK, body + Offset(-unit * 0.015f, unit * 0.04f), body + Offset(-unit * 0.015f, unit * 0.07f), unit * 0.008f)
}

private val SQUIRREL_RUST = Color(0xFFB8673A)
private val SQUIRREL_LIGHT = Color(0xFFE8B98A)

/** Where the squirrel's tail joins its body, from its feet, in tile widths. */
private val SQUIRREL_TAIL_ROOT = Offset(0.04f, -0.03f)

/** A squirrel sitting up, facing left, its big tail swung back [tail] degrees. */
private fun DrawScope.drawSquirrel(unit: Float, tail: Float) {
    val body = Offset(0f, -unit * 0.08f)
    // The tail: a thick curl behind the body.
    rotate(degrees = tail, pivot = SQUIRREL_TAIL_ROOT * unit) {
        drawOval(SQUIRREL_RUST, body + Offset(unit * 0.03f, -unit * 0.12f), Size(unit * 0.1f, unit * 0.17f))
        drawOval(SQUIRREL_LIGHT, body + Offset(unit * 0.06f, -unit * 0.1f), Size(unit * 0.05f, unit * 0.09f))
    }
    drawOval(SQUIRREL_RUST, body - Offset(unit * 0.045f, unit * 0.055f), Size(unit * 0.09f, unit * 0.12f))
    drawOval(SQUIRREL_LIGHT, body - Offset(unit * 0.03f, unit * 0.025f), Size(unit * 0.045f, unit * 0.075f))
    val head = body + Offset(-unit * 0.02f, -unit * 0.08f)
    drawCircle(SQUIRREL_RUST, unit * 0.04f, head)
    drawCircle(SQUIRREL_RUST, unit * 0.014f, head + Offset(unit * 0.012f, -unit * 0.038f))
    drawCircle(BODY_DARK, unit * 0.007f, head + Offset(-unit * 0.017f, -unit * 0.005f))
    // A seed held in its paws.
    drawOval(Color(0xFF5D4A3A), body + Offset(-unit * 0.04f, -unit * 0.035f), Size(unit * 0.02f, unit * 0.028f))
}

private val FROG_GREEN = Color(0xFF7DB356)
private val FROG_LIGHT = Color(0xFFC9E3A5)

/** A frog sitting, seen from the front. */
private fun DrawScope.drawFrog(unit: Float) {
    val body = Offset(0f, -unit * 0.04f)
    drawOval(FROG_GREEN, body - Offset(unit * 0.075f, unit * 0.045f), Size(unit * 0.15f, unit * 0.08f))
    val throat = unit * 0.031f
    drawOval(FROG_LIGHT, body + Offset(-throat, -unit * 0.005f), Size(throat * 2f, throat * 1.1f))
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

/** A red dragonfly, its four clear wings spread [open] of the way; its feet are the foot of its tail. */
private fun DrawScope.drawDragonfly(unit: Float, open: Float) {
    val centre = Offset(0f, -unit * 0.11f)
    // Two pairs of long wings, spread flat.
    for (side in floatArrayOf(-1f, 1f)) {
        scale(scaleX = side * open, scaleY = 1f, pivot = centre) {
            for ((dy, length) in listOf(-0.012f to 0.12f, 0.012f to 0.1f)) {
                val wing = Size(unit * length, unit * 0.028f)
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
