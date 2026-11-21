package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.CropScale
import com.studioguu.miniaturegarden.domain.model.CreatureAction
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GreenhouseDoor
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Ground
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.Manner
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import com.studioguu.miniaturegarden.ui.render.GreenhouseLayout
import com.studioguu.miniaturegarden.ui.render.PictureSnow
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FB1114b2: device feedback of 11/14-15 (moss, the greenhouse, the crops, the fences, the well, the rabbit). */
class Fb1114b2Test {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 5000, playerLevel = 30) }
    private val record = RecordCollectionUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, record)

    private class Trusted : SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = true
        override fun isTamperFlagged() = false
        override fun setTamperFlag(flagged: Boolean) = Unit
    }

    private fun source(path: String) = File("src/main/java/com/studioguu/miniaturegarden/$path").readText()

    // --- 2. Moss ---

    @Test
    fun `moss goes into the book once it has grown in, not as it is laid, as a lawn does`() = runBlocking {
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, Trusted()), CalculateOfflineProgressUseCase(), record)
        repo.putSlot(InventorySlot(0, GardenItem.GROUND_MOSS.itemId, 1))
        assertEquals(ActionResult.SUCCESS, place(0, 4, 4, t0))
        assertTrue(repo.collection.isEmpty())
        sync(t0 + 24 * hour, WeatherType.SUNNY)
        assertTrue(repo.collection.isEmpty())
        sync(t0 + (LawnGrowth.HOURS_TO_FULL * hour).toLong() + hour, WeatherType.SUNNY)
        assertEquals(setOf(LawnGrowth.MOSS_OBJECT_ID), repo.collection.keys)
    }

    @Test
    fun `moss just laid shows a bed of damp earth, as a lawn just sown does`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val growing = canvas.substringAfter("if (mossGrowth >= 1f) {").substringBefore("continue")
        assertTrue(growing.contains("drawPath(transform.tilePath(obj.gridX, obj.gridY), MOSS_BED)"))
    }

    // --- 3. The greenhouse ---

    @Test
    fun `a greenhouse goes up over its building time, and nothing is sown or turned in it till then`() = runBlocking {
        val build = GardenItem.FLOOR_GREENHOUSE.buildSeconds
        assertTrue(build > 0)
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_GREENHOUSE.itemId, 1))
        assertEquals(ActionResult.SUCCESS, place(0, 4, 4, t0))
        val site = repo.objects.values.single()
        assertEquals(WorkKind.BUILDING, site.workKind)
        assertEquals(t0 + build * 1000L, site.workEndsAt)
        val grid = GardenGrid(GardenMap.DEFAULT, repo.objects.values.toList())
        assertNotEquals(Ground.GREENHOUSE, grid.groundAt(4, 4))
        assertFalse(grid.isUnderGlass(4, 4))
        assertTrue(GreenhouseLayout.of(repo.objects.values.toList(), GardenMap.DEFAULT::isInside).wallsOf(GridCell(4, 4)).isEmpty())
        assertFalse(GreenhouseDoor.canTurnAt(4, 4, repo.objects.values.toList()))
        // Built, it is a greenhouse, with walls and a door that turns.
        val built = listOf(site.withoutWork())
        assertEquals(Ground.GREENHOUSE, GardenGrid(GardenMap.DEFAULT, built).groundAt(4, 4))
        assertTrue(GreenhouseLayout.of(built, GardenMap.DEFAULT::isInside).wallsOf(GridCell(4, 4)).isNotEmpty())
        assertTrue(GreenhouseDoor.canTurnAt(4, 4, built))
    }

    @Test
    fun `a greenhouse's door turns from its own panel, as a building does, not only in the editor`() {
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("canTurnDoor = (editMode || it.isGreenhouse) && GreenhouseDoor.canTurnAt(it.gridX, it.gridY, gridObjects)"))
    }

    @Test
    fun `under glass a crop that bears again is still there after its harvest out of season`() = runBlocking {
        repo.progress = GardenProgress(season = Season.WINTER)
        repo.put(GridObject(instanceId = "glass", gridX = 3, gridY = 3, objectId = GridObject.GREENHOUSE_OBJECT_ID, lastUpdatedTimestamp = t0))
        repo.put(GridObject(instanceId = "tomato", gridX = 3, gridY = 3, objectId = PlantSpecies.TOMATO.objectId, growthProgress = 1f, lastUpdatedTimestamp = t0))
        val harvest = HarvestPlantUseCase(repo, CalculateOfflineProgressUseCase(), record)
        assertEquals(ActionResult.SUCCESS, harvest("tomato", t0, WeatherType.SUNNY))
        assertNotNull(repo.objects["tomato"])
        // Out in the open the same harvest is its last.
        repo.put(GridObject(instanceId = "out", gridX = 6, gridY = 6, objectId = PlantSpecies.TOMATO.objectId, growthProgress = 1f, lastUpdatedTimestamp = t0))
        repo.till(6, 6)
        assertEquals(ActionResult.SUCCESS, harvest("out", t0, WeatherType.SUNNY))
        assertTrue(repo.objects["out"] == null)
    }

    // --- 5. The crops ---

    @Test
    fun `the hakusai comes down to 0_8, every stage alike, and the poinsettia's seedling keeps its size`() {
        assertEquals(0.8f, CropScale.SHRINKS.getValue("plant_hakusai"), 0f)
        assertTrue("plant_poinsettia_stage1" in CropScale.UNSHRUNK)
        // The poinsettia's other stages and the ornamental cabbage stay as they were.
        assertEquals(0.82f, CropScale.SHRINKS.getValue("plant_poinsettia"), 0f)
        assertEquals(0.8f, CropScale.SHRINKS.getValue("plant_habotan"), 0f)
    }

    @Test
    fun `a crop whose leaves spread below where it stands has a shadow that fades at its foot`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("softFoot = obj.isPlant && sprite.anchorY < SOFT_FOOT_BELOW"))
    }

    // --- 4. The windmill's lights ---

    @Test
    fun `the windmill's sails turn in front of its lit windows`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val lamps = canvas.substringAfter("if (lamps > 0f) {").substringBefore("private val BRIDGE_CELL_FILL")
        assertTrue(lamps.contains("WindmillSails.MILL_OBJECT_ID"))
        assertTrue(lamps.contains("drawSpriteOnWall(atlasImage, sails"))
        assertTrue(canvas.contains("ownFront(obj, base)"))
    }

    // --- 6. The fences ---

    @Test
    fun `fences have no drawn grass at their foot, and their snow lies soft and patchy`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val fence = canvas.substringAfter("val run = obj.runKind").substringBefore("// Two shadows tie a thing to the ground")
        assertFalse(fence.contains("drawGrassAtFoot("))
        // The band as it was (a white line drawn with a pen) is gone from the fences, the soft one in its place.
        assertFalse(fence.contains("drawBand("))
        // FB1117: the snow lies along the pictures' own outlines (FenceSnow), not as soft bands.
        assertFalse(fence.contains("drawSoftBand("))
        assertEquals(3, Regex("FenceSnow\\.maskOf\\(").findAll(fence).count())
    }

    // --- 7. The well ---

    @Test
    fun `the well's roof snow keeps to its tiles, not the beam and the post on its left`() {
        assertTrue(PictureSnow.WELL_ROOF.minOf { it.first } > 0.16f)
        assertTrue(PictureSnow.WELL_ROOF.maxOf { it.second } < 0.38f)
    }

    // --- 8. The rabbit ---

    @Test
    fun `the rabbit hops a cell at a time at most, white or not`() {
        assertEquals(1f, Manner.of(com.studioguu.miniaturegarden.domain.model.Creature.RABBIT).longest, 0f)
        val lawn = (2..8).flatMap { x -> (2..8).map { y -> GridObject(instanceId = "l$x$y", gridX = x, gridY = y, objectId = LawnGrowth.OBJECT_ID, growthProgress = 1f, lastUpdatedTimestamp = 0L) } }
        val grid = GardenGrid(GardenMap.DEFAULT, lawn)
        for (k in 0 until 6) {
            val arrives = 1_000_000L + k * 7_919L
            for (variant in listOf(null, "white")) {
                val route = CreatureRoutes.plan(CreatureVisit(VisitRule.RABBIT_LAWN, GridCell(5, 5), null, arrives, arrives + 240_000L, variant = variant), grid)
                val poses = (arrives until arrives + 240_000L step 20L).mapNotNull { route.poseAt(it) }
                var start: Pair<Float, Float>? = null
                var last: Long = -1
                var at = 0f to 0f
                fun close() {
                    val s = start ?: return
                    assertTrue("a hop of ${hypot(at.first - s.first, at.second - s.second)}", hypot(at.first - s.first, at.second - s.second) <= 1.02f)
                    start = null
                }
                for (pose in poses) {
                    if (pose.action != CreatureAction.STEP || pose.elapsed < last) close()
                    if (pose.action == CreatureAction.STEP) {
                        if (start == null) start = pose.x to pose.y
                        at = pose.x to pose.y
                        last = pose.elapsed
                    } else {
                        last = -1
                    }
                }
                close()
            }
        }
    }
}
