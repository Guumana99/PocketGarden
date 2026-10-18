package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas

/**
 * The lawn's picture of grass, cut to its top face and feathered ([LawnShade.feather]), ready to
 * be laid cell by cell so that the cells fade into one another.
 *
 * @property image the feathered picture, on its own.
 * @property sprite where it is anchored, as the atlas sprite was; it starts at (0, 0) of [image].
 * @property base the picture's average colour, laid under the whole lawn first.
 * @property tileWidth how many of its pixels one tile spans, as in the atlas.
 */
class FeatheredLawn(val image: ImageBitmap, val sprite: AssetManifest, val base: Color, val tileWidth: Int) {
    companion object {
        /** Made from [atlas]'s picture of [assetId]; null when the atlas does not have it. */
        fun from(atlas: SpriteAtlas, assetId: String): FeatheredLawn? {
            val bitmap = atlas.bitmap ?: return null
            val sprite = atlas[assetId] ?: return null
            val width = sprite.width
            val height = sprite.height
            if (sprite.atlasX + width > bitmap.width || sprite.atlasY + height > bitmap.height) return null
            val pixels = IntArray(width * height)
            // A picture whose pixels cannot be read (one held on the graphics card) is drawn as it is.
            runCatching { bitmap.getPixels(pixels, 0, width, sprite.atlasX, sprite.atlasY, width, height) }
                .onFailure { return null }
            // The top face is a diamond a tile across and half as tall, round the anchor.
            val halfWidth = atlas.manifest.tileWidth / 2f
            val halfHeight = halfWidth / 2f
            val centerX = sprite.anchorX * width
            val centerY = sprite.anchorY * height
            val feathered = LawnShade.feather(pixels, width, centerX, centerY, halfWidth, halfHeight)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            out.setPixels(feathered, 0, width, 0, 0, width, height)
            // Drawn many times smaller than it is (a 512 px cell at about 70 px on a phone): with
            // mipmaps the blades blend into a soft lawn instead of glittering as grain (#53).
            out.setHasMipMap(true)
            val base = Color(LawnShade.meanColour(pixels, width, centerX, centerY, halfWidth, halfHeight))
            return FeatheredLawn(out.asImageBitmap(), sprite.copy(atlasX = 0, atlasY = 0), base, atlas.manifest.tileWidth)
        }
    }
}
