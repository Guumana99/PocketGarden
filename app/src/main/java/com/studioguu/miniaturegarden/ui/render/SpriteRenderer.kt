package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * An atlas' pages as images to draw: each sprite is drawn from the page it is packed on. The
 * functions below that take one draw [AssetManifest]s from any page; those that take a single
 * [ImageBitmap] draw from that image (a picture made from a sprite, such as the lawn).
 */
class AtlasPages(private val images: List<ImageBitmap>) {
    operator fun get(asset: AssetManifest): ImageBitmap = images[asset.page]

    companion object {
        /** [atlas]'s pages; null while it has none. */
        fun of(atlas: SpriteAtlas): AtlasPages? =
            atlas.pages.takeIf { it.isNotEmpty() }?.let { pages -> AtlasPages(pages.map { it.asImageBitmap() }) }
    }
}

/** [drawSprite] from the page of [pages] that [asset] is on. */
fun DrawScope.drawSprite(
    pages: AtlasPages,
    asset: AssetManifest,
    anchor: Offset,
    scale: Float,
    flipped: Boolean = false,
    alpha: Float = 1f,
    tint: Color? = null,
) = drawSprite(pages[asset], asset, anchor, scale, flipped, alpha, tint)

/** [drawTurningSprite] from the page of [pages] that [asset] is on. */
fun DrawScope.drawTurningSprite(pages: AtlasPages, asset: AssetManifest, pivot: Offset, scale: Float, degrees: Float, flipped: Boolean = false) =
    drawTurningSprite(pages[asset], asset, pivot, scale, degrees, flipped)

/** [drawSwayingSprite] from the page of [pages] that [asset] is on. */
fun DrawScope.drawSwayingSprite(
    pages: AtlasPages,
    asset: AssetManifest,
    anchor: Offset,
    scale: Float,
    lean: Float,
    flipped: Boolean = false,
    colorFilter: ColorFilter? = null,
) = drawSwayingSprite(pages[asset], asset, anchor, scale, lean, flipped, colorFilter)

/** [drawSpriteSnow] from the page of [pages] that [asset] is on. */
fun DrawScope.drawSpriteSnow(pages: AtlasPages, asset: AssetManifest, anchor: Offset, scale: Float, lean: Float, flipped: Boolean = false) =
    drawSpriteSnow(pages[asset], asset, anchor, scale, lean, flipped)

/** [drawSpriteShadow] from the page of [pages] that [asset] is on. */
fun DrawScope.drawSpriteShadow(pages: AtlasPages, asset: AssetManifest, anchor: Offset, scale: Float, light: SunLight, flipped: Boolean = false) =
    drawSpriteShadow(pages[asset], asset, anchor, scale, light, flipped)

/**
 * Draws one atlas sprite so that its anchor point lands on [anchor].
 *
 * @param scale screen pixels per atlas pixel.
 * @param flipped mirror horizontally around the anchor; ignored for sprites that do not allow it.
 */
