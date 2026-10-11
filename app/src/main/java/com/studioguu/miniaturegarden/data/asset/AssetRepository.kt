package com.studioguu.miniaturegarden.data.asset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.studioguu.miniaturegarden.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A decoded atlas image together with its manifest. [EMPTY] (no image, no sprites) is what the
 * app runs with until real artwork is bundled; lookups then return null and callers fall back
 * to their placeholder drawing.
 */
class SpriteAtlas(
    val manifest: AtlasManifest,
    val bitmap: Bitmap?,
) {
    private val byId: Map<String, AssetManifest> = manifest.assets.associateBy { it.assetId }

    /** Metadata for [assetId], or null if the sprite is not in the atlas (or no image is loaded). */
    operator fun get(assetId: String): AssetManifest? = if (bitmap == null) null else byId[assetId]

    companion object {
        val EMPTY = SpriteAtlas(AtlasManifest(), bitmap = null)
    }
}

/**
 * Flat, seen-from-above pictures that are laid onto the ground. Each is optional: with one
 * missing, that kind of tile is drawn as a plain shape instead.
 *
 * @property riverAutotile river tiles, one per neighbour mask, in one 4 x 4 block per animation
 *   frame with the blocks stacked top to bottom (see RiverAutotile).
 */
class TerrainTextures(
    val riverAutotile: Bitmap? = null,
    val cobblestone: Bitmap? = null,
) {
    companion object {
        val EMPTY = TerrainTextures()
    }
}

/**
 * Loads the sprite atlas from the APK's `assets/atlas/` directory, off the main thread, and
 * keeps it in memory for the life of the process.
 */
@Singleton
class AssetRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cached: SpriteAtlas? = null

    /**
     * Returns the atlas, loading it on first use. Yields [SpriteAtlas.EMPTY] when no atlas is
     * bundled; throws if one is bundled but its manifest or image cannot be read.
     */
    suspend fun loadAtlas(): SpriteAtlas = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { readAtlas() }.also { cached = it }
    }

    private var cachedTerrain: TerrainTextures? = null

    /**
     * Returns the ground textures, loading them on first use. They are drawable resources
     * (`res/drawable-nodpi`), named here through `R.drawable`, so a missing or renamed file is a
     * build error rather than a tile that silently fails to appear.
     */
    suspend fun loadTerrainTextures(): TerrainTextures = mutex.withLock {
        cachedTerrain ?: withContext(Dispatchers.IO) {
            TerrainTextures(
                riverAutotile = decodeUnscaled(R.drawable.tile_river_autotile),
                cobblestone = decodeUnscaled(R.drawable.tile_path_cobblestone),
            )
        }.also { cachedTerrain = it }
    }

    /** Decodes at the file's own pixel size, whatever the screen density. */
    private fun decodeUnscaled(@DrawableRes id: Int): Bitmap? =
        BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })

    private fun readAtlas(): SpriteAtlas {
        val manifestText = try {
            context.assets.open("$ATLAS_DIR/$MANIFEST_FILE").use { it.readBytes().decodeToString() }
        } catch (_: FileNotFoundException) {
            return SpriteAtlas.EMPTY
        }
        val manifest = AtlasManifest.parse(manifestText)
        val bitmap = context.assets.open("$ATLAS_DIR/${manifest.atlas}").use(BitmapFactory::decodeStream)
            ?: error("Atlas image '${manifest.atlas}' could not be decoded")
        check(bitmap.width == manifest.atlasWidth && bitmap.height == manifest.atlasHeight) {
            "Atlas image is ${bitmap.width}x${bitmap.height} but the manifest says " +
                "${manifest.atlasWidth}x${manifest.atlasHeight}; re-run tools/asset_packer.py"
        }
        return SpriteAtlas(manifest, bitmap)
    }

    companion object {
        const val ATLAS_DIR = "atlas"
        const val MANIFEST_FILE = "assets_manifest.json"
    }
}
