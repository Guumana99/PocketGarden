package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
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
fun DrawScope.drawSwayingSprite(pages: AtlasPages, asset: AssetManifest, anchor: Offset, scale: Float, lean: Float, flipped: Boolean = false) =
    drawSwayingSprite(pages[asset], asset, anchor, scale, lean, flipped)

/** [drawSpriteShadow] from the page of [pages] that [asset] is on. */
fun DrawScope.drawSpriteShadow(pages: AtlasPages, asset: AssetManifest, anchor: Offset, scale: Float, light: SunLight) =
    drawSpriteShadow(pages[asset], asset, anchor, scale, light)

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
            colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) },
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
) {
    if (lean == 0f) {
        drawSprite(atlasImage, asset, anchor, scale, flipped)
        return
    }
    withTransform({ transform(shearAbout(anchor, lean, -1f)) }) {
        drawSprite(atlasImage, asset, anchor, scale, flipped)
    }
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
    withTransform({ transform(shearAbout(anchor, light.dx, light.dy)) }) {
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
