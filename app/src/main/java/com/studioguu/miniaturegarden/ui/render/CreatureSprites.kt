package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.studioguu.miniaturegarden.data.asset.AssetManifest
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

// The visitors, as they go about the garden: their Recraft pictures (#71), from the creatures' own
// atlas ([SpriteAtlas.creatures]). Whoever draws a visitor calls [drawVisitor].
//
// Every movement is made by moving still pictures: turned to face the other way, stretched,
// tilted, lifted; the fliers' wings folded or beaten on their own about their roots; and the
// squirrel and the bird each have a second picture (running, flying) they change into, easing
// from one to the other. Nothing is drawn differently from moment to moment (see ASSETS.md).

/** How high a visitor that flies keeps above its spot, in tile widths; 0 for those on the ground. */
val Creature.hoverHeight: Float get() = CreatureRoutes.cruiseAltitude(this)

/** Whether [creature] keeps to the air. */
fun Creature.flies(): Boolean = hoverHeight > 0f

/**
 * How a visitor's picture is moved at one moment: all of it about its feet, and its wings on their own.
 *
 * @property altitude how high it flies, in tile widths.
 * @property lift how far it is off the ground for a moment (a hop), in tile widths.
 * @property facing which way it faces; the pictures face left.
 * @property scaleX stretch across, about its feet: a pop, a jump.
 * @property tilt degrees it leans, about its feet, as the picture faces: less than 0 leans forward.
 * @property wingOpen how far a flier's wings are open, 0 (folded, or beaten down) to 1 (spread as drawn).
 * @property moving how much of its second picture shows, 0 to 1: the squirrel running, the bird flying.
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
    val moving: Float = 0f,
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
                Creature.BIRD -> bird(base, pose, p, now)
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
            // Runs from cell to cell in bounds, changing into its running picture as it sets off
            // and back as it stops.
            CreatureAction.STEP -> arc(p).let { a ->
                base.copy(lift = a * 0.04f, scaleY = 1f + 0.04f * a, tilt = -4f * a, moving = easing(pose), shadow = 1f - 0.3f * a)
            }
            // Sits up, breathing.
            else -> base.copy(scaleY = 1f + 0.012f * wave(now, 1800.0))
        }

        private fun bird(base: VisitorLook, pose: CreaturePose, p: Float, now: Long): VisitorLook = when (pose.action) {
            // Flying in or away: its wings beating, its flying picture giving way to its standing
            // one in the last of its way down (and the other way round as it takes off).
            CreatureAction.FLY -> base.copy(
                wingOpen = wave(now, 260.0),
                moving = (pose.altitude / LANDING_ALTITUDE).coerceIn(0f, 1f),
                shadow = (1f - pose.altitude / CreatureRoutes.SKY_ALTITUDE).coerceIn(0f, 1f),
            )
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

        /**
         * How much of the running picture shows over a step: all of it, but eased in over the first
         * moment of the first step of a move and out over the last of the last, rather than switched.
         */
        private fun easing(pose: CreaturePose): Float {
            var shown = 1f
            if (pose.setsOff) shown = min(shown, pose.elapsed.toFloat() / CHANGE_MILLIS)
            if (pose.stops) shown = min(shown, (pose.duration - pose.elapsed).toFloat() / CHANGE_MILLIS)
            return shown.coerceIn(0f, 1f)
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

        /** How long the squirrel takes to change into its running picture and back. */
        const val CHANGE_MILLIS = 140L

        /** Below this height (tile widths) the bird's flying picture gives way to its standing one. */
        const val LANDING_ALTITUDE = 0.025f
    }
}

/**
 * Draws [creature] at [base], a point on the ground (or on what it sits on), moved as [look]
 * says, from its pictures in [atlas] (the creatures' atlas, [TileSetDefinition.expectedCreatureSpriteIds]).
 * A picture stands with its anchor on [base]; a flier is held [VisitorLook.altitude] above it,
 * its shadow left on the ground unless [shadow] is false. Nothing is drawn while the atlas has
 * no picture of it.
 */
