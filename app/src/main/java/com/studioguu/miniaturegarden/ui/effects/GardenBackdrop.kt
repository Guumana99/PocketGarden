package com.studioguu.miniaturegarden.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.studioguu.miniaturegarden.domain.model.WeatherType
import androidx.compose.ui.graphics.lerp
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.ui.render.SceneTint
import kotlin.math.PI
import kotlin.math.sin

/** The colours of the scene behind the garden under one kind of weather. */
private class BackdropPalette(
    val skyTop: Color,
    val skyHorizon: Color,
    val hillFar: Color,
    val hillMid: Color,
    val hillNear: Color,
    val meadowTop: Color,
    val meadowBottom: Color,
    val cloud: Color,
    /** Laid over the whole scene at the end: nothing on a fine day, grey when it is not. */
    val haze: Color,
)

private val SUNNY = BackdropPalette(
    skyTop = Color(0xFFB9DFF0),
    skyHorizon = Color(0xFFFDF1D3),
    hillFar = Color(0xFFBCD6B4),
    hillMid = Color(0xFFA5C98C),
    hillNear = Color(0xFF93BD74),
    meadowTop = Color(0xFFB7D184),
    meadowBottom = Color(0xFFE3CD95),
    cloud = Color(0xFFFFFFFF),
    haze = Color.Transparent,
)

private val CLOUDY = BackdropPalette(
    skyTop = Color(0xFFC6D1D6),
    skyHorizon = Color(0xFFEFE8D8),
    hillFar = Color(0xFFB7C7B4),
    hillMid = Color(0xFFA3BC92),
    hillNear = Color(0xFF94B07E),
    meadowTop = Color(0xFFB0C487),
    meadowBottom = Color(0xFFD8C598),
    cloud = Color(0xFFF2F2EE),
    haze = Color(0x14707A80),
)

private val RAINY = BackdropPalette(
    skyTop = Color(0xFF9FAFBA),
    skyHorizon = Color(0xFFD5D6CC),
    hillFar = Color(0xFFA3B4A6),
    hillMid = Color(0xFF8FA886),
    hillNear = Color(0xFF819D74),
    meadowTop = Color(0xFF9CB37C),
    meadowBottom = Color(0xFFC4B58E),
    cloud = Color(0xFFDDE1E2),
    haze = Color(0x24566470),
)

private val SNOWY = BackdropPalette(
    skyTop = Color(0xFFC3CDD5),
    skyHorizon = Color(0xFFEDEFF0),
    hillFar = Color(0xFFE4E9EC),
    hillMid = Color(0xFFDCE3E6),
    hillNear = Color(0xFFD3DCDF),
    meadowTop = Color(0xFFE5EAEC),
    meadowBottom = Color(0xFFDADDDD),
    cloud = Color(0xFFF4F5F6),
    haze = Color(0x18707A86),
)

private val MOON = Color(0xFFFFF6D8)
private val STAR = Color(0xFFFFFFF0)

private val LEAF_DARK = Color(0xFF5E8C3A)
private val LEAF_LIGHT = Color(0xFF86B454)

/** The leaves and flowers of the greenery round the edges, by season. */
private class SeasonLeaves(val dark: Color, val light: Color, val blossoms: List<Color>, val snow: Boolean = false) {
    companion object {
        private val SPRING = SeasonLeaves(LEAF_DARK, LEAF_LIGHT, BLOSSOMS)
        private val SUMMER = SeasonLeaves(Color(0xFF3F7F2E), Color(0xFF6BAA3E), listOf(Color(0xFFFFE08A)))
        private val AUTUMN = SeasonLeaves(Color(0xFFB5552A), Color(0xFFE29A3A), emptyList())
        private val WINTER = SeasonLeaves(Color(0xFF4F6B4A), Color(0xFF7D9478), emptyList(), snow = true)

        fun of(season: Season): SeasonLeaves = when (season) {
            Season.SPRING -> SPRING
            Season.SUMMER -> SUMMER
            Season.AUTUMN -> AUTUMN
            Season.WINTER -> WINTER
        }
    }
}

