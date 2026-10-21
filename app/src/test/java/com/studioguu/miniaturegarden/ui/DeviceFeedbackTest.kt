package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import com.studioguu.miniaturegarden.data.asset.TestPng
import com.studioguu.miniaturegarden.data.local.AppDatabase
import com.studioguu.miniaturegarden.domain.model.FarmTool
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.LawnStage
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase
import com.studioguu.miniaturegarden.ui.render.PondJoin
import com.studioguu.miniaturegarden.ui.render.SoilPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch of #55, from playing on a device: the hoe's level out of sight, a quiet book with
 * "NEW" on it, a faster title, a question before the garden grows, seedlings without a second
 * clump of earth, fences that stand alone as one panel and on a lawn, the cottage on its lot,
 * the ring of logs closed round joined ponds, beds of the wasteland's own earth, and lawns that
 * grow in stages.
 */
class DeviceFeedbackTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val tools: File = listOf(File("../tools"), File("tools")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")
    private val strings by lazy { File(main, "res/values/strings.xml").readText() }

    // --- 1.1 The hoe's level ---

    @Test
    fun `the hoe keeps its level out of sight, the watering can still shows its own`() {
        assertFalse(FarmTool.HOE.showsLevel)
        assertTrue(FarmTool.WATERING_CAN.showsLevel)
        // It still learns: the level is kept and does its work.
        assertEquals(2, FarmTool.HOE.levelFor(15))
        assertTrue(source("ui/hud/QuickActionBar.kt").contains("badge = levelBadge(hoeLevel).takeIf { FarmTool.HOE.showsLevel }"))
        val levelUp = source("ui/GardenViewModel.kt").substringAfter("private fun toolLevelUp(").substringBefore("\n    }\n")
        assertTrue(levelUp.indexOf("if (!tool.showsLevel) return") < levelUp.indexOf("audio.play"))
    }

    // --- 1.2 The book ---

    @Test
    fun `the book opens and fills its pages in silently`() {
        val model = source("ui/collection/CollectionViewModel.kt")
        assertFalse(model.contains("audio.play"))
        assertFalse(model.contains("AudioManager"))
        // The day's coins for opening it are still paid.
        assertTrue(model.contains("grantReward.bookOpen(todayEpochDay())"))
    }

    @Test
    fun `the button that fills a page has no sparkle, and a new creature is marked NEW`() {
        assertTrue(strings.contains("<string name=\"collection_reveal_button\">登録する</string>"))
        assertTrue(strings.contains("<string name=\"nature_new\">NEW</string>"))
        assertFalse(strings.contains("あたらしい"))
    }

    // --- 1.3 The title ---

    @Test
    fun `the title's artwork is read before the wasteland and the ads, which wait for it`() {
        val repository = source("data/asset/AssetRepository.kt")
        val prefetch = repository.substringAfter("fun prefetch(scope: CoroutineScope): Job {").substringBefore("\n    }\n")
        assertTrue(prefetch.indexOf("title.joinAll()") < prefetch.indexOf("loadWasteland()"))
        assertTrue(prefetch.indexOf("titleArt.complete()") < prefetch.indexOf("loadWasteland()"))
        // The atlas goes up to the graphics memory as soon as it is decoded.
        assertTrue(repository.contains("bitmap.prepareToDraw()"))
        val app = source("PocketGardenApplication.kt").substringAfter("override fun onCreate()")
        assertTrue(app.indexOf("titleArt.join()") in 0 until app.indexOf("rewardedAds.start()"))
        // And the ads SDK starts off the main thread.
        assertTrue(source("data/ads/RewardedAdManager.kt").contains("Thread({ MobileAds.initialize(context) { handler.post(::load) } }, \"ads-init\").start()"))
    }

    // --- 1.4 Growing the garden ---

    @Test
    fun `growing the garden is asked about first, and goes 9 to 11 to 13`() {
        assertEquals(listOf(11, 13), GardenExpansion.STAGES.map { it.size })
        assertEquals(11, GardenExpansion.nextStage(GridObject.GRID_SIZE)?.size)
        val screen = source("ui/GardenScreen.kt")
        assertFalse(screen.contains("onExpand = viewModel::expand"))
        assertTrue(screen.contains("onExpand = { confirmExpand = true }"))
        val dialog = screen.substringAfter("if (confirmExpand && growTo != null) {").substringBefore("\n    }\n")
        assertTrue(dialog.contains("ExpandConfirmDialog("))
        assertTrue(dialog.contains("viewModel.expand()"))
        assertTrue(strings.contains("<string name=\"expand_confirm_title\">庭を広げますか？</string>"))
        assertTrue(strings.contains("name=\"expand_confirm_text\""))
        val confirm = source("ui/hud/ExpandConfirmDialog.kt")
        assertTrue(confirm.indexOf("R.string.action_cancel") < confirm.indexOf("R.string.expand_confirm_ok"))
    }

    // --- 2.1 Seedlings ---

    private val manifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()) }
    private val atlas: TestPng by lazy { TestPng.read(File(main, "assets/atlas/atlas.png")) }

    /** The share of the opaque pixels in the lower part of [id]'s picture that are as dark as earth. */
    private fun darkShareLow(id: String): Float {
        val sprite = manifest.assets.single { it.assetId == id }
        var dark = 0
        var opaque = 0
        for (y in (sprite.height * 0.55f).toInt() until sprite.height) for (x in 0 until sprite.width) {
            if (atlas.alpha(sprite.atlasX + x, sprite.atlasY + y) <= 128) continue
            opaque++
            if (atlas.luminance(sprite.atlasX + x, sprite.atlasY + y) < 60) dark++
        }
        return dark.toFloat() / opaque
    }

    @Test
    fun `the strawberry seedling stands without its clump of earth`() {
        // It was 0.19 with the clump: the bed it is planted in is drawn by the game.
        assertTrue("${darkShareLow("plant_strawberry_stage1")}", darkShareLow("plant_strawberry_stage1") < 0.08f)
        val soil = File(tools, "crop_soil.py").readText()
        assertTrue(soil.contains("def _strip_clumps("))
        // A crop in its bed has no crumbs of earth drawn round its foot either.
        assertTrue(source("ui/components/GardenCanvas.kt").contains("if (!isGroundCover && structure == null && !obj.isPlant) {"))
    }

    // --- 2.2 Fences ---

    private val lawnAt11 = GridObject("lawn", 1, 1, objectId = LawnGrowth.OBJECT_ID, lastUpdatedTimestamp = 0L)
    private val fenceAt22 = GridObject("fence", 2, 2, objectId = GridObject.FENCE_OBJECT_ID, lastUpdatedTimestamp = 0L)

    @Test
    fun `a fence can stand on a lawn, and a lawn be sown under a fence`() {
        val grid = GardenGrid(GardenMap.allGrass(9), listOf(lawnAt11, fenceAt22))
        val fence = GridObject.FENCE_OBJECT_ID
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 1, 1, PlacementKind.OBJECT, fence))
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 2, 2, PlacementKind.OBJECT, LawnGrowth.OBJECT_ID))
        assertTrue(GardenGrid.sharesCellWithLawn("struct_fence_post"))
    }

    @Test
    fun `nothing else shares a lawn, and a fence still needs a free cell otherwise`() {
        val grid = GardenGrid(GardenMap.allGrass(9), listOf(lawnAt11, fenceAt22))
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 1, 1, PlacementKind.OBJECT, "deco_bench"))
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 1, 1, PlacementKind.OBJECT))
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 2, 2, PlacementKind.OBJECT, GridObject.FENCE_OBJECT_ID))
        // Nor can the hoe break up a lawn.
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 1, 1, PlacementKind.TERRAIN))
    }

    @Test
    fun `where a fence stands on a lawn, the fence is what stands there and the lawn lies under it`() {
        val fenceOnLawn = fenceAt22.copy(instanceId = "f2", gridX = 1, gridY = 1)
        val grid = GardenGrid(GardenMap.allGrass(9), listOf(lawnAt11, fenceOnLawn))
        assertEquals(fenceOnLawn, grid.occupantAt(1, 1))
        assertEquals(fenceOnLawn, grid.objectAt(1, 1))
        assertEquals(lawnAt11, grid.lawnAt(1, 1))
        // A lawn alone is still what stands on its cell.
        assertEquals(lawnAt11, GardenGrid(GardenMap.allGrass(9), listOf(lawnAt11)).occupantAt(1, 1))
        assertNull(grid.lawnAt(2, 2))
    }

    @Test
    fun `placing and moving tell the grid what is being put down`() {
        assertTrue(source("domain/usecase/GardenActionUseCases.kt").contains("gridX, gridY, kind, item.objectId)) {"))
        assertTrue(source("domain/usecase/ConstructionUseCases.kt").contains("PlacementKind.forObjectId(target.objectId), target.objectId)) {"))
    }

    @Test
    fun `a fence standing alone is one panel between two posts, not a run of pales`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("val section = atlas[FencePart.RAIL.objectId] ?: atlas[FencePart.SECTION.objectId]"))
        assertNotNull(manifest.assets.firstOrNull { it.assetId == "struct_fence_rail" })
        // Joined, it is still built round the post.
        assertTrue(canvas.contains("drawFenceArms(transform, obj.gridX, obj.gridY, mask, behind = true, postHeight)"))
    }

    // --- 2.3 The cottage ---

    @Test
    fun `the cottage splits the miss between its sides and its front, like the other buildings`() {
        val cottage = manifest.assets.single { it.assetId == "struct_cottage" }
        assertEquals(0.7481f, cottage.anchorY, 1e-4f)
        val script = File(tools, "prepare_building_art.py").readText()
        assertTrue(script.contains("out[\"struct_cottage\"] = prepare_building(load(\"building_cottage.png\"), tiles=3)"))
    }

    // --- 2.4 Ponds ---

    @Test
    fun `the ring of logs runs on across a join on the pool's edge`() {
        // From the first pond's log end to the second's, closed with the middle of its own log.
        assertEquals(1.26f, PondJoin.GAP_FROM + PondJoin.GAP_SPAN, 1e-4f)
        assertTrue(PondJoin.GAP_FROM - PondJoin.GAP_SHIFT in 0.2f..0.3f)
        assertTrue(PondJoin.GAP_FROM + PondJoin.GAP_SPAN - PondJoin.GAP_SHIFT in 0.7f..0.8f)
        assertTrue(PondJoin.BANK_BEFORE.start < 0f && PondJoin.BANK_AFTER.endInclusive > 1f)
        val bridge = source("ui/components/GardenCanvas.kt").substringAfter("private fun DrawScope.drawPondBridge(").substringBefore("\n}\n")
        assertTrue(bridge.contains("drawPondLogAcross(transform, bridge, pond, PondJoin.BANK_BEFORE)"))
        assertTrue(bridge.contains("drawPondLogAcross(transform, bridge, pond, PondJoin.BANK_AFTER)"))
    }

    // --- 3.1 Beds on the wasteland ---

    @Test
    fun `a bed on the wasteland is laid over the wasteland's own earth, turned over`() {
        val bed = source("ui/components/GardenCanvas.kt").substringAfter("private fun DrawScope.drawTilledSoil(").substringBefore("\n}\n")
        assertTrue(bed.contains("drawFlatGround(earth, transform, bed)"))
        assertTrue(bed.indexOf("drawFlatGround(earth, transform, bed)") < bed.indexOf("SoilPalette.TURNED_OVER"))
        assertTrue(SoilPalette.TURNED_OVER in 0.3f..0.75f)
    }

    // --- 3.2 Lawns that grow ---

    private fun lawn(progress: Float, at: Long = 0L) =
        GridObject("g", 1, 1, objectId = LawnGrowth.OBJECT_ID, growthProgress = progress, lastUpdatedTimestamp = at)

    @Test
    fun `a lawn is sown and comes on with time, to full in its hours`() {
        assertTrue(LawnGrowth.GROWS_IN_STAGES)
        assertEquals(LawnStage.SOWN, LawnGrowth.stageOf(lawn(0f)))
        val hour = 3_600_000L
        val offline = CalculateOfflineProgressUseCase()
        val half = offline(lawn(0f), (LawnGrowth.HOURS_TO_FULL / 2 * hour).toLong())
        assertEquals(0.5f, half.growthProgress, 1e-3f)
        assertEquals(LawnStage.FULL, LawnGrowth.stageOf(offline(lawn(0f), 3 * 24 * hour)))
        // A lawn has nothing else of a plant's: no status changes.
        assertEquals(lawn(0f).status, half.status)
    }

    @Test
    fun `the lawns laid before are fully grown, and the title's garden stays grown over`() {
        val database = File(main, "java/com/studioguu/miniaturegarden/data/local/AppDatabase.kt").readText()
        assertTrue(database.contains("UPDATE grid_objects SET growthProgress = 1.0 WHERE objectId = 'ground_grass'"))
        assertEquals(15, AppDatabase.MIGRATION_14_15.endVersion)
        assertTrue(source("ui/title/TitleSampleGarden.kt").contains("at(x, y, \"ground_grass\").copy(growthProgress = 1f)"))
    }

    @Test
    fun `a grown lawn has tufts standing up all over it`() {
        assertTrue(LawnStage.FULL.blades >= 12)
        LawnStage.entries.forEach { assertTrue(it.blades >= 6) }
    }
}
