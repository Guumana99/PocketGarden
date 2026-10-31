package com.studioguu.miniaturegarden.data.asset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.render.WaterArt
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
 * An atlas' decoded page images together with its manifest, one bitmap a page in the order of
 * [AtlasManifest.pages]. [EMPTY] (no images, no sprites) is what the app runs with until real
 * artwork is bundled; lookups then return null and callers fall back to their placeholder drawing.
 */
class SpriteAtlas(
    val manifest: AtlasManifest,
    val pages: List<Bitmap>,
    private val creatureAtlas: SpriteAtlas? = null,
) {
    private val byId: Map<String, AssetManifest> = manifest.assets.associateBy { it.assetId }

    /**
     * The visitors' pictures, packed in a small atlas of their own (`assets/atlas/creatures_manifest.json`,
     * see [TileSetDefinition.expectedCreatureSpriteIds]) rather than in this one: they are drawn
     * at a scale of their own ([AtlasManifest.tileWidth]). [EMPTY] until they are bundled.
     */
    val creatures: SpriteAtlas get() = creatureAtlas ?: EMPTY

    /** Metadata for [assetId], or null if the sprite is not in the atlas (or no image is loaded). */
    operator fun get(assetId: String): AssetManifest? = if (pages.isEmpty()) null else byId[assetId]

    /** The page image [asset] is packed on. */
    fun pageOf(asset: AssetManifest): Bitmap = pages[asset.page]

    companion object {
        val EMPTY = SpriteAtlas(AtlasManifest(), pages = emptyList())
    }
}

/**
 * Flat, seen-from-above pictures that are laid onto the ground. Each is optional: with one
 * missing, that kind of tile is drawn as a plain shape instead.
 *
 * @property water the river's and the pond's water, and the river's banks ([WaterArt]).
 */
