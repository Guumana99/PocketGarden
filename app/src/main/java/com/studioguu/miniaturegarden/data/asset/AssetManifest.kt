package com.studioguu.miniaturegarden.data.asset

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Metadata for one sprite. The anchor is the point of the image that stands on the ground
 * (the "feet"); drawing places that point on the tile center.
 */
@Serializable
data class AssetManifest(
    /** e.g. "plant_tulip_stage3" */
    val assetId: String,
    /** Ground-contact X as a fraction of the image width, 0 (left) .. 1 (right). */
    val anchorX: Float = 0.5f,
    /** Ground-contact Y as a fraction of the image height, 0 (top) .. 1 (bottom). */
    val anchorY: Float = 0.8f,
    val width: Int,
    val height: Int,
    /** Whether the sprite may be drawn mirrored to create a left/right variation. */
    val isFlippedAllowed: Boolean = true,
    /** Top-left corner of the sprite inside the atlas image. */
    val atlasX: Int = 0,
    val atlasY: Int = 0,
) {
    /** Left edge on screen when the anchor sits at [screenX] and the sprite is drawn at [scale]. */
    fun drawLeft(screenX: Float, scale: Float): Float = screenX - anchorX * width * scale

    /** Top edge on screen when the anchor sits at [screenY] and the sprite is drawn at [scale]. */
    fun drawTop(screenY: Float, scale: Float): Float = screenY - anchorY * height * scale
}

/**
 * Contents of `assets_manifest.json` as written by `tools/asset_packer.py`: one atlas image
 * plus the sprites packed into it.
 */
@Serializable
data class AtlasManifest(
    val version: Int = 1,
    /** Atlas image file name, relative to the manifest. */
    val atlas: String = "",
    val atlasWidth: Int = 0,
    val atlasHeight: Int = 0,
    /** How many source pixels one grid tile spans; sets the sprite-to-screen scale. */
    val tileWidth: Int = DEFAULT_TILE_WIDTH,
    val assets: List<AssetManifest> = emptyList(),
) {
    companion object {
        const val DEFAULT_TILE_WIDTH = 256

        private val json = Json { ignoreUnknownKeys = true }

        /** @throws IllegalArgumentException if the JSON is malformed or describes an impossible atlas. */
        fun parse(text: String): AtlasManifest = json.decodeFromString<AtlasManifest>(text).also { it.validate() }
    }

    private fun validate() {
        require(tileWidth > 0) { "tileWidth must be positive, was $tileWidth" }
        val seen = HashSet<String>()
        for (asset in assets) {
            val id = asset.assetId
            require(seen.add(id)) { "Duplicate assetId '$id'" }
            require(asset.width > 0 && asset.height > 0) { "'$id' has an empty size ${asset.width}x${asset.height}" }
            require(asset.anchorX in 0f..1f && asset.anchorY in 0f..1f) {
                "'$id' anchor (${asset.anchorX}, ${asset.anchorY}) is outside 0..1"
            }
            require(
                asset.atlasX >= 0 && asset.atlasY >= 0 &&
                    asset.atlasX + asset.width <= atlasWidth && asset.atlasY + asset.height <= atlasHeight
            ) { "'$id' does not fit inside the ${atlasWidth}x$atlasHeight atlas" }
        }
    }
}
