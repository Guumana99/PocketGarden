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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
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
     * Open water seen from straight above, made to tile ([FlatGround.tileable]): what is laid
     * where two ponds join (#54). Since #57 it is the face of `pond_center.png`, a cell of water
     * alone, rather than the middle of the pond's own picture.
     */
    val pondWater: Bitmap? = null,
    /**
     * The face of a bed of tilled soil seen from straight above, one cell to a copy
     * (`soil_tilled.png`, #57): laid on every bed, its furrows running on from bed to bed.
     */
    val tilled: Bitmap? = null,
    /** The same for a composted bed (`soil_revived.png`, #57). */
    val composted: Bitmap? = null,
) {
    /** The face for a bed: [composted] for a composted one, [tilled] for any other. */
    fun bedFace(isComposted: Boolean): Bitmap? = if (isComposted) composted ?: tilled else tilled

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
     *
     * The wasteland is made only once what the title shows has been read: making it keeps the
     * background threads busy, and beside it the title's ground took seconds longer on a small
     * device (#55). The garden, which needs it, opens only after the title anyway.
     *
     * @return done once what the title shows (the atlas and the ground without the wasteland) has
     *   been read, so that other work at the start can wait for it rather than slow it down.
     */
    fun prefetch(scope: CoroutineScope): Job {
        val titleArt = Job()
        val title = listOf(
            scope.launch { runCatching { loadAtlas() } },
            scope.launch { runCatching { loadTerrainTextures(withWasteland = false) } },
        )
        scope.launch {
            title.joinAll()
            titleArt.complete()
            runCatching { loadWasteland() }
        }
        return titleArt
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
     * (`res/drawable-nodpi`, and the wasteland, the beds and the pond in `res/drawable`), named here
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
                        decodeUnscaled(R.drawable.pond_center)?.let { picture ->
                            val square = FlatGround.region(picture, GroundSlab.POND_CENTER, POND_WATER_FROM, POND_WATER_TO, POND_WATER_SIZE)
                            picture.recycle()
                            // In the colour of the pond's own water, which it runs between (#57: as it
                            // came, it was paler and bluer, and showed as a strip across a join).
                            pond.await()?.let { ring ->
                                val own = FlatGround.region(ring, GroundSlab.POND, POND_OWN_WATER_FROM, POND_OWN_WATER_TO, POND_WATER_SIZE / 4)
                                FlatGround.matchMean(square, own)
                                own.recycle()
                            }
                            FlatGround.tileable(square, POND_WATER_EVEN_OUT).also { square.recycle() }
                        }
                    }
                    val tilled = async(Dispatchers.Default) { bedFace(R.drawable.soil_tilled, GroundSlab.TILLED) }
                    val composted = async(Dispatchers.Default) { bedFace(R.drawable.soil_revived, GroundSlab.REVIVED) }
                    TerrainTextures(
                        riverAutotile = river.await(),
                        cobblestone = cobblestone.await(),
                        pond = pond.await(),
                        pondWater = pondWater.await(),
                        tilled = tilled.await(),
                        composted = composted.await(),
                    )
                }
            }.also { cachedTerrain = it }
        }
        val ground = wasteland?.await() ?: return@coroutineScope rest
        TerrainTextures(rest.riverAutotile, rest.cobblestone, ground, rest.pond, rest.pondWater, rest.tilled, rest.composted)
    }

    /**
     * The top face of the bed picture [id] (laid out as [slab]) seen from straight above, a little
     * in from its edges ([BED_INSET], where the slab's rim catches the light), [BED_FACE_SIZE] square.
     */
    private fun bedFace(@DrawableRes id: Int, slab: GroundSlab): Bitmap? = decodeUnscaled(id)?.let { picture ->
        FlatGround.region(picture, slab, BED_INSET, 1f - BED_INSET, BED_FACE_SIZE).also { picture.recycle() }
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
        // Starts its upload to the graphics memory now, off the main thread, instead of on the
        // first frame that draws the title's garden: it is one large picture (#55).
        bitmap.prepareToDraw()
        return SpriteAtlas(manifest, bitmap)
    }

    companion object {
        /** The open water taken from `pond_center.png`'s face: all of it but its rim (shares of the face). */
        const val POND_WATER_FROM = 0.08f
        const val POND_WATER_TO = 0.92f

        /** The open water of `pond.png`'s face, inside its logs (shares of the face): the colour [POND_WATER_FROM] is brought to. */
        const val POND_OWN_WATER_FROM = 0.25f
        const val POND_OWN_WATER_TO = 0.62f

        /** Share of a bed picture's face left off each side: its lit rim. */
        const val BED_INSET = 0.04f

        /** Pixels a side of a bed's face as unprojected ([TerrainTextures.tilled]). */
        const val BED_FACE_SIZE = 256

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