class TerrainTextures(
    val water: WaterArt? = null,
    /** Paving seen from straight above, one copy to a cell (`floor_cobblestone.png`, #74). */
    val cobblestone: Bitmap? = null,
    /**
     * The untilled ground every cell starts as: the earth of `soil_wasteland.png` flattened into
     * a square texture ([FlatGround]), laid over the whole garden as one field.
     */
    val wasteland: Bitmap? = null,
    /** The pond, a cell of water in a ring of stones and logs ([GroundSlab.POND]). */
    val pond: Bitmap? = null,
    /**
     * Open water seen from straight above, made to tile: what is laid where two ponds join (#54),
     * the same water as the river's ([WaterArt.surface]).
     */
    val pondWater: Bitmap? = null,
    /**
     * The face of a bed of tilled soil seen from straight above, one cell to a copy
     * (`soil_tilled.png`, #57): laid on every bed, its furrows running on from bed to bed. A
     * composted bed is this one darkened and flecked with leaf and straw (#61).
     */
    val tilled: Bitmap? = null,
    /**
     * Ground the hoe has broken up but not yet tilled (#61): loose, turned earth with no furrows
     * in it, the face of `soil_revived.png` seen from straight above. It is the step between the
     * dry wasteland and a bed, shown on hard ground that has had some of the strikes it needs.
     * (From #57 to #60 it was the face of a composted bed instead.)
     */
    val broken: Bitmap? = null,
    /**
     * Pebbles, wild flowers in grass and wooden decking (#74), each seen from straight above and
     * made to tile, one copy to a cell, like [cobblestone]: the top faces of the slabs they came
     * as (`tools/prepare_drawable_art.py`).
     */
    val pebbles: Bitmap? = null,
    val flowers: Bitmap? = null,
    val woodDeck: Bitmap? = null,
) {
    /** The picture laid over a cell of ground cover [objectId] (pebbles, wild flowers), or null. */
    fun coverFor(objectId: String): Bitmap? = when (objectId) {
        GroundCover.PEBBLE.objectId -> pebbles
        GroundCover.FLOWERS.objectId -> flowers
        else -> null
    }

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
     * bundled; throws if one is bundled but its manifest or images cannot be read. Its pages and
     * the visitors' atlas are decoded side by side.
     */
    suspend fun loadAtlas(): SpriteAtlas = atlasMutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            coroutineScope {
                val creatures = async { readAtlas(CREATURE_MANIFEST_FILE) }
                // The crops' stages are brought to a natural size next to one another (CropScale).
                val main = readAtlas(MANIFEST_FILE, CropScale::normalized)
                if (main === SpriteAtlas.EMPTY) main else SpriteAtlas(main.manifest, main.pages, creatureAtlas = creatures.await())
            }
        }.also { cached = it }
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
                    val water = async { decodeWater() }
                    val cobblestone = async { decodeGround(R.drawable.floor_cobblestone) }
                    val pebbles = async { decodeGround(R.drawable.ground_pebble) }
                    val flowers = async { decodeGround(R.drawable.ground_flowers) }
                    val woodDeck = async { decodeGround(R.drawable.floor_wood) }
                    val pond = async { decodeUnscaled(R.drawable.pond) }
                    val tilled = async(Dispatchers.Default) { bedFace(R.drawable.soil_tilled, GroundSlab.TILLED) }
                    val broken = async(Dispatchers.Default) { bedFace(R.drawable.soil_revived, GroundSlab.REVIVED) }
                    TerrainTextures(
                        water = water.await(),
                        cobblestone = cobblestone.await(),
                        pond = pond.await(),
                        pondWater = water.await()?.surface,
                        tilled = tilled.await(),
                        broken = broken.await(),
                        pebbles = pebbles.await(),
                        flowers = flowers.await(),
                        woodDeck = woodDeck.await(),
                    )
                }
            }.also { cachedTerrain = it }
        }
        val ground = wasteland?.await() ?: return@coroutineScope rest
        TerrainTextures(
            rest.water, rest.cobblestone, ground, rest.pond, rest.pondWater, rest.tilled, rest.broken,
            rest.pebbles, rest.flowers, rest.woodDeck,
        )
    }

    /** The river's pictures; null if any is missing. */
    private fun decodeWater(): WaterArt? {
        return WaterArt(
            surface = decodeUnscaled(R.drawable.water_surface) ?: return null,
            rippleA = decodeUnscaled(R.drawable.water_ripple_a) ?: return null,
            rippleB = decodeUnscaled(R.drawable.water_ripple_b) ?: return null,
            bank = decodeUnscaled(R.drawable.river_bank) ?: return null,
            bankBend = decodeUnscaled(R.drawable.river_bank_concave) ?: return null,
            bankPoint = decodeUnscaled(R.drawable.river_bank_convex) ?: return null,
        )
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
     * A picture laid over the ground cell by cell ([TerrainTextures.cobblestone] and the like):
     * with mipmaps, as it is drawn many times smaller than it is, so that its stones and flowers
     * blend rather than glitter as grain (as the lawn's, #53).
     */
    private fun decodeGround(@DrawableRes id: Int): Bitmap? = decodeUnscaled(id)?.also { it.setHasMipMap(true) }

    /**
     * Decodes at the file's own pixel size, whatever the screen density: a picture in plain
     * `res/drawable` would otherwise be scaled up as if it were drawn for the lowest density.
     */
    private fun decodeUnscaled(@DrawableRes id: Int): Bitmap? =
        BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })

    /**
     * The atlas described by [manifestFile] in `assets/atlas/`, its manifest passed through
     * [adjust], with every page decoded, side by side; an empty one while none is bundled.
     */
    private suspend fun readAtlas(manifestFile: String, adjust: (AtlasManifest) -> AtlasManifest = { it }): SpriteAtlas {
        val manifestText = try {
            context.assets.open("$ATLAS_DIR/$manifestFile").use { it.readBytes().decodeToString() }
        } catch (_: FileNotFoundException) {
            return SpriteAtlas.EMPTY
        }
        val manifest = adjust(AtlasManifest.parse(manifestText))
        val pages = coroutineScope {
            manifest.pages.map { page -> async { readPage(page) } }.map { it.await() }
        }
        return SpriteAtlas(manifest, pages)
    }

    private fun readPage(page: AtlasPage): Bitmap {
        val bitmap = context.assets.open("$ATLAS_DIR/${page.file}").use(BitmapFactory::decodeStream)
            ?: error("Atlas page '${page.file}' could not be decoded")
        check(bitmap.width == page.width && bitmap.height == page.height) {
            "Atlas page '${page.file}' is ${bitmap.width}x${bitmap.height} but the manifest says " +
                "${page.width}x${page.height}; re-run tools/asset_packer.py"
        }
        // Starts its upload to the graphics memory now, off the main thread, instead of on the
        // first frame that draws the title's garden (#55).
        bitmap.prepareToDraw()
        return bitmap
    }

    companion object {
        /** The open water taken from `pond_center.png`'s face: all of it but its rim (shares of the face). */
        const val POND_WATER_FROM = 0.08f
        const val POND_WATER_TO = 0.92f

        /** The open water of `pond.png`'s face, inside its logs (shares of the face): the colour [POND_WATER_FROM] is brought to. */
        const val POND_OWN_WATER_FROM = 0.25f
        const val POND_OWN_WATER_TO = 0.62f

        /**
         * Share of a bed picture's face left off each side: its lit rim, and on the far side of
         * soil_tilled the white behind the furrows, which dip below the slab's edge there (it
         * reaches 0.068 in; at 0.04 it showed as white flecks where two beds meet, device
         * feedback after #75).
         */
        const val BED_INSET = 0.08f

        /** Pixels a side of a bed's face as unprojected ([TerrainTextures.tilled]). */
        const val BED_FACE_SIZE = 256

        /** Pixels a side of the square the water is unprojected into, before it is made to tile. */
        const val POND_WATER_SIZE = 256

        /** How large the light across the water taken out of it is, at least (pixels of [POND_WATER_SIZE]). */
        const val POND_WATER_EVEN_OUT = 20

        /** How much of a cell one copy of [TerrainTextures.pondWater] spans: the part of the face its tile came from. */
        const val POND_WATER_CELLS = 2f

        /** The folder under the cache directory where made ground is kept. */
        const val GROUND_CACHE_DIR = "ground"

        const val ATLAS_DIR = "atlas"
        const val MANIFEST_FILE = "assets_manifest.json"
        const val CREATURE_MANIFEST_FILE = "creatures_manifest.json"
    }
}