/** This weather's palette, with the hills and the meadow turned to [season]. Spring leaves it as it is. */
private fun BackdropPalette.inSeason(season: Season): BackdropPalette {
    fun Color.toward(target: Color, amount: Float) = lerp(this, target, amount)
    return when (season) {
        Season.SPRING -> this
        Season.SUMMER -> BackdropPalette(
            skyTop, skyHorizon, hillFar.toward(Color(0xFF7FB069), 0.35f), hillMid.toward(Color(0xFF6FA552), 0.35f),
            hillNear.toward(Color(0xFF5E9A44), 0.35f), meadowTop.toward(Color(0xFF8EC25A), 0.4f), meadowBottom, cloud, haze,
        )
        Season.AUTUMN -> BackdropPalette(
            skyTop, skyHorizon.toward(Color(0xFFF7DDB4), 0.4f), hillFar.toward(Color(0xFFC9A86A), 0.5f), hillMid.toward(Color(0xFFC98A4B), 0.5f),
            hillNear.toward(Color(0xFFB9783F), 0.45f), meadowTop.toward(Color(0xFFD3B06A), 0.5f), meadowBottom.toward(Color(0xFFD9B98A), 0.3f), cloud, haze,
        )
        Season.WINTER -> BackdropPalette(
            skyTop.toward(Color(0xFFC9D8E2), 0.5f), skyHorizon.toward(Color(0xFFEFF3F5), 0.6f), hillFar.toward(Color(0xFFF2F5F7), 0.75f),
            hillMid.toward(Color(0xFFE9EEF1), 0.75f), hillNear.toward(Color(0xFFE3E9EC), 0.7f), meadowTop.toward(Color(0xFFF1F4F6), 0.75f),
            meadowBottom.toward(Color(0xFFE6E9EA), 0.6f), cloud, haze,
        )
    }
}
private val BRANCH = Color(0xFF7A5230)
private val BLOSSOMS = listOf(Color(0xFFF7A8B8), Color(0xFFFFE08A), Color(0xFFFFFFFF))
private val SUN_GLOW = Color(0xFFFFF4C2)
private val VIGNETTE = Color(0xFF4A3420)

/** The horizon, as a fraction of the height from the top. */
private const val HORIZON = 0.4f

/**
 * What lies behind and around the garden box: a sky fading to a warm horizon, three ranges of
 * hills, a meadow that turns to earth towards the viewer, and greenery at the corners of the
 * screen. It is all drawn from shapes, without images, and changes its colours with the weather.
 * The sun stands in the sky by day; by night ([hourOfDay]) a moon and a few stars do, where the
 * sky is clear. The darkness itself is laid over the scene with the garden ([SceneTint]).
 * Nothing in it moves or takes touches.
 *
 * @param hourOfDay local time (0 until 24); null for an afternoon.
 */
