package com.studioguu.miniaturegarden.data.asset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.studioguu.miniaturegarden.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    /**
     * The untilled ground every cell starts as: the earth of `soil_wasteland.png` flattened into
     * a square texture ([FlatGround]), laid over the whole garden as one field.
     */
    val wasteland: Bitmap? = null,
    /** The pond, a cell of water in a ring of stones and logs ([GroundSlab.POND]). */
    val pond: Bitmap? = null,
    /**
     * The pond's open water alone, unprojected into a small square ([FlatGround.region]): what is
     * laid where two ponds join, so that the water running through is the pond's own (#54).
     */
    val pondWater: Bitmap? = null,
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
    // One lock each, so that the atlas and the ground are read at the same time: under a single
    // lock the title waited for one after the other.
    private val atlasMutex = Mutex()
    private val terrainMutex = Mutex()
    private val wastelandMutex = Mutex()
    private var cached: SpriteAtlas? = null

    // Writes the made wasteland to the cache without holding up whoever is waiting for it.
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts reading all the artwork in the background, as the app starts, before any screen
     * asks for it: the title's sample garden then appears as soon as it is drawn, rather than
     * after the atlas has been decoded, and the wasteland is ready by the time the garden opens.
     * Later calls to [loadAtlas] and [loadTerrainTextures] wait for these reads instead of
     * starting their own.
     */
    fun prefetch(scope: CoroutineScope) {
        scope.launch { runCatching { loadAtlas() } }
        scope.launch { runCatching { loadTerrainTextures(withWasteland = false) } }
        scope.launch { runCatching { loadWasteland() } }
    }

    /**
     * Returns the atlas, loading it on first use. Yields [SpriteAtlas.EMPTY] when no atlas is
     * bundled; throws if one is bundled but its manifest or image cannot be read.
     */
    suspend fun loadAtlas(): SpriteAtlas = atlasMutex.withLock {
        cached ?: withContext(Dispatchers.IO) { readAtlas() }.also { cached = it }
    }

    private var cachedTerrain: TerrainTextures? = null
    private var cachedWasteland: Bitmap? = null
    private var wastelandRead = false

    /**
     * Returns the ground textures, loading them on first use. They are drawable resources
     * (`res/drawable-nodpi`, and the wasteland and the pond in `res/drawable`), named here
     * through `R.drawable`, so a missing or renamed file is a build error rather than a tile that
     * silently fails to appear. The pictures are decoded side by side.
     *
     * [withWasteland] false leaves out the wasteland ([loadWasteland], the slowest to make): the
     * title's garden is grown over and does not show it, and need not wait for it (#54).
     */
    suspend fun loadTerrainTextures(withWasteland: Boolean = true): TerrainTextures = coroutineScope {
        val wasteland = if (withWasteland) async { loadWasteland() } else null
        val rest = terrainMutex.withLock {
            cachedTerrain ?: withContext(Dispatchers.IO) {
                coroutineScope {
                    val river = async { decodeUnscaled(R.drawable.tile_river_autotile) }
                    val cobblestone = async { decodeUnscaled(R.drawable.tile_path_cobblestone) }
                    val pond = async { decodeUnscaled(R.drawable.pond) }
                    val pondWater = async(Dispatchers.Default) {
                        pond.await()?.let { picture ->
                            val square = FlatGround.region(picture, GroundSlab.POND, POND_WATER_FROM, POND_WATER_TO, POND_WATER_SIZE)
                            FlatGround.tileable(square, POND_WATER_EVEN_OUT).also { square.recycle() }
                        }
                    }
                    TerrainTextures(
                        riverAutotile = river.await(),
                        cobblestone = cobblestone.await(),
                        pond = pond.await(),
                        pondWater = pondWater.await(),
                    )
                }
            }.also { cachedTerrain = it }
        }
        val ground = wasteland?.await() ?: return@coroutineScope rest
        TerrainTextures(rest.riverAutotile, rest.cobblestone, ground, rest.pond, rest.pondWater)
    }

    /**
     * The untilled ground ([TerrainTextures.wasteland]): `soil_wasteland.png` made into a field
     * of earth ([FlatGround.flatten]), which takes a while, so it is made once for each installed
     * build of the app and kept in the cache directory as a picture ([wastelandCacheFile]); later
     * starts only read that back. Null when the picture cannot be read.
     */
    suspend fun loadWasteland(): Bitmap? = wastelandMutex.withLock {
        if (wastelandRead) return@withLock cachedWasteland
        withContext(Dispatchers.Default) {
            val file = wastelandCacheFile()
            readCachedField(file) ?: decodeUnscaled(R.drawable.soil_wasteland)?.let { slab ->
                FlatGround.flatten(slab).also { slab.recycle() }
            }?.also { field -> writer.launch { writeCachedField(field, file) } }
        }.also {
            cachedWasteland = it
            wastelandRead = true
        }
    }

    /**
     * Where the made wasteland is kept: named for this build of the app (when it was installed or
     * updated) and for the way it is made ([FlatGround.FIELD_VERSION]), so that an update with
     * new art or a new way never reads an old one back.
     */
    private fun wastelandCacheFile(): File {
        @Suppress("DEPRECATION")
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        return File(File(context.cacheDir, GROUND_CACHE_DIR), "wasteland-${FlatGround.FIELD_VERSION}-$installed.png")
    }

    private fun readCachedField(file: File): Bitmap? {
        if (!file.isFile) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return null
        return bitmap.takeIf { it.width == FlatGround.FIELD_SIZE && it.height == FlatGround.FIELD_SIZE }
    }

    private fun writeCachedField(field: Bitmap, file: File) {
        runCatching {
            val dir = file.parentFile ?: return
            dir.mkdirs()
            // Only the one for this build is kept.
            dir.listFiles()?.filter { it.name != file.name }?.forEach { it.delete() }
            val partial = File(dir, file.name + ".part")
            partial.outputStream().use { field.compress(Bitmap.CompressFormat.PNG, 100, it) }
            partial.renameTo(file)
        }
    }

    /**
     * Decodes at the file's own pixel size, whatever the screen density: a picture in plain
     * `res/drawable` would otherwise be scaled up as if it were drawn for the lowest density.
     */
    private fun decodeUnscaled(@DrawableRes id: Int): Bitmap? =
        BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })

    private fun readAtlas(): SpriteAtlas {
        val manifestText = try {
            context.assets.open("$ATLAS_DIR/$MANIFEST_FILE").use { it.readBytes().decodeToString() }
        } catch (_: FileNotFoundException) {
            return SpriteAtlas.EMPTY
        }
        // The crops' stages are brought to a natural size next to one another (CropScale).
        val manifest = CropScale.normalized(AtlasManifest.parse(manifestText))
        val bitmap = context.assets.open("$ATLAS_DIR/${manifest.atlas}").use(BitmapFactory::decodeStream)
            ?: error("Atlas image '${manifest.atlas}' could not be decoded")
        check(bitmap.width == manifest.atlasWidth && bitmap.height == manifest.atlasHeight) {
            "Atlas image is ${bitmap.width}x${bitmap.height} but the manifest says " +
                "${manifest.atlasWidth}x${manifest.atlasHeight}; re-run tools/asset_packer.py"
        }
        return SpriteAtlas(manifest, bitmap)
    }

    companion object {
        /** The open water of the pond's face, inside its logs and away from them (shares of the face). */
        const val POND_WATER_FROM = 0.25f
        const val POND_WATER_TO = 0.62f

        /** Pixels a side of the square the water is unprojected into, before it is made to tile. */
        const val POND_WATER_SIZE = 256

        /** How large the light across the water taken out of it is, at least (pixels of [POND_WATER_SIZE]). */
        const val POND_WATER_EVEN_OUT = 20

        /** How much of a cell one copy of [TerrainTextures.pondWater] spans: the part of the face its tile came from. */
        const val POND_WATER_CELLS = (POND_WATER_TO - POND_WATER_FROM) * FlatGround.TILE_SIZE / FlatGround.SIZE

        /** The folder under the cache directory where made ground is kept. */
        const val GROUND_CACHE_DIR = "ground"

        const val ATLAS_DIR = "atlas"
        const val MANIFEST_FILE = "assets_manifest.json"
    }
}