fun DrawScope.drawVisitor(
    creature: Creature,
    base: Offset,
    unit: Float,
    look: VisitorLook,
    atlas: SpriteAtlas,
    atlasImage: AtlasPages?,
    shadow: Boolean = true,
) {
    val image = atlasImage ?: return
    if (shadow && look.shadow > 0f) {
        val size = min(abs(look.scaleX), 1.1f)
        drawCreatureShadow(base, unit, SHADOW_WIDTH.getValue(creature) * size, alpha = look.shadow)
    }
    val feet = base - Offset(0f, (look.altitude + look.lift) * unit)
    val scale = unit / atlas.manifest.tileWidth
    val picture: DrawScope.() -> Unit = {
        withTransform({
            translate(feet.x, feet.y)
            scale(if (look.facing == Facing.RIGHT) -look.scaleX else look.scaleX, look.scaleY, Offset.Zero)
            rotate(look.tilt, Offset.Zero)
        }) {
            drawPictures(creature, look, atlas, image, scale, unit)
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
 * Draws [creature]'s pictures with its anchor at the origin: its own, and its second one
 * ([VisitorLook.moving]) over it as much as it shows; a flier in parts, its wings moving.
 */
private fun DrawScope.drawPictures(creature: Creature, look: VisitorLook, atlas: SpriteAtlas, image: AtlasPages, scale: Float, unit: Float) {
    val own = atlas[TileSetDefinition.assetIdFor(creature)]
    when (creature) {
        Creature.SQUIRREL -> {
            val running = atlas[TileSetDefinition.SQUIRREL_RUNNING]
            val moving = if (running == null) 0f else look.moving
            if (own != null && moving < 1f) drawSprite(image, own, Offset.Zero, scale, alpha = 1f - moving)
            if (running != null && moving > 0f) drawSprite(image, running, Offset.Zero, scale, alpha = moving)
        }
        Creature.BIRD -> {
            if (own != null && look.moving < 1f) drawSprite(image, own, Offset.Zero, scale, alpha = 1f - look.moving)
            // The flying picture is anchored on the middle of its body: held where the standing
            // one's body is, so that it lands where it stands.
            if (look.moving > 0f) translate(0f, -BIRD_BODY_HEIGHT * unit) { drawWinged(creature, look, atlas, image, scale, look.moving) }
        }
        Creature.BUTTERFLY, Creature.DRAGONFLY -> {
            if (look.whole && own != null) drawSprite(image, own, Offset.Zero, scale) else drawWinged(creature, look, atlas, image, scale, 1f)
        }
        Creature.FROG, Creature.LADYBUG -> if (own != null) drawSprite(image, own, Offset.Zero, scale)
    }
}

/**
 * Draws a flier from its body and its wings ([TileSetDefinition.wingIds]), at [alpha], the body's
 * anchor at the origin. Each wing hangs on the body by its root: the butterfly's and the
 * dragonfly's (seen from above) fold towards the body; the bird's (seen from the side) beat
 * down about their roots. The wings go behind the body.
 */
private fun DrawScope.drawWinged(creature: Creature, look: VisitorLook, atlas: SpriteAtlas, image: AtlasPages, scale: Float, alpha: Float) {
    val body = atlas[TileSetDefinition.wingedBodyId(creature) ?: return] ?: return
    val wings = TileSetDefinition.wingIds(creature).map { atlas[it] }
    for ((index, wing) in wings.withIndex()) {
        if (wing == null) continue
        val root = rootOf(wing, scale)
        if (creature == Creature.BIRD) {
            // Down from where it is drawn: the left wing, reaching out in front, turns one way;
            // the right one, raised behind, the other.
            val down = (1f - look.wingOpen) * BIRD_WING_BEAT
            rotate(if (index == 0) -down else down, root) { drawSprite(image, wing, root, scale, alpha = alpha) }
        } else {
            scale(look.wingOpen, 1f, root) { drawSprite(image, wing, root, scale, alpha = alpha) }
        }
    }
    drawSprite(image, body, Offset.Zero, scale, alpha = alpha)
}

/** Where [wing]'s root (its anchor) is when the body's anchor (its attachment point) is at the origin. */
private fun rootOf(wing: AssetManifest, scale: Float): Offset {
    val attachX = wing.attachX ?: wing.anchorX
    val attachY = wing.attachY ?: wing.anchorY
    return Offset((wing.anchorX - attachX) * wing.drawnWidth(scale), (wing.anchorY - attachY) * wing.drawnHeight(scale))
}

/** How far the standing bird's body is above its feet, in tile widths (prepare_creature_art.py prints it). */
private const val BIRD_BODY_HEIGHT = 0.08f

/** How far (degrees) the bird's wings beat down from where they are drawn. */
private const val BIRD_WING_BEAT = 38f

/** How wide each visitor's shadow is, in tile widths. */
private val SHADOW_WIDTH: Map<Creature, Float> = mapOf(
    Creature.BUTTERFLY to 0.12f,
    Creature.LADYBUG to 0.07f,
    Creature.BIRD to 0.13f,
    Creature.SQUIRREL to 0.16f,
    Creature.FROG to 0.17f,
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