@Composable
fun GardenBackdrop(weather: WeatherType, modifier: Modifier = Modifier, season: Season = Season.SPRING, hourOfDay: Float? = null) {
    val palette = when (weather) {
        WeatherType.SUNNY -> SUNNY
        WeatherType.CLOUDY -> CLOUDY
        WeatherType.RAINY -> RAINY
        WeatherType.SNOWY -> SNOWY
    }.inSeason(season)
    val time = hourOfDay?.let { TimeOfDay.at(it, season) } ?: TimeOfDay.DAY
    val leaves = SeasonLeaves.of(season)
    Canvas(modifier) {
        val horizon = size.height * HORIZON
        // Sky, down to a little below the horizon so no seam shows between the hills.
        drawRect(
            brush = Brush.verticalGradient(listOf(palette.skyTop, palette.skyHorizon), endY = horizon),
            size = Size(size.width, horizon + size.height * 0.05f),
        )
        if (time == TimeOfDay.NIGHT && weather != WeatherType.RAINY && weather != WeatherType.SNOWY) {
            // Stars only where the sky is clear, and fewer behind the clouds of a grey night.
            val stars = if (weather == WeatherType.SUNNY) 18 else 7
            repeat(stars) { i ->
                val x = ((i * 0.618034f + 0.11f) % 1f) * size.width
                val y = ((i * 0.381966f * 1.7f + 0.05f) % 1f) * horizon * 0.8f
                drawCircle(STAR.copy(alpha = 0.55f + (i % 3) * 0.15f), radius = size.width * (0.0028f + (i % 4) * 0.0008f), center = Offset(x, y))
            }
            val moon = Offset(size.width * 0.12f, size.height * 0.23f)
            drawCircle(
                brush = Brush.radialGradient(listOf(MOON.copy(alpha = 0.45f), MOON.copy(alpha = 0f)), center = moon, radius = size.width * 0.16f),
                radius = size.width * 0.16f,
                center = moon,
            )
            drawCircle(MOON, radius = size.width * 0.045f, center = moon)
        }
        if (weather == WeatherType.SUNNY && (time == TimeOfDay.MORNING || time == TimeOfDay.DAY)) {
            val sun = Offset(size.width * 0.2f, size.height * 0.14f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(SUN_GLOW, SUN_GLOW.copy(alpha = 0f)),
                    center = sun,
                    radius = size.width * 0.42f,
                ),
                radius = size.width * 0.42f,
                center = sun,
            )
        }
        val cloudAlpha = if (weather == WeatherType.SUNNY) 0.75f else 0.9f
        drawCloud(Offset(size.width * 0.70f, size.height * 0.21f), size.width * 0.105f, palette.cloud.copy(alpha = cloudAlpha))
        drawCloud(Offset(size.width * 0.22f, size.height * 0.30f), size.width * 0.075f, palette.cloud.copy(alpha = cloudAlpha * 0.85f))
        if (weather != WeatherType.SUNNY) {
            drawCloud(Offset(size.width * 0.47f, size.height * 0.15f), size.width * 0.13f, palette.cloud.copy(alpha = cloudAlpha))
        }

        // Hills, far to near. Each is a couple of sine waves, with its own length and offset.
        drawHills(horizon - size.height * 0.035f, size.height * 0.030f, waves = 1.3f, shift = 0.4f, color = palette.hillFar)
        drawHills(horizon - size.height * 0.010f, size.height * 0.026f, waves = 2.1f, shift = 2.2f, color = palette.hillMid)
        drawHills(horizon + size.height * 0.018f, size.height * 0.020f, waves = 1.6f, shift = 4.1f, color = palette.hillNear)

        // The meadow the box stands in.
        val meadowTop = horizon + size.height * 0.035f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(palette.hillNear, palette.meadowTop, palette.meadowBottom),
                startY = meadowTop,
                endY = size.height,
            ),
            topLeft = Offset(0f, meadowTop),
            size = Size(size.width, size.height - meadowTop),
        )

        // Greenery at the edges: a leafy branch reaching in from each top corner, and bushes in
        // the bottom corners, beside the tool bar.
        drawBranch(Offset(-size.width * 0.02f, size.height * 0.105f), size.width * 0.34f, towardsRight = true, leaves)
        drawBranch(Offset(size.width * 1.02f, size.height * 0.135f), size.width * 0.26f, towardsRight = false, leaves)
        drawBush(Offset(size.width * 0.04f, size.height * 0.985f), size.width * 0.15f, seed = 3, leaves)
        drawBush(Offset(size.width * 0.97f, size.height * 0.99f), size.width * 0.16f, seed = 11, leaves)
        drawBush(Offset(-size.width * 0.02f, size.height * 0.80f), size.width * 0.085f, seed = 5, leaves)
        drawBush(Offset(size.width * 1.03f, size.height * 0.76f), size.width * 0.095f, seed = 8, leaves)

        if (palette.haze != Color.Transparent) drawRect(palette.haze)
        // Darker towards the corners, which draws the eye to the box in the middle.
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to VIGNETTE.copy(alpha = 0.22f),
                center = Offset(size.width / 2f, size.height * 0.5f),
                radius = size.maxDimension * 0.72f,
            )
        )
    }
}

