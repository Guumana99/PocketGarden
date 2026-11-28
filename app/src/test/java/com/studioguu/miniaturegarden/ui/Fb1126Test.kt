package com.studioguu.miniaturegarden.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.ui.effects.anchoredTopLeft
import com.studioguu.miniaturegarden.ui.render.IsometricTransform
import com.studioguu.miniaturegarden.ui.render.PictureHit
import com.studioguu.miniaturegarden.ui.render.TapTargets
import com.studioguu.miniaturegarden.ui.render.TileCoord
import com.studioguu.miniaturegarden.ui.render.TilePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The device feedback of 11/26-27 (FB1126): taps near other plants, and where a harvest's sum stands. */
class Fb1126Test {

    private val transform = IsometricTransform(tileWidth = 180f, tileHeight = 90f, heightStep = 22f, offsetX = 540f, offsetY = 300f)

    // A crop's picture: half a tile wide and most of a tile tall, standing on the middle of its cell.
    private val picture = AssetManifest(assetId = "p", anchorX = 0.5f, anchorY = 0.9f, width = 100, height = 160)
    private val shown = PictureHit.Shown(picture, 1f)

    private fun plant(id: String, x: Int, y: Int, status: PlantStatus = PlantStatus.HEALTHY) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = "plant_strawberry", lastUpdatedTimestamp = 0L, growthProgress = 1f, status = status)

    private fun base(obj: GridObject) = transform.worldToScreen(obj.gridX + 0.5f, obj.gridY + 0.5f)

    private fun tap(at: Offset, objects: List<GridObject>, opaque: (AssetManifest, Int, Int) -> Boolean = { _, _, _ -> true }): TileCoord? {
        val grid = GardenGrid(GardenMap.allGrass(9), objects)
        return TapTargets.resolve(
            at, transform, grid, objects, editing = false, minReach = 60f,
            plantTop = { picture.anchorY * picture.drawnHeight(1f) },
            picture = { PictureHit.frontMost(it, transform, grid, objects, { shown }, opaque) },
        )
    }

    private val back = plant("back", 4, 4)
    private val weak = plant("weak", 4, 5, PlantStatus.UNNOURISHED)

    @Test
    fun `a weak plant's bubble does not take the taps on the fruit behind it`() {
        val bubble = TapTargets.bubbleCentre(weak, transform, picture.anchorY * picture.drawnHeight(1f))!!
        // On the back plant's picture, clear of the bubble's drawn circle but within its old reach.
        val fruit = bubble + Offset(transform.tileWidth * 0.2f, -transform.tileWidth * 0.1f)
        // Nothing of the front plant's picture is there (its picture's clear corner).
        val clear: (AssetManifest, Int, Int) -> Boolean = { _, x, _ -> x >= 0 }
        val objects = listOf(back, weak)
        val grid = GardenGrid(GardenMap.allGrass(9), objects)
        val seen = TapTargets.resolve(fruit, transform, grid, objects, editing = false, minReach = 60f,
            plantTop = { picture.anchorY * picture.drawnHeight(1f) }, picture = { back })
        assertEquals(TileCoord(4, 4), seen)
        // Right on the bubble, it is the weak plant.
        assertEquals(TileCoord(4, 5), tap(bubble, objects, clear))
    }

    @Test
    fun `a tap on a plant's picture picks the plant, though the ground there is another cell`() {
        val head = base(back) + Offset(0f, -120f)
        val ground = TilePicker.pick(head, transform)!!
        assertEquals(false, ground == TileCoord(4, 4))
        assertEquals(TileCoord(4, 4), tap(head, listOf(back)))
        // Through a clear part of it, the ground behind.
        assertEquals(ground, tap(head, listOf(back)) { _, _, _ -> false })
    }

    @Test
    fun `of two pictures over one point, the one drawn in front wins`() {
        val front = plant("front", 5, 5)
        // A point on both: the front plant stands a tile height below the back one.
        val both = base(back) + Offset(0f, -20f)
        assertEquals(TileCoord(5, 5), tap(both, listOf(front, back)))
        assertEquals(TileCoord(5, 5), tap(both, listOf(back, front)))
    }

    @Test
    fun `a turned picture is read mirrored`() {
        val leftHalf: (AssetManifest, Int, Int) -> Boolean = { _, x, _ -> x < 50 }
        val foot = Offset(100f, 200f)
        val left = foot + Offset(-30f, -50f)
        assertEquals(true, PictureHit.covers(left, foot, shown, flipped = false, opaque = leftHalf))
        assertEquals(false, PictureHit.covers(left, foot, shown, flipped = true, opaque = leftHalf))
    }

    @Test
    fun `flat things in the ground are not read as pictures`() {
        val lawn = GridObject(instanceId = "lawn", gridX = 4, gridY = 4, objectId = "ground_grass", lastUpdatedTimestamp = 0L)
        val soil = GridObject(instanceId = "soil", gridX = 4, gridY = 4, objectId = GridObject.TILLED_SOIL_OBJECT_ID, lastUpdatedTimestamp = 0L)
        val hut = GridObject(instanceId = "hut", gridX = 1, gridY = 1, objectId = StructureData.HUT.objectId, lastUpdatedTimestamp = 0L)
        val grid = GardenGrid(GardenMap.allGrass(9), listOf(lawn, soil, hut))
        assertNull(PictureHit.frontMost(base(back), transform, grid, listOf(lawn, soil), { shown }) { _, _, _ -> true })
    }

    // --- 3. Where a harvest's sum stands ---

    private val area = Rect(10f, 200f, 1070f, 1500f)

    @Test
    fun `a sum stands centred over its point and rises from it`() {
        assertEquals(Offset(450f, 720f), anchoredTopLeft(Offset(500f, 800f), 100f, 80f, area, rise = 0f))
        assertEquals(Offset(450f, 690f), anchoredTopLeft(Offset(500f, 800f), 100f, 80f, area, rise = 30f))
    }

    @Test
    fun `near an edge or behind the panel it is moved inside, and still rises`() {
        // Past the right edge.
        assertEquals(970f, anchoredTopLeft(Offset(1060f, 800f), 100f, 80f, area, rise = 0f).x, 0.001f)
        // Behind the panel at the foot: just above it, then rising.
        assertEquals(1420f, anchoredTopLeft(Offset(500f, 1900f), 100f, 80f, area, rise = 0f).y, 0.001f)
        assertEquals(1390f, anchoredTopLeft(Offset(500f, 1900f), 100f, 80f, area, rise = 30f).y, 0.001f)
        // Above the top bar's foot, never over it.
        assertEquals(200f, anchoredTopLeft(Offset(500f, 100f), 100f, 80f, area, rise = 30f).y, 0.001f)
    }
}