fun DrawScope.drawSprite(
    atlasImage: ImageBitmap,
    asset: AssetManifest,
    anchor: Offset,
    scale: Float,
    flipped: Boolean = false,
    alpha: Float = 1f,
    tint: Color? = null,
    colorFilter: ColorFilter? = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) },
) {
    val dstOffset = IntOffset(
        asset.drawLeft(anchor.x, scale).roundToInt(),
        asset.drawTop(anchor.y, scale).roundToInt(),
    )
    val dstSize = IntSize(
        asset.drawnWidth(scale).roundToInt().coerceAtLeast(1),
        asset.drawnHeight(scale).roundToInt().coerceAtLeast(1),
    )
    scale(scaleX = if (flipped && asset.isFlippedAllowed) -1f else 1f, scaleY = 1f, pivot = anchor) {
        drawImage(
            image = atlasImage,
            srcOffset = IntOffset(asset.atlasX, asset.atlasY),
            srcSize = IntSize(asset.width, asset.height),
            dstOffset = dstOffset,
            dstSize = dstSize,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/**
 * Draws a sprite turned [degrees] clockwise about its anchor, which lands on [pivot]: the sails
 * of a windmill on their hub. Mirrored first when [flipped], so a turned building's sails sit
 * on its other side and turn the other way, as they would seen from there.
 */
fun DrawScope.drawTurningSprite(
    atlasImage: ImageBitmap,
    asset: AssetManifest,
    pivot: Offset,
    scale: Float,
    degrees: Float,
    flipped: Boolean = false,
) {
    rotate(degrees, pivot) { drawSprite(atlasImage, asset, pivot, scale, flipped) }
}

// Reused between draws; all drawing happens on the main thread.
private val shearMatrix = Matrix()

/**
 * Maps every point to where it is when the picture is sheared and flattened about the ground
 * line through [anchor]: a point `h` above that line moves `h * dx` sideways, and ends up
 * `h * dy` below the line instead of `h` above it. With `dy = -1` heights are kept, which makes
 * a lean; with a small positive `dy` the picture is laid down on the ground, which makes a shadow.
 */
private fun shearAbout(anchor: Offset, dx: Float, dy: Float): Matrix = shearMatrix.apply {
    reset()
    // x' = x + dx * (anchor.y - y),  y' = anchor.y + dy * (anchor.y - y)
    this[1, 0] = -dx
    this[3, 0] = dx * anchor.y
    this[1, 1] = -dy
    this[3, 1] = anchor.y + dy * anchor.y
}

/**
 * Maps every point to where it is when a picture standing on the ground line through [middle]
 * is turned along a run sloping [slope] pixels down for each one across: what is `x` from the
 * middle goes `x * slope` down, and what is `h` above the line goes `h * dx` sideways and
 * `h * dy` down. With `dx = 0, dy = -1` it stands; laid flat, it is its shadow.
 */
private fun alongRun(middle: Offset, slope: Float, dx: Float, dy: Float): Matrix = shearMatrix.apply {
    reset()
    // x' = x + dx * (middle.y - y),  y' = middle.y + slope * (x - middle.x) + dy * (middle.y - y)
    this[1, 0] = -dx
    this[3, 0] = dx * middle.y
    this[0, 1] = slope
    this[1, 1] = -dy
    this[3, 1] = middle.y - slope * middle.x + dy * middle.y
}

/** Where a rail's anchor goes, for its attachment point (where the ground is on it) to stand on [middle]. */
private fun railAnchor(asset: AssetManifest, middle: Offset, scale: Float): Offset =
    middle - Offset(0f, ((asset.attachY ?: asset.anchorY) - asset.anchorY) * asset.drawnHeight(scale))

/**
 * Draws the piece of a run between [from] and [to], two points on the ground: a sprite drawn
 * upright with its ground line ([railAnchor]) on the point midway, and sheared so that the line
 * follows the run. Verticals stay upright.
 *
 * @param mirrored shown from its other side, for the slope that would otherwise lay it down.
 */
fun DrawScope.drawRunPiece(pages: AtlasPages, asset: AssetManifest, from: Offset, to: Offset, scale: Float, mirrored: Boolean = false) {
    val middle = (from + to) / 2f
    withTransform({
        transform(alongRun(middle, (to.y - from.y) / (to.x - from.x), 0f, -1f))
        scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f, pivot = middle)
    }) {
        drawSprite(pages[asset], asset, railAnchor(asset, middle, scale), scale)
    }
}

/** The shadow [drawRunPiece]'s piece casts in [light]: its own outline laid on the ground along the run. */
fun DrawScope.drawRunPieceShadow(pages: AtlasPages, asset: AssetManifest, from: Offset, to: Offset, scale: Float, light: SunLight, mirrored: Boolean = false) {
    if (light.alpha <= 0f) return
    val middle = (from + to) / 2f
    withTransform({
        transform(alongRun(middle, (to.y - from.y) / (to.x - from.x), light.dx, light.dy))
        scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f, pivot = middle)
    }) {
        drawSprite(pages[asset], asset, railAnchor(asset, middle, scale), scale, alpha = light.alpha, colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn))
    }
}

