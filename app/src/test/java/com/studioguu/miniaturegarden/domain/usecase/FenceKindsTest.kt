package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenSnapshot
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.FenceAutotile
import com.studioguu.miniaturegarden.ui.shop.ShelfTag
import com.studioguu.miniaturegarden.ui.shop.ShopShelves
import com.studioguu.miniaturegarden.ui.shop.ShopStage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three kinds of fence, the brick edging, and where the bridge's picture stands. */
class FenceKindsTest {

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private val manifest: AtlasManifest by lazy {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())
    }

    private fun sprite(id: String) = manifest.assets.first { it.assetId == id }

    // --- The shop ---

    @Test
    fun `the shop keeps its fences under one, and the kinds open with the levels`() {
        val shelf = ShopShelves.stock(ShopStage.entries.first { it.category == ItemCategory.STRUCTURE }, Season.SPRING, PlayerStats(level = 20, totalXp = 0L), 9_999L, ownsSymbol = false).map { it.item }
        assertEquals(1, shelf.count { it in GardenItem.FENCE_KINDS })
        assertTrue(GardenItem.STRUCT_EDGING_BRICK in shelf)
        val kinds = ShopShelves.fenceKinds(PlayerStats(level = 5, totalXp = 0L), 9_999L)
        assertEquals(listOf(1, 5, 8), kinds.map { it.item.unlockLevel })
        assertEquals(listOf(15, 25, 35), kinds.map { it.item.price })
        assertEquals(listOf(false, false, true), kinds.map { it.tag == ShelfTag.LOCKED })
        assertEquals(GardenItem.FENCE_KINDS, GardenItem.kindsUnder(GardenItem.STRUCT_FENCE))
        assertNull(GardenItem.kindsUnder(GardenItem.STRUCT_FENCE_WHITE))
    }

    @Test
    fun `the edging opens late, takes one cell and goes up like a fence`() {
        val edging = GardenItem.STRUCT_EDGING_BRICK
        assertEquals(16, edging.unlockLevel)
        assertEquals(60, edging.price)
        assertEquals(GardenItem.STRUCT_FENCE.buildSeconds, edging.buildSeconds)
        assertEquals(Footprint.SINGLE, Footprint.forObjectId(edging.objectId))
    }

    @Test
    fun `every kind has a page in the book`() {
        for (kind in RunKind.entries) assertNotNull(kind.name, CollectionEntry.fromObjectId(kind.objectId))
    }

    // --- Joining ---

    private fun maskIn(objects: List<GridObject>, x: Int, y: Int): Int {
        val grid = GardenGrid(GardenMap.DEFAULT, objects)
        val kind = grid.occupantAt(x, y)?.runKind
        return FenceAutotile.maskAt(x, y) { ax, ay -> grid.occupantAt(ax, ay)?.runKind == kind }
    }

    @Test
    fun `only the same kind joins`() {
        val row = listOf(at(2, 2, RunKind.WOOD_FENCE.objectId), at(3, 2, RunKind.WHITE_FENCE.objectId), at(4, 2, RunKind.BAMBOO_FENCE.objectId), at(5, 2, RunKind.BRICK_EDGING.objectId))
        for (x in 2..5) assertEquals(0, maskIn(row, x, 2))
        val same = listOf(at(2, 2, RunKind.BAMBOO_FENCE.objectId), at(3, 2, RunKind.BAMBOO_FENCE.objectId))
        assertEquals(FenceAutotile.EAST, maskIn(same, 2, 2))
    }

    @Test
    fun `a closed ring of edging has a rail for every side and every cell two neighbours`() {
        val ring = (2..6).flatMap { x -> (2..6).map { y -> x to y } }.filter { (x, y) -> x == 2 || x == 6 || y == 2 || y == 6 }
        val objects = ring.map { (x, y) -> at(x, y, RunKind.BRICK_EDGING.objectId) }
        for ((x, y) in ring) assertEquals("$x,$y", 2, FenceAutotile.arms(maskIn(objects, x, y)).size)
        assertEquals(ring.size, ring.sumOf { (x, y) -> FenceAutotile.railsFrom(maskIn(objects, x, y)).size })
    }

    @Test
    fun `an old save's fences are all wooden, and so joined to the new wooden ones`() {
        for (part in FencePart.entries) assertEquals(part.name, RunKind.WOOD_FENCE, RunKind.of(part.objectId))
        val mixed = listOf(at(2, 2, FencePart.RAIL.objectId), at(3, 2, FencePart.POST.objectId), at(4, 2, FencePart.SECTION.objectId))
        assertEquals(FenceAutotile.EAST or FenceAutotile.WEST, maskIn(mixed, 3, 2))
    }

    @Test
    fun `each goes back into the bag as what it is`() {
        assertEquals(GardenItem.STRUCT_FENCE_WHITE, StoreObjectUseCase.itemFor(at(1, 1, RunKind.WHITE_FENCE.objectId)))
        assertEquals(GardenItem.STRUCT_FENCE_BAMBOO, StoreObjectUseCase.itemFor(at(1, 1, RunKind.BAMBOO_FENCE.objectId)))
        assertEquals(GardenItem.STRUCT_EDGING_BRICK, StoreObjectUseCase.itemFor(at(1, 1, RunKind.BRICK_EDGING.objectId)))
        assertEquals(GardenItem.STRUCT_FENCE, StoreObjectUseCase.itemFor(at(1, 1, FencePart.POST.objectId)))
    }

    // --- What they do ---

    @Test
    fun `any kind of fence will do for the bird, and the edging will not`() {
        for (kind in listOf(RunKind.WOOD_FENCE, RunKind.WHITE_FENCE, RunKind.BAMBOO_FENCE)) {
            val fences = (0..2).map { at(it, 0, kind.objectId) }
            assertTrue(kind.name, VisitRule.BIRD_FENCE.isMetBy(GardenSnapshot(fences, WeatherType.SUNNY)))
        }
        val bricks = (0..2).map { at(it, 0, RunKind.BRICK_EDGING.objectId) }
        assertFalse(VisitRule.BIRD_FENCE.isMetBy(GardenSnapshot(bricks, WeatherType.SUNNY)))
        assertFalse(at(0, 0, RunKind.BRICK_EDGING.objectId).isFence)
    }

    @Test
    fun `nothing walks through any of them`() {
        for (kind in RunKind.entries) {
            val grid = GardenGrid(GardenMap.DEFAULT, listOf(at(3, 3, kind.objectId)))
            assertFalse(kind.name, CreatureRoutes.canWalk(Creature.BIRD, grid, 3, 3))
        }
    }

    @Test
    fun `any of them may stand on a lawn, and a lawn be sown under it`() {
        for (kind in RunKind.entries) {
            val lawn = GardenGrid(GardenMap.DEFAULT, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
            assertEquals(kind.name, PlacementCheck.OK, lawn.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, kind.objectId))
            val standing = GardenGrid(GardenMap.DEFAULT, listOf(at(3, 3, kind.objectId)))
            assertEquals(kind.name, PlacementCheck.OK, standing.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, LawnGrowth.OBJECT_ID))
        }
    }

    // --- The pictures ---

    @Test
    fun `every kind has its pictures, and each is looked for`() {
        for (kind in RunKind.entries) for (id in kind.spriteIds) {
            assertTrue(id, manifest.assets.any { it.assetId == id })
            assertTrue(id, id in TileSetDefinition.expectedSpriteIds)
        }
    }

    @Test
    fun `the three fences' posts are as tall as each other, and the edging a good deal lower`() {
        val posts = RunKind.entries.mapNotNull { it.postSpriteId }.map { sprite(it).height }
        assertEquals(3, posts.size)
        assertTrue(posts.toString(), posts.max() - posts.min() <= 1)
        val brick = sprite(RunKind.BRICK_EDGING.railSpriteId).height.toFloat()
        assertTrue("$brick of ${posts.max()}", brick / posts.max() in 0.3f..0.4f)
    }

    @Test
    fun `a rail fills the way from post to post and goes in under them`() {
        val span = manifest.tileWidth / 2f
        for (kind in RunKind.entries.filter { it.hasPosts }) {
            val rail = sprite(kind.railSpriteId)
            assertEquals(kind.name, 0.5f, rail.anchorX, 1e-3f)
            assertNotNull(kind.name, rail.attachY)
            assertTrue(kind.name, rail.width in (span * 0.8f).toInt()..span.toInt())
        }
        // Without posts, one cell's bricks reach the next cell's, their ends on the slant.
        assertTrue(sprite(RunKind.BRICK_EDGING.railSpriteId).width >= span)
    }

    @Test
    fun `the bridge is drawn no wider than its footprint`() {
        val half = manifest.tileWidth / 2f
        for ((id, cells) in listOf(StructureData.BRIDGE.objectId to 3, "struct_bridge_taiko_long" to 4)) {
            val bridge = sprite(id)
            assertTrue(id, bridge.width * bridge.drawScale <= (cells + 1) * half)
        }
    }
}
