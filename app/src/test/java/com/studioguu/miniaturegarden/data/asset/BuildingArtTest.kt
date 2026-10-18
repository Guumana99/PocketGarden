package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.CollectionCategory
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.FakeGardenRepository
import com.studioguu.miniaturegarden.domain.usecase.FlipObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.PlaceItemUseCase
import com.studioguu.miniaturegarden.domain.usecase.RecordCollectionUseCase
import com.studioguu.miniaturegarden.ui.render.WindmillSails
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ground tiles and buildings of #40: their pictures, turning them round, and the windmill's sails. */
class BuildingArtTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val tools: File = listOf(File("../tools"), File("tools")).first { it.isDirectory }
    private val manifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()) }

    private fun sprite(id: String): AssetManifest =
        manifest.assets.firstOrNull { it.assetId == id }.also { assertNotNull("no sprite for $id", it) }!!

    /** A tile's height step in atlas pixels: a quarter of a tile's height, which is half its width. */
    private val step: Float get() = manifest.tileWidth / 8f

    // --- The pictures in the atlas ---

    @Test
    fun `the lawn and the tilled soil are tiles standing on the middle of their top face`() {
        for (id in listOf(GroundTile.GRASS.assetId, GroundTile.GRASS_DARK.assetId, GridObject.TILLED_SOIL_OBJECT_ID)) {
            val tile = sprite(id)
            assertTrue(id, tile.anchorX in 0.45f..0.55f)
            assertTrue(id, tile.anchorY in 0.35f..0.55f)
            // Exactly one tile across, never mirrored (the light would come from the wrong side).
            assertEquals(id, manifest.tileWidth.toFloat(), tile.drawnWidth(1f), manifest.tileWidth * 0.02f)
            assertFalse(id, tile.isFlippedAllowed)
            assertEquals(id, 1f, tile.drawScale, 0f)
        }
    }

    @Test
    fun `each building covers its footprint and stands on the middle of it`() {
        for ((id, tiles) in listOf("struct_cottage" to 3, "floor_greenhouse" to 1, "struct_windmill" to 2)) {
            val building = sprite(id)
            assertTrue(id, building.anchorX in 0.4f..0.6f)
            assertTrue(id, building.anchorY in 0.6f..0.9f)
            assertTrue(id, building.isFlippedAllowed)
            // At least as wide as its footprint, drawn: the base spans it.
            assertTrue(id, building.drawnWidth(1f) >= tiles * manifest.tileWidth * 0.95f)
            assertTrue(id, building.drawnWidth(1f) <= tiles * manifest.tileWidth * 1.6f)
        }
        // Enlarged when drawn rather than in the atlas.
        assertTrue(sprite("struct_cottage").drawScale > 1f)
        assertTrue(sprite("struct_windmill").drawScale > 1f)
    }

    @Test
    fun `the atlas stands each building where the art tool measured its base`() {
        // tools/prepare_building_art.py fits the anchor to the base's ground corners (the pictures
        // are flatter than 2:1). The greenhouse and the windmill split the miss between the sides
        // and the front; the cottage stands its base's side corners on its footprint's, centred
        // on its 3x3 lot without spilling past it. The atlas must carry what it wrote.
        val overrides = File(tools, "atlas_overrides.json").readText()
        for (id in listOf("struct_cottage", "floor_greenhouse", "struct_windmill")) {
            val written = Regex("\"$id\": \\{[^}]*\"anchorY\": ([0-9.]+)").find(overrides)?.groupValues?.get(1)?.toFloat()
            assertNotNull(id, written)
            assertEquals(id, written!!, sprite(id).anchorY, 0f)
        }
    }

    @Test
    fun `a building's picture is about as tall as the height it hides things by`() {
        for (structure in listOf(StructureData.COTTAGE, StructureData.WINDMILL)) {
            val building = sprite(structure.objectId)
            val stepsUp = building.anchorY * building.drawnHeight(1f) / step
            assertTrue("${structure.name}: $stepsUp", stepsUp in structure.heightSteps * 0.8f..structure.heightSteps * 1.1f)
        }
    }

    @Test
    fun `the sails hang on the mill's hub and turn about their own`() {
        val mill = sprite(WindmillSails.MILL_OBJECT_ID)
        val sails = sprite(WindmillSails.SAILS_ASSET_ID)
        assertNotNull(mill.attachX)
        assertTrue(mill.attachX!! in 0.5f..1f) // the hub is on the gable, on the right
        assertTrue(mill.attachY!! in 0.1f..0.4f) // high up, under the roof
        // The sails' pivot is their hub, in the middle of the cross.
        assertTrue(sails.anchorX in 0.45f..0.55f)
        assertTrue(sails.anchorY in 0.45f..0.55f)
        assertEquals(mill.drawScale, sails.drawScale, 0f)
        // About as wide across as the mill is.
        assertTrue(sails.drawnWidth(1f) in mill.drawnWidth(1f) * 0.7f..mill.drawnWidth(1f) * 1.3f)
    }

    @Test
    fun `where the sails turn, as the garden reckons it for what they hide, matches their pictures`() {
        val mill = sprite(WindmillSails.MILL_OBJECT_ID)
        val sails = sprite(WindmillSails.SAILS_ASSET_ID)
        val tile = manifest.tileWidth.toFloat()
        assertEquals((mill.attachX!! - mill.anchorX) * mill.drawnWidth(1f) / tile, WindmillSails.HUB_RIGHT, 0.03f)
        assertEquals((mill.anchorY - mill.attachY!!) * mill.drawnHeight(1f) / tile, WindmillSails.HUB_UP, 0.03f)
        assertEquals(maxOf(sails.drawnWidth(1f), sails.drawnHeight(1f)) / 2f / tile, WindmillSails.REACH, 0.05f)
        // The windmill is as tall as the tip of a sail at the top of its turn (8 steps a tile width).
        val tipSteps = (WindmillSails.HUB_UP + WindmillSails.REACH) * 8f
        assertEquals(tipSteps, StructureData.WINDMILL.heightSteps, StructureData.WINDMILL.heightSteps * 0.05f)
        // And the sails reach past the side of the 2 x 2 footprint, half of which is a tile wide.
        assertTrue(WindmillSails.HUB_RIGHT + WindmillSails.REACH > 1f)
    }

    @Test
    fun `the atlas stays within the texture size every device can take`() {
        assertTrue(manifest.atlasWidth <= 4096)
        assertTrue(manifest.atlasHeight <= 4096)
    }

    @Test
    fun `the pictures came through the art tools, not as loose drawables`() {
        val drawable = File(main, "res/drawable").listFiles()!!.map { it.name }
        assertTrue(drawable.none { it.startsWith("building_") || it.startsWith("tile_") })
        for (name in listOf(
            "building_cottage.png", "building_greenhouse.png", "building_windmill_body.jpg",
            "building_windmill_blades.jpg", "tile_dirt_plowed.png", "tile_grass_default.png",
        )) {
            assertTrue(name, File(tools, "source_art/$name").isFile)
        }
        assertTrue(File(tools, "prepare_building_art.py").isFile)
    }

    // --- The manifest ---

    @Test
    fun `a draw scale and an attachment point are read from the manifest, and are optional`() {
        val parsed = AtlasManifest.parse(
            """{"atlasWidth": 100, "atlasHeight": 100, "tileWidth": 50, "assets": [
                {"assetId": "a", "width": 10, "height": 20, "anchorX": 0.5, "anchorY": 0.5, "drawScale": 3.0, "attachX": 0.9, "attachY": 0.1},
                {"assetId": "b", "width": 10, "height": 20}
            ]}""",
        )
        val a = parsed.assets[0]
        assertEquals(30f, a.drawnWidth(1f), 0f)
        assertEquals(60f, a.drawnHeight(1f), 0f)
        assertEquals(100f - 15f, a.drawLeft(100f, 1f), 0f)
        assertEquals(1f, parsed.assets[1].drawScale, 0f)
        assertNull(parsed.assets[1].attachmentAt(0f, 0f, 1f, flipped = false))
        // The hub, 0.4 of the width to the right of the anchor and 0.4 of the height above it...
        val (x, y) = a.attachmentAt(100f, 200f, 2f, flipped = false)!!
        assertEquals(100f + 0.4f * 60f, x, 1e-3f)
        assertEquals(200f - 0.4f * 120f, y, 1e-3f)
        // ...comes to the left of it when the picture is mirrored.
        assertEquals(100f - 0.4f * 60f, a.attachmentAt(100f, 200f, 2f, flipped = true)!!.first, 1e-3f)
    }

    // --- Turning a building round ---

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 0, playerLevel = 20) }
    private val place = PlaceItemUseCase(repo, GardenMap.allGrass(), RecordCollectionUseCase(repo))
    private val flip = FlipObjectUseCase(repo)

    @Test
    fun `the cottage, the greenhouse and the windmill can face either way, nothing else`() {
        assertEquals(listOf("struct_cottage", "floor_greenhouse", "struct_windmill"), GridObject.FLIPPABLE_OBJECT_IDS)
        for (id in GridObject.FLIPPABLE_OBJECT_IDS) {
            // Turning round must not change the cells: their footprints are square.
            val footprint = StructureData.fromObjectId(id)?.footprint
            if (footprint != null) assertEquals(id, footprint.width, footprint.height)
        }
        assertFalse(GridObject.canFaceEitherWay(StructureData.HUT.objectId))
        assertFalse(GridObject.canFaceEitherWay("plant_tulip"))
        assertFalse(GridObject.canFaceEitherWay(GridObject.TILLED_SOIL_OBJECT_ID))
    }

    @Test
    fun `a building can be put down facing the other way`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WINDMILL.itemId, 1))
        repo.putSlot(InventorySlot(1, GardenItem.DECO_ROCK.itemId, 1))

        assertEquals(ActionResult.SUCCESS, place(0, 0, 0, 0L, flipped = true))
        // A thing that has only one way to face ignores the choice.
        assertEquals(ActionResult.SUCCESS, place(1, 5, 5, 0L, flipped = true))

        val mill = repo.objects.values.single { it.objectId == StructureData.WINDMILL.objectId }
        assertTrue(mill.isFlipped)
        assertFalse(repo.objects.values.single { it.objectId == "deco_rock" }.isFlipped)
    }

    @Test
    fun `a building turns round where it stands, and turns back`() = runBlocking {
        repo.put(GridObject("c", 2, 2, objectId = StructureData.COTTAGE.objectId, lastUpdatedTimestamp = 0L))

        assertEquals(ActionResult.SUCCESS, flip("c"))
        val turned = repo.objects.getValue("c")
        assertTrue(turned.isFlipped)
        assertEquals(2, turned.gridX)
        assertEquals(2, turned.gridY)
        assertEquals(ActionResult.SUCCESS, flip("c"))
        assertFalse(repo.objects.getValue("c").isFlipped)
    }

    @Test
    fun `what cannot turn round is left as it is`() = runBlocking {
        repo.put(GridObject("h", 0, 0, objectId = StructureData.HUT.objectId, lastUpdatedTimestamp = 0L))
        repo.put(GridObject("p", 4, 4, objectId = "plant_tulip", lastUpdatedTimestamp = 0L))

        assertEquals(ActionResult.NOT_FLIPPABLE, flip("h"))
        assertEquals(ActionResult.NOT_FLIPPABLE, flip("p"))
        assertEquals(ActionResult.NOT_FOUND, flip("nothing"))
        assertFalse(repo.objects.getValue("h").isFlipped)
    }

    @Test
    fun `the way a building faces is kept in the database`() {
        val mill = GridObject("m", 1, 1, isFlipped = true, objectId = StructureData.WINDMILL.objectId, lastUpdatedTimestamp = 0L)
        assertTrue(mill.toEntity().isFlipped)
        assertEquals(mill, mill.toEntity().toDomain())
    }

    @Test
    fun `the garden draws a building the way it faces, sails included`() {
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertTrue(canvas.contains("drawSwayingSprite(atlasImage, sprite, base, spriteScale, lean, flipped = obj.isFlipped)"))
        assertTrue(canvas.contains("sprite.attachmentAt(base.x, base.y, spriteScale, obj.isFlipped)"))
        assertTrue(canvas.contains("drawSprite(atlasImage, house, center, spriteScale, flipped = obj.isFlipped"))
        val screen = File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()
        assertTrue(screen.contains("onFlip = viewModel::flipSelected"))
        assertTrue(screen.contains("onFlip = viewModel::togglePlacingFlip"))
    }

    // --- The windmill's sails ---

    @Test
    fun `the sails go round steadily, once in a while`() {
        assertEquals(0f, WindmillSails.angleAt(0L), 0f)
        assertEquals(90f, WindmillSails.angleAt(WindmillSails.TURN_MILLIS / 4), 1e-3f)
        assertEquals(180f, WindmillSails.angleAt(WindmillSails.TURN_MILLIS / 2), 1e-3f)
        // Round and round: always within one turn.
        for (t in listOf(WindmillSails.TURN_MILLIS, 7 * WindmillSails.TURN_MILLIS + 123, 10_000_000L)) {
            assertTrue(WindmillSails.angleAt(t) in 0f..360f)
        }
        assertEquals(0f, WindmillSails.angleAt(WindmillSails.TURN_MILLIS), 1e-3f)
    }

    @Test
    fun `the sails stand still without a clock, and go faster in wind and rain`() {
        assertEquals(0f, WindmillSails.angleAt(null), 0f)
        val t = WindmillSails.TURN_MILLIS / 10
        assertTrue(WindmillSails.angleAt(t, WeatherType.RAINY) > WindmillSails.angleAt(t, WeatherType.CLOUDY))
        assertTrue(WindmillSails.angleAt(t, WeatherType.CLOUDY) > WindmillSails.angleAt(t, WeatherType.SUNNY))
    }

    @Test
    fun `two mills side by side do not turn as one`() {
        assertTrue(WindmillSails.angleAt(1_000L, gridX = 0, gridY = 0) != WindmillSails.angleAt(1_000L, gridX = 3, gridY = 0))
        assertEquals(WindmillSails.angleAt(1_000L, gridX = 3, gridY = 0), WindmillSails.angleAt(1_000L, gridX = 3, gridY = 0), 0f)
    }

    // --- The windmill as a building ---

    @Test
    fun `the windmill is a 2x2 building on sale from level 4, with its own page in the book`() {
        assertEquals(2, StructureData.WINDMILL.footprint.width)
        assertEquals(2, StructureData.WINDMILL.footprint.height)
        assertFalse(StructureData.WINDMILL.isSymbol)
        assertEquals(UnlockCondition.PlayerLevel(4), GardenItem.STRUCT_WINDMILL.unlock)
        assertTrue(GardenItem.STRUCT_WINDMILL.buildSeconds > 0)
        val page = CollectionEntry.entries.single { it.objectId == StructureData.WINDMILL.objectId }
        assertEquals(CollectionCategory.STRUCTURES, page.category)
        val strings = File(main, "res/values/strings.xml").readText()
        for (name in listOf("object_struct_windmill", "flavor_windmill", "action_flip", "placing_flip", "msg_not_flippable")) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
    }
}
