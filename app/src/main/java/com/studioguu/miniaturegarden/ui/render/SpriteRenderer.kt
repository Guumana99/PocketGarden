package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import kotlin.math.roundToInt

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
) {
    val dstOffset = IntOffset(
        asset.drawLeft(anchor.x, scale).roundToInt(),
        asset.drawTop(anchor.y, scale).roundToInt(),
    )
    val dstSize = IntSize(
        (asset.width * scale).roundToInt().coerceAtLeast(1),
        (asset.height * scale).roundToInt().coerceAtLeast(1),
    )
    scale(scaleX = if (flipped && asset.isFlippedAllowed) -1f else 1f, scaleY = 1f, pivot = anchor) {
        drawImage(
            image = atlasImage,
            srcOffset = IntOffset(asset.atlasX, asset.atlasY),
            srcSize = IntSize(asset.width, asset.height),
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.Medium,
        )
    }
}
