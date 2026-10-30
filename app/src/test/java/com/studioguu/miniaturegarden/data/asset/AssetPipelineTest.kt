package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetPipelineTest {

    /** Same shape as the output of tools/asset_packer.py. */
    private val packerOutput = """
        {
          "version": 2,
          "tileWidth": 128,
          "pages": [
            {"file": "crops_0.png", "width": 64, "height": 164},
            {"file": "world_0.png", "width": 84, "height": 54}
          ],
          "assets": [
            {
              "assetId": "deco_rock",
              "anchorX": 0.5,
              "anchorY": 1.0,
              "width": 80,
              "height": 50,
              "isFlippedAllowed": false,
              "page": 1,
              "atlasX": 2,
              "atlasY": 2
            },
            {
              "assetId": "plant_tulip_stage3",
              "anchorX": 0.5,
              "anchorY": 1.0,
              "width": 60,
              "height": 160,
              "isFlippedAllowed": true,
              "page": 0,
              "atlasX": 2,
              "atlasY": 2
            }
          ]
        }
    """.trimIndent()

    // --- AssetManifest / AtlasManifest ---

    @Test
    fun `parses the packer's manifest`() {
        val manifest = AtlasManifest.parse(packerOutput)

        assertEquals(listOf(AtlasPage("crops_0.png", 64, 164), AtlasPage("world_0.png", 84, 54)), manifest.pages)
        assertEquals(128, manifest.tileWidth)
        assertEquals(1, manifest.assets[0].page)
        assertEquals(
            AssetManifest(
                assetId = "plant_tulip_stage3",
                anchorX = 0.5f,
                anchorY = 1.0f,
                width = 60,
                height = 160,
                isFlippedAllowed = true,
                atlasX = 2,
                atlasY = 2,
            ),
            manifest.assets[1],
        )
        assertFalse(manifest.assets[0].isFlippedAllowed)
    }

    @Test
    fun `omitted fields take the documented defaults and unknown fields are ignored`() {
        val manifest = AtlasManifest.parse(
            """{"pages": [{"file": "a_0.png", "width": 10, "height": 10}], "futureField": 1,
                "assets": [{"assetId": "a", "width": 10, "height": 10, "futureField": true}]}"""
        )
        val asset = manifest.assets.single()
        assertEquals(0.5f, asset.anchorX, 0f)
        assertEquals(0.8f, asset.anchorY, 0f)
        assertTrue(asset.isFlippedAllowed)
        assertEquals(0, asset.page)
        assertEquals(AtlasManifest.DEFAULT_TILE_WIDTH, manifest.tileWidth)
    }

    @Test
    fun `invalid manifests are rejected`() {
        fun manifestWith(asset: String, extra: String = "") =
            """{"pages": [{"file": "a_0.png", "width": 100, "height": 100}, {"file": "a_1.png", "width": 20, "height": 20}], $extra "assets": [$asset]}"""

        val invalid = listOf(
            "not json",
            manifestWith("""{"assetId": "a", "width": 10}"""), // height missing
            manifestWith("""{"assetId": "a", "width": 0, "height": 10}"""),
            manifestWith("""{"assetId": "a", "width": 10, "height": 10, "anchorY": 1.2}"""),
            manifestWith("""{"assetId": "a", "width": 10, "height": 10, "atlasX": 95}"""), // overflows its page
            manifestWith("""{"assetId": "a", "width": 10, "height": 10, "page": 1, "atlasX": 15}"""), // overflows the smaller page
            manifestWith("""{"assetId": "a", "width": 10, "height": 10, "page": 2}"""), // no such page
            manifestWith(
                """{"assetId": "a", "width": 10, "height": 10}, {"assetId": "a", "width": 10, "height": 10}"""
            ),
            manifestWith("""{"assetId": "a", "width": 10, "height": 10}""", extra = """"tileWidth": 0,"""),
        )
        for (text in invalid) {
            assertThrows(text, IllegalArgumentException::class.java) { AtlasManifest.parse(text) }
        }
    }

    @Test
    fun `anchor point lands on the requested screen position`() {
        val asset = AssetManifest("a", anchorX = 0.5f, anchorY = 0.8f, width = 100, height = 200)
        // At 1x: left = 300 - 0.5 * 100, top = 500 - 0.8 * 200
        assertEquals(250f, asset.drawLeft(300f, scale = 1f), 1e-4f)
        assertEquals(340f, asset.drawTop(500f, scale = 1f), 1e-4f)
        // At 0.5x the sprite is 50x100, so the offsets halve.
        assertEquals(275f, asset.drawLeft(300f, scale = 0.5f), 1e-4f)
        assertEquals(420f, asset.drawTop(500f, scale = 0.5f), 1e-4f)
    }

    // --- SpriteAtlas ---

    @Test
    fun `atlas without an image resolves no sprites`() {
        assertNull(SpriteAtlas.EMPTY["plant_tulip_stage3"])
        // A manifest alone is not drawable either.
        assertNull(SpriteAtlas(AtlasManifest.parse(packerOutput), pages = emptyList())["plant_tulip_stage3"])
    }

    // --- TileSetDefinition ---

    private fun gridObject(objectId: String, growth: Float = 0f, status: PlantStatus = PlantStatus.HEALTHY) =
        GridObject(
            instanceId = "id",
            gridX = 0,
            gridY = 0,
            objectId = objectId,
            growthProgress = growth,
            status = status,
            lastUpdatedTimestamp = 0L,
        )

    @Test
    fun `growth progress maps onto stages with the last one reserved for full growth`() {
        val stages = listOf(0f, 0.2f, 0.34f, 0.66f, 0.67f, 0.99f, 1f, 7f, -1f)
            .map { TileSetDefinition.growthStage(it, stages = 4) }
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 1), stages)
        assertEquals(1, TileSetDefinition.growthStage(0.9f, stages = 1))
    }

    @Test
    fun `asset ids follow the object's state`() {
        assertEquals("plant_sunflower_stage1", TileSetDefinition.assetIdFor(gridObject("plant_sunflower")))
        assertEquals("plant_sunflower_stage2", TileSetDefinition.assetIdFor(gridObject("plant_sunflower", growth = 0.7f)))
        assertEquals("plant_sunflower_stage3", TileSetDefinition.assetIdFor(gridObject("plant_sunflower", growth = 1f)))
        assertEquals(
            "plant_sunflower_withered",
            TileSetDefinition.assetIdFor(gridObject("plant_sunflower", growth = 1f, status = PlantStatus.WITHERED)),
        )
        // The tulip (#58): three stages, a colour of its own once in flower, and never withered.
        assertEquals("plant_tulip_stage2", TileSetDefinition.assetIdFor(gridObject("plant_tulip", growth = 0.7f)))
        assertEquals("plant_tulip_stage3", TileSetDefinition.assetIdFor(gridObject("plant_tulip", growth = 1f)))
        val bloom = gridObject("plant_tulip", growth = 1f).copy(bloomColour = "pink")
        assertEquals("plant_tulip_stage3_pink", TileSetDefinition.assetIdFor(bloom))
        assertEquals("plant_tulip_stage3", TileSetDefinition.assetIdFor(bloom.copy(bloomColour = "red")))
        assertEquals("plant_tulip_stage3_pink", TileSetDefinition.assetIdFor(bloom.copy(status = PlantStatus.WITHERED)))
        // Plants without a PlantSpecies entry still resolve, using the default stage count.
        assertEquals("plant_new_stage3", TileSetDefinition.assetIdFor(gridObject("plant_new", growth = 1f)))
        assertEquals("deco_rock", TileSetDefinition.assetIdFor(gridObject("deco_rock", growth = 0.5f)))
    }

    @Test
    fun `tulips bloom in six colours, one picture each`() {
        val sprites = TileSetDefinition.spriteIdsFor(PlantSpecies.TULIP)
        assertEquals(
            listOf("plant_tulip_stage1", "plant_tulip_stage2", "plant_tulip_stage3") +
                listOf("pink", "yellow", "white", "purple", "blue").map { "plant_tulip_stage3_$it" },
            sprites,
        )
    }

    @Test
    fun `definitions have unique ids and match the domain's plant prefix`() {
        val objectIds = TileSetDefinition.objectIds
        assertEquals(objectIds.size, objectIds.toSet().size)
        assertEquals(GroundTile.entries.size, GroundTile.entries.map { it.assetId }.toSet().size)
        assertTrue(PlantSpecies.entries.all { it.objectId.startsWith(GridObject.PLANT_ID_PREFIX) })
    }

    @Test
    fun `the lawn is one shade of grass, no longer a checkerboard`() {
        for (x in 0 until 4) for (y in 0 until 4) {
            assertEquals(GroundTile.GRASS, TileSetDefinition.groundTileFor(TileType.GRASS, x, y))
        }
        assertEquals(GroundTile.WATER, TileSetDefinition.groundTileFor(TileType.RIVER, 0, 0))
        assertEquals(GroundTile.WATER, TileSetDefinition.groundTileFor(TileType.RIVER, 1, 0))
        // Structures draw with a sprite named after their object id.
        assertEquals("struct_hut", TileSetDefinition.assetIdFor(gridObject("struct_hut")))
        assertTrue("struct_well" in TileSetDefinition.objectIds)
    }
}
