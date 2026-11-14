package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StructureData

/**
 * The snow read off each picture ([SnowCover]) on the roofs, the benches and the rocks
 * (#fb-1113 2-5): made once per picture (and per one of [VARIANTS] amounts for the small things,
 * so that not every rock looks the same), kept, and drawn over the picture just as the picture is
 * drawn, mirrored with it when the thing is turned.
 */
object PictureSnow {

    /** How many amounts of snow the small things come in, chosen by where they stand. */
    const val VARIANTS = 3

    /** How much of the most the small things' variants carry. */
    private val VARIANT_AMOUNT = floatArrayOf(1.1f, 1.5f, 1.9f)

    /** The windmill's roof face as measured on its picture: shares of its width and height. */
    val WINDMILL_ROOF: List<Pair<Float, Float>> = listOf(0.324f to 0.008f, 0.751f to 0.126f, 0.559f to 0.399f, 0.154f to 0.331f)

    /**
     * The well's roof as measured on its picture (the outline kept for the old cover stopped a
     * course of tiles short of the low edge): shares of its width and height.
     */
    val WELL_ROOF: List<Pair<Float, Float>> = listOf(
        0.023f to 0.243f, 0.614f to 0.012f, 0.795f to 0.209f, 0.801f to 0.238f, 0.392f to 0.370f, 0.242f to 0.348f,
    )

    /** How much a light dusting is at its most, for each small thing (#fb-1113 5: less than a roof). */
    private val DUSTING: Map<String, Float> = mapOf(
        "deco_bench" to 0.6f,
        "deco_bench_back" to 0.6f,
        "deco_rock" to 0.55f,
        "deco_rock_flat" to 0.62f,
        "deco_rock_mossy" to 0.45f,
        "deco_rock_standing" to 0.4f,
        "deco_rock_pebbles" to 0.5f,
        // #book60: a dusting on the scarecrow's hat brim and sleeves, and on the bird bath's rim.
        GridObject.SCARECROW_OBJECT_ID to 0.75f,
        GridObject.BIRDBATH_OBJECT_ID to 0.55f,
    )

    /**
     * #book60: what has water in its picture that freezes over while the snow lies: the bird
     * bath. No snow lies on the water ([isWater]); it is drawn over in pale ice instead.
     */
    val FROZEN: Set<String> = setOf(GridObject.BIRDBATH_OBJECT_ID)

    /** Whether the pixel [argb] is water: solid, and clearly bluer than it is red or green. */
    fun isWater(argb: Int): Boolean {
        val a = argb ushr 24
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return a > 128 && b > r + WATER_BLUE && b >= g
    }

    /** How far down the bird bath's picture its water lies at most: below it, bluish shade on the stone is no water. */
    const val WATER_TOP_SHARE = 0.4f

    /** How much bluer than red water is, at least. */
    private const val WATER_BLUE = 14

    /** The ice over frozen water, and how opaque it is. */
    private const val ICE_RGB = 0xDDE8F2
    private const val ICE_ALPHA = 0.82f

    /** Whether the picture [assetId] has snow of its own. */
    fun has(assetId: String, objectId: String): Boolean =
        objectId in ROOFS || assetId in DUSTING

    private val ROOFS = setOf(
        StructureData.COTTAGE.objectId, StructureData.HUT.objectId, StructureData.WELL.objectId, StructureData.WINDMILL.objectId,
    )

    /** Which amount the small thing [obj] carries: by where it stands, so neighbours differ. */
    fun variantOf(obj: GridObject): Int = Math.floorMod(obj.gridX * 7 + obj.gridY * 13, VARIANTS)

    /**
     * What the snow on [asset] (standing for [objectId]) is: a roof over its faces, or a dusting;
     * null for anything else. [tileWidth] is the atlas's tile in pixels, to read the roof outlines
     * kept in tile widths ([EdgeSnow.facesOf]) as shares of the picture.
     */
    fun specOf(objectId: String, asset: AssetManifest, tileWidth: Int, variant: Int = 0): SnowCover.Spec? {
        DUSTING[asset.assetId]?.let { most ->
            return SnowCover.Spec(SnowCover.Kind.TOP, (most * VARIANT_AMOUNT[variant % VARIANTS]).coerceAtMost(0.85f))
        }
        val faces = if (objectId == StructureData.WINDMILL.objectId) {
            listOf(WINDMILL_ROOF)
        } else if (objectId == StructureData.WELL.objectId) {
            listOf(WELL_ROOF)
        } else {
            val measured = EdgeSnow.facesOf(objectId, flipped = false) ?: return null
            measured.map { face -> face.points.map { (across, up) -> shareOf(asset, tileWidth, across, up) } }
        }
        val solid = objectId in EdgeSnow.SOLID_ROOFS
        return SnowCover.Spec(SnowCover.Kind.ROOF, amount = if (solid) 0.97f else 0.92f, faces = faces, floor = if (solid) 0.72f else 0.45f, solid = solid)
    }

    /** An offset from the ground point under a picture (tile widths across and up) as shares of the picture. */
    fun shareOf(asset: AssetManifest, tileWidth: Int, across: Float, up: Float): Pair<Float, Float> =
        (asset.anchorX + across * tileWidth / (asset.drawScale * asset.width)) to
            (asset.anchorY - up * tileWidth / (asset.drawScale * asset.height))

    private val masks = HashMap<String, ImageBitmap?>()

    /** The snow over [asset], made from its pixels on [page] the first time it is asked for. */
    fun maskOf(page: ImageBitmap, objectId: String, asset: AssetManifest, tileWidth: Int, variant: Int): ImageBitmap? {
        val key = "${asset.assetId}#$variant"
        return masks.getOrPut(key) {
            val spec = specOf(objectId, asset, tileWidth, variant) ?: return@getOrPut null
            val w = asset.width
            val h = asset.height
            val pixels = IntArray(w * h)
            runCatching { page.asAndroidBitmap().getPixels(pixels, 0, w, asset.atlasX, asset.atlasY, w, h) }.getOrElse { return@getOrPut null }
            val alpha = SnowCover.alphaOf(pixels, w, h, spec, seed = asset.assetId.hashCode() + variant * 31)
            val frozen = objectId in FROZEN
            val colours = IntArray(w * h) { i ->
                if (frozen && i / w < h * WATER_TOP_SHARE && isWater(pixels[i])) {
                    // Ice, not snow, over the bird bath's water.
                    val a = (ICE_ALPHA * 255f).toInt()
                    (a shl 24) or ICE_RGB
                } else {
                    val a = (alpha[i] * 255f).toInt().coerceIn(0, 255)
                    (a shl 24) or SNOW_RGB
                }
            }
            Bitmap.createBitmap(colours, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    /** The snow's white, a touch blue. */
    private const val SNOW_RGB = 0xF6F9FF

    /** Draws the snow on [asset] as the picture is drawn at [anchor], [depth] of it (0 to 1). */
    fun DrawScope.drawPictureSnow(
        page: ImageBitmap,
        objectId: String,
        asset: AssetManifest,
        tileWidth: Int,
        anchor: Offset,
        scale: Float,
        flipped: Boolean,
        variant: Int,
        depth: Float,
    ) {
        if (depth <= 0f) return
        val mask = maskOf(page, objectId, asset, tileWidth, variant) ?: return
        drawSprite(mask, asset.copy(page = 0, atlasX = 0, atlasY = 0), anchor, scale, flipped, alpha = depth)
    }
}