/** A cloud of the given half-width: three overlapping puffs on a flat base. */
private fun DrawScope.drawCloud(center: Offset, radius: Float, color: Color) {
    drawOval(color, center + Offset(-radius * 1.5f, -radius * 0.15f), Size(radius * 3f, radius * 0.8f))
    drawCircle(color, radius * 0.62f, center + Offset(-radius * 0.65f, -radius * 0.05f))
    drawCircle(color, radius * 0.85f, center + Offset(radius * 0.05f, -radius * 0.35f))
    drawCircle(color, radius * 0.58f, center + Offset(radius * 0.8f, 0f))
}

/** A range of hills whose crest waves about [baseline] and which fills everything below it. */
private fun DrawScope.drawHills(baseline: Float, amplitude: Float, waves: Float, shift: Float, color: Color) {
    val steps = 48
    val path = Path().apply {
        moveTo(0f, size.height)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val angle = t * waves * 2f * PI.toFloat() + shift
            val y = baseline - amplitude * (sin(angle) + 0.45f * sin(angle * 2.3f + shift))
            lineTo(t * size.width, y)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

/** A bough reaching in from the side of the screen, sagging a little, with leaves along it. */
private fun DrawScope.drawBranch(start: Offset, length: Float, towardsRight: Boolean, leaves: SeasonLeaves) {
    val direction = if (towardsRight) 1f else -1f
    val count = 9
    var previous = start
    for (i in 1..count) {
        val t = i / count.toFloat()
        val point = start + Offset(direction * length * t, length * 0.16f * t * t)
        drawLine(BRANCH, previous, point, strokeWidth = length * 0.022f * (1.2f - t))
        previous = point
        val leaf = Size(length * 0.17f, length * 0.075f)
        // Alternately above and below the bough, each tilted its own way.
        val above = i % 2 == 0
        val tilt = direction * (if (above) -38f else 34f) + (i * 37 % 17 - 8)
        rotate(tilt, pivot = point) {
            drawOval(
                color = if ((i + if (towardsRight) 0 else 1) % 3 == 0) leaves.light else leaves.dark,
                topLeft = point + Offset(if (towardsRight) 0f else -leaf.width, -leaf.height / 2f),
                size = leaf,
            )
        }
    }
}

/**
 * A bush of the given radius sitting on [base]: a mound of leaf-coloured balls with a few
 * blossoms on it. [seed] decides how the balls and blossoms fall, so each bush differs.
 */
private fun DrawScope.drawBush(base: Offset, radius: Float, seed: Int, leaves: SeasonLeaves) {
    var state = seed * 7919 + 17
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7FFF) / 32767f
    }
    repeat(9) { i ->
        val at = base + Offset((next() - 0.5f) * radius * 1.9f, -next() * radius * 0.95f)
        drawCircle(if (i % 3 == 0) leaves.light else leaves.dark, radius * (0.38f + next() * 0.28f), at)
    }
    repeat(5) { i ->
        val at = base + Offset((next() - 0.5f) * radius * 1.7f, -radius * (0.25f + next() * 0.9f))
        if (leaves.blossoms.isNotEmpty()) drawCircle(leaves.blossoms[(i + seed) % leaves.blossoms.size], radius * 0.085f, at)
    }
    // Snow settles on top of a bush in winter.
    if (leaves.snow) {
        drawOval(Color(0xF2FFFFFF), base + Offset(-radius * 0.8f, -radius * 1.25f), Size(radius * 1.6f, radius * 0.45f))
    }
}