/**
 * Draws one atlas sprite leaning in the wind: its foot stays on [anchor] and everything above
 * moves sideways in proportion to its height, [lean] times as far (negative to the left).
 */
fun DrawScope.drawSwayingSprite(
    atlasImage: ImageBitmap,
    asset: AssetManifest,
    anchor: Offset,
    scale: Float,
    lean: Float,
    flipped: Boolean = false,
    colorFilter: ColorFilter? = null,
) {
    if (lean == 0f) {
        drawSprite(atlasImage, asset, anchor, scale, flipped, colorFilter = colorFilter)
        return
    }
    withTransform({ transform(shearAbout(anchor, lean, -1f)) }) {
        drawSprite(atlasImage, asset, anchor, scale, flipped, colorFilter = colorFilter)
    }
}

private val SNOW = Color(0xFFFFFFFF)

/**
 * Snow lying on the sprite standing on [anchor], leaning with it by [lean] (see
 * [drawSwayingSprite]): the sprite's own outline in white, thick at the top and thinning out
 * towards the middle, so that it reads as a dusting on its upper leaves.
 */
fun DrawScope.drawSpriteSnow(
    atlasImage: ImageBitmap,
    asset: AssetManifest,
    anchor: Offset,
    scale: Float,
    lean: Float,
    flipped: Boolean = false,
) {
    val top = asset.drawTop(anchor.y, scale)
    val height = anchor.y - top
    if (height <= 0f) return
    val reach = abs(lean) * height
    val bounds = Rect(asset.drawLeft(anchor.x, scale) - reach, top, asset.drawLeft(anchor.x, scale) + asset.drawnWidth(scale) + reach, anchor.y)
    drawContext.canvas.saveLayer(bounds, Paint())
    val outline = ColorFilter.tint(SNOW, BlendMode.SrcIn)
    if (lean == 0f) {
        drawSprite(atlasImage, asset, anchor, scale, flipped, colorFilter = outline)
    } else {
        withTransform({ transform(shearAbout(anchor, lean, -1f)) }) {
            drawSprite(atlasImage, asset, anchor, scale, flipped, colorFilter = outline)
        }
    }
    // Only the upper part keeps it, fading out downwards.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.85f),
            0.3f to Color.Black.copy(alpha = 0.45f),
            0.6f to Color.Transparent,
            startY = top,
            endY = anchor.y,
        ),
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

/**
 * Draws the shadow that the sprite standing on [anchor] casts in [light]: the sprite's own
 * outline, darkened and laid down on the ground. Only what is above the anchor casts one; the
 * rest of the picture is the ground the thing stands in.
 */
fun DrawScope.drawSpriteShadow(
    atlasImage: ImageBitmap,
    asset: AssetManifest,
    anchor: Offset,
    scale: Float,
    light: SunLight,
    flipped: Boolean = false,
) {
    if (light.alpha <= 0f) return
    val rowsAboveAnchor = (asset.anchorY * asset.height).roundToInt().coerceIn(1, asset.height)
    val dstSize = IntSize(
        asset.drawnWidth(scale).roundToInt().coerceAtLeast(1),
        (rowsAboveAnchor * scale * asset.drawScale).roundToInt().coerceAtLeast(1),
    )
    val dstOffset = IntOffset(
        asset.drawLeft(anchor.x, scale).roundToInt(),
        (anchor.y - dstSize.height).roundToInt(),
    )
    // Mirrored with the thing itself, and then thrown the way the light goes.
    val mirror = if (flipped && asset.isFlippedAllowed) -1f else 1f
    withTransform({
        transform(shearAbout(anchor, light.dx, light.dy))
        scale(scaleX = mirror, scaleY = 1f, pivot = anchor)
    }) {
        drawImage(
            image = atlasImage,
            srcOffset = IntOffset(asset.atlasX, asset.atlasY),
            srcSize = IntSize(asset.width, rowsAboveAnchor),
            dstOffset = dstOffset,
            dstSize = dstSize,
            alpha = light.alpha,
            colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn),
            filterQuality = FilterQuality.Low,
        )
    }
}
