package com.studioguu.miniaturegarden.domain.usecase

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.data.asset.GroundSlab
import com.studioguu.miniaturegarden.data.asset.TestPng
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.CollectionCategory
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventoryGroup
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.ui.render.slabBounds
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The untilled ground is drawn from `soil_wasteland.png`, and the pond (`pond.png`) is a thing
 * bought in the shop, put down on a cell of its own, drawn there and kept in the save.
 */
class PondAndWastelandTest {

    private val t0 = 1_700_000_000_000L

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText()

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 500) }
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))
    private val move = MoveObjectUseCase(repo, GardenMap.DEFAULT)
    private val hoe = HoeUseCase(repo, GardenMap.DEFAULT)

    private fun obj(id: String, x: Int, y: Int, objectId: String) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    private fun pondInBag(slot: Int = 0, amount: Int = 1) =
        repo.putSlot(InventorySlot(slot, GardenItem.DECO_POND.itemId, amount))

    // --- The pictures ---

    @Test
    fun `both pictures are in res drawable and are read through R drawable`() {
        for (name in listOf("soil_wasteland", "pond")) assertTrue(name, File(main, "res/drawable/$name.png").isFile)
        val repository = source("data/asset/AssetRepository.kt")
        // Decoded side by side (#54), each in its own job.
        assertTrue(repository.contains("decodeUnscaled(R.drawable.soil_wasteland)"))
        assertTrue(repository.contains("val pond = async { decodeUnscaled(R.drawable.pond) }"))
    }

    /** The middle and width of the top face, measured off the picture's solid pixels. */
    private fun measure(name: String, darkOnly: Boolean, atCorners: Boolean = false): GroundSlab {
        val image = TestPng.read(File(main, "res/drawable/$name.png"))
        var left = Int.MAX_VALUE
        var right = -1
        val leftYs = ArrayList<Int>()
        val rightYs = ArrayList<Int>()
        // The #57 pictures ([atCorners]) are opaque, on white: the white is not the slab.
        fun solid(x: Int, y: Int): Boolean =
            image.alpha(x, y) > 250 && (!atCorners || image.luminance(x, y) < 210) && (!darkOnly || image.luminance(x, y) < 140)
        // A column is part of the slab when a good few of its pixels are: specks scattered over
        // the white round a picture are not.
        val least = if (atCorners) 12 else 1
        for (x in 0 until image.width) if ((0 until image.height).count { y -> solid(x, y) } >= least) {
            if (x < left) left = x
            if (x > right) right = x
        }
        // Only pixels at the top of a solid run down the column: a speck is alone.
        fun body(x: Int, y: Int): Boolean = solid(x, y) && (!atCorners || (1..5).all { y + it < image.height && solid(x, y + it) })
        for (y in 0 until image.height) {
            for (x in left..left + 3) if (body(x, y)) leftYs += y
            for (x in right - 3..right) if (body(x, y)) rightYs += y
        }
        // [atCorners]: the side corners themselves, the highest solid pixel at each side; for a
        // slab whose sides stand tall under its corners (#57) an average over them falls low.
        val middleY = if (atCorners) (leftYs.min() + rightYs.min()) / 2.0 else (leftYs.average() + rightYs.average()) / 2
        return GroundSlab(
            anchorX = ((left + right) / 2f) / image.width,
            anchorY = (middleY / image.height).toFloat(),
            topWidth = (right - left).toFloat() / image.width,
        )
    }

    @Test
    fun `the face of each picture is where the slab says it is`() {
        for ((name, slab) in listOf(
            "soil_wasteland" to GroundSlab.WASTELAND,
            "soil_tilled" to GroundSlab.TILLED,
            "soil_revived" to GroundSlab.REVIVED,
            "pond_center" to GroundSlab.POND_CENTER,
        )) {
            val measured = measure(name, darkOnly = false, atCorners = true)
            assertEquals(name, slab.anchorX, measured.anchorX, 0.01f)
            // The corners of these photographs are soft and rounded: measured here at the very
            // last columns, by tools/prepare_drawable_art.py over a band 2% wide, which sits up to
            // 0.02 of the picture higher.
            assertEquals(name, slab.anchorY, measured.anchorY, 0.02f)
            assertEquals(name, slab.topWidth, measured.topWidth, 0.015f)
        }
        val pond = measure("pond", darkOnly = false)
        assertEquals(GroundSlab.POND.anchorX, pond.anchorX, 0.01f)
        assertEquals(GroundSlab.POND.anchorY, pond.anchorY, 0.01f)
        assertEquals(GroundSlab.POND.topWidth, pond.topWidth, 0.01f)
    }

    @Test
    fun `a slab is drawn with its face on the cell, as wide as the cell`() {
        val center = Offset(400f, 300f)
        val (left, top, right, bottom) = slabBounds(1536, 768, GroundSlab.WASTELAND, center, tileWidth = 128f)
        val width = right - left
        // The face is the cell's width; the middle of the face is on the cell's middle.
        assertEquals(128f, width * GroundSlab.WASTELAND.topWidth, 1e-3f)
        assertEquals(center.x, left + GroundSlab.WASTELAND.anchorX * width, 1e-3f)
        assertEquals(center.y, top + GroundSlab.WASTELAND.anchorY * (bottom - top), 1e-3f)
        // Half as tall as it is wide, like the picture.
        assertEquals(0.5f, (bottom - top) / width, 1e-4f)
    }

    @Test
    fun `the garden's bare ground is the wasteland, the title's garden keeps its lawn`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("bareGround: BareGround = BareGround.WASTELAND"))
        assertTrue(canvas.contains("val wasteland = if (bareGround == BareGround.WASTELAND) terrain.wasteland else null"))
        assertTrue(canvas.contains("if (wasteland != null) drawFlatGround(wasteland, transform, gardenOutline(transform, max))"))
        assertTrue(source("ui/title/TitleScreen.kt").contains("bareGround = BareGround.LAWN"))
    }

    // --- The pond in the shop ---

    @Test
    fun `the pond is sold among the things to put down`() {
        val pond = GardenItem.DECO_POND
        assertEquals(GridObject.POND_OBJECT_ID, pond.objectId)
        assertEquals(ItemCategory.DECORATION, pond.category)
        assertEquals(75, pond.price)
        assertEquals(20, pond.placeXp)
        assertEquals(0, pond.buildSeconds)
        assertEquals(UnlockCondition.PlayerLevel(4), pond.unlock)
        assertEquals(InventoryGroup.GROUND, InventoryGroup.of(pond))
        val page = CollectionEntry.entries.single { it.objectId == GridObject.POND_OBJECT_ID }
        assertEquals(CollectionCategory.TERRAIN, page.category)
        assertTrue(GridObject.POND_OBJECT_ID in TileSetDefinition.objectIds)
        // Drawn from its own picture, never looked for in the atlas.
        assertFalse(GridObject.POND_OBJECT_ID in TileSetDefinition.expectedSpriteIds)
    }

    // --- Putting it down ---

    @Test
    fun `a pond goes on open ground, pays its experience and fills in its page`() = runBlocking {
        pondInBag()
        assertEquals(ActionResult.SUCCESS, place(0, 3, 4, t0))
        val pond = repo.objects.values.single()
        assertTrue(pond.isPond)
        assertEquals(3 to 4, pond.gridX to pond.gridY)
        assertTrue(repo.slots.getValue(0).isEmpty)
        assertEquals(20L, repo.profile?.currentXp)
        assertTrue(GridObject.POND_OBJECT_ID in repo.collection)
    }

    @Test
    fun `a pond takes its cell to itself, like the river`() = runBlocking {
        assertEquals(PlacementKind.TERRAIN, PlacementKind.forObjectId(GridObject.POND_OBJECT_ID))
        repo.put(obj("stone", 1, 1, GridObject.COBBLESTONE_OBJECT_ID))
        repo.put(obj("bench", 2, 2, "deco_bench"))
        pondInBag(amount = 3)
        // Not on paving, nor on anything standing there.
        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 1, 1, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 2, 2, t0))
        assertEquals(ActionResult.SUCCESS, place(0, 5, 5, t0))
        // And nothing goes into it afterwards: no paving, no bench, no hoe.
        repo.putSlot(InventorySlot(1, GardenItem.FLOOR_COBBLESTONE.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.DECO_BENCH.itemId, 1))
        assertEquals(ActionResult.TILE_OCCUPIED, place(1, 5, 5, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, place(2, 5, 5, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, hoe.till(5, 5, t0))
    }

    @Test
    fun `a pond can be moved to open ground and not onto paving`() = runBlocking {
        repo.put(obj("pond", 0, 0, GridObject.POND_OBJECT_ID))
        repo.put(obj("stone", 4, 4, GridObject.COBBLESTONE_OBJECT_ID))
        assertEquals(ActionResult.TILE_OCCUPIED, move("pond", 4, 4))
        assertEquals(ActionResult.SUCCESS, move("pond", 6, 2))
        assertEquals(6 to 2, repo.objects.getValue("pond").let { it.gridX to it.gridY })
    }

    // --- The save ---

    @Test
    fun `a pond is saved and read back as it was`() {
        val pond = obj("pond-1", 7, 3, GridObject.POND_OBJECT_ID).copy(isFlipped = true)
        val entity = pond.toEntity()
        assertEquals(GridObject.POND_OBJECT_ID, entity.objectId)
        assertEquals(7, entity.gridX)
        assertEquals(3, entity.gridY)
        val back = entity.toDomain()
        assertEquals(pond, back)
        assertTrue(back.isPond)
    }

    @Test
    fun `the pond is drawn in the ground layer from its picture, with a drawing to fall back on`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val branch = canvas.substringAfter("if (obj.isPond) {").substringBefore("continue")
        assertTrue(branch.contains("RenderLayer.BOTTOM_OBJECTS"))
        assertTrue(branch.contains("drawGroundSlab(pond, GroundSlab.POND"))
        assertTrue(branch.contains("drawPondInCode("))
        assertNotNull(GroundSlab.POND)
    }
}
