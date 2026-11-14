package com.studioguu.miniaturegarden.domain.model

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The book's 60 (#book60): four crops (kabu, satsumaimo, suisen, habotan), moss, the scarecrow and
 * the bird bath, the rabbit (and its white winter coat) and the snail.
 */
class Book60Test {

    private val map = GardenMap.DEFAULT
    private var nextId = 0

    private fun at(x: Int, y: Int, objectId: String, growth: Float = 0f, flipped: Boolean = false) = GridObject(
        instanceId = "b${nextId++}", gridX = x, gridY = y, objectId = objectId, growthProgress = growth, lastUpdatedTimestamp = 0L, isFlipped = flipped,
    )

    private fun garden(objects: List<GridObject>, weather: WeatherType = WeatherType.SUNNY, season: Season = Season.SPRING, time: TimeOfDay = TimeOfDay.DAY) =
        GardenSnapshot(objects, weather, season, time)

    // --- The crops ---

    @Test
    fun `the four crops have the numbers, seasons and grounds asked for`() {
        data class Row(val level: Int, val seed: Int, val hours: Int, val coins: Int, val xp: Int, val season: Season, val flower: Boolean)
        val rows = mapOf(
            GardenItem.SEED_KABU to Row(7, 25, 24, 66, 30, Season.WINTER, false),
            GardenItem.SEED_SATSUMAIMO to Row(9, 40, 30, 100, 42, Season.AUTUMN, false),
            GardenItem.SEED_SUISEN to Row(12, 45, 34, 120, 46, Season.WINTER, true),
            GardenItem.SEED_HABOTAN to Row(16, 80, 44, 200, 72, Season.WINTER, true),
        )
        for ((item, row) in rows) {
            val species = PlantSpecies.forObjectId(item.objectId)
            assertEquals(item.name, row, Row(item.unlockLevel, item.price, species.growthHours, species.harvestCoins, species.harvestXp, item.seasons.single(), species.isFlower))
            assertEquals(3, species.growthStages)
        }
    }

    @Test
    fun `each new crop keeps its soil as long as the crop it is most like`() {
        assertEquals(PlantSpecies.DAIKON.dryOutHours, PlantSpecies.KABU.dryOutHours)
        assertEquals(PlantSpecies.WHEAT.dryOutHours, PlantSpecies.SATSUMAIMO.dryOutHours)
        assertEquals(PlantSpecies.NANOHANA.dryOutHours, PlantSpecies.SUISEN.dryOutHours)
        assertEquals(PlantSpecies.POINSETTIA.dryOutHours, PlantSpecies.HABOTAN.dryOutHours)
    }

    @Test
    fun `the narcissus blooms yellow or white, and an orange cup only now and then on good soil`() {
        assertEquals(listOf("yellow", "white", "orange"), PlantSpecies.SUISEN.bloomColours)
        val random = Random(7)
        val plain = (1..2000).map { BloomColours.pick(PlantSpecies.SUISEN, SoilGrade.PLAIN, 20, Season.WINTER, random) }
        assertEquals(setOf("yellow", "white"), plain.toSet())
        val good = (1..4000).map { BloomColours.pick(PlantSpecies.SUISEN, SoilGrade.GOOD, 20, Season.WINTER, random) }
        val orange = good.count { it == "orange" } / 4000f
        assertTrue("orange $orange", orange in 0.11f..0.19f)
        // Fluffy soil in spring (under glass) has no very rare colour to roll for: it does not fail.
        repeat(500) { BloomColours.pick(PlantSpecies.SUISEN, SoilGrade.FLUFFY, 20, Season.SPRING, random) }
        assertEquals(listOf("plant_suisen_stage3_white", "plant_suisen_stage3_orange"), TileSetDefinition.spriteIdsFor(PlantSpecies.SUISEN).filter { it.contains("stage3_") })
    }

    @Test
    fun `the narcissus is a flower to the butterfly and the dragonfly, the ornamental cabbage is not`() {
        assertTrue(PlantSpecies.SUISEN in CreaturePerches.FLOWERS)
        assertFalse(PlantSpecies.HABOTAN in CreaturePerches.FLOWERS)
        val suisen = at(1, 1, PlantSpecies.SUISEN.objectId, 1f)
        val habotan = at(2, 1, PlantSpecies.HABOTAN.objectId, 1f)
        assertEquals(PerchKind.FLOWER, CreaturePerches.pointsOf(suisen).single().kind)
        assertEquals(PerchKind.LEAF, CreaturePerches.pointsOf(habotan).single().kind)
        // Two narcissi in bloom bring the butterflies; two habotan do not.
        val two = listOf(suisen, at(3, 1, PlantSpecies.SUISEN.objectId, 1f))
        assertTrue(VisitRule.BUTTERFLY_BLOOMS.isMetBy(garden(two)))
        assertFalse(VisitRule.BUTTERFLY_BLOOMS.isMetBy(garden(listOf(habotan, at(3, 1, PlantSpecies.HABOTAN.objectId, 1f)))))
    }

    // --- Moss, the scarecrow and the bird bath ---

    @Test
    fun `moss lies on the ground as the lawn does, and what stands on a lawn stands on it`() {
        val moss = at(4, 4, LawnGrowth.MOSS_OBJECT_ID)
        assertTrue(moss.isGroundCover)
        val grid = GardenGrid(map, listOf(moss))
        assertEquals(Ground.LAWN, grid.groundAt(4, 4))
        for (thing in listOf("deco_rock", "deco_bench", GridObject.SCARECROW_OBJECT_ID, GridObject.BIRDBATH_OBJECT_ID, "struct_fence")) {
            assertEquals(thing, PlacementCheck.OK, grid.checkPlacement(Footprint.forObjectId(thing), 4, 4, objectId = thing))
        }
        // And moss goes under a scarecrow standing on bare ground, as a lawn would.
        val standing = GardenGrid(map, listOf(at(5, 5, GridObject.SCARECROW_OBJECT_ID)))
        assertEquals(PlacementCheck.OK, standing.checkPlacement(Footprint.forObjectId(LawnGrowth.MOSS_OBJECT_ID), 5, 5, objectId = LawnGrowth.MOSS_OBJECT_ID))
        assertEquals(GardenItem.GROUND_MOSS, GardenItem.fromItemId("ground_moss"))
        assertEquals(6, GardenItem.GROUND_MOSS.unlockLevel)
        assertEquals(20, GardenItem.GROUND_MOSS.price)
    }

    @Test
    fun `the scarecrow and the bird bath turn to face the other way, their points with them`() {
        for (id in listOf(GridObject.SCARECROW_OBJECT_ID, GridObject.BIRDBATH_OBJECT_ID)) {
            assertTrue(GridObject.canFaceEitherWay(id))
            val plain = CreaturePerches.pointsOf(at(3, 3, id)).map { it.kind to Math.round((it.x - it.y) * 1000) }
            val turned = CreaturePerches.pointsOf(at(3, 3, id, flipped = true)).map { it.kind to Math.round((it.x - it.y) * 1000) }
            // Across the screen (x - y), every point lands on the other side.
            assertEquals(plain.map { it.first to -it.second }.toSet(), turned.toSet())
        }
        assertEquals(200, GardenItem.DECO_SCARECROW.price)
        assertEquals(8, GardenItem.DECO_SCARECROW.unlockLevel)
        assertEquals(350, GardenItem.DECO_BIRDBATH.price)
        assertEquals(12, GardenItem.DECO_BIRDBATH.unlockLevel)
    }

    @Test
    fun `who settles where on the new things`() {
        fun allowed(c: Creature) = CreaturePerches.ALLOWED.getValue(c)
        assertTrue(allowed(Creature.BIRD).containsAll(setOf(PerchKind.BIRDBATH_RIM, PerchKind.SCARECROW_ARM, PerchKind.SCARECROW_HAT)))
        assertEquals(setOf(PerchKind.SCARECROW_HAT), allowed(Creature.DRAGONFLY).filter { it.name.startsWith("SCARECROW") || it.name.startsWith("BIRDBATH") }.toSet())
        assertTrue(PerchKind.BIRDBATH_FOOT in allowed(Creature.FROG))
        assertFalse(PerchKind.BIRDBATH_RIM in allowed(Creature.FROG))
        assertEquals(setOf(PerchKind.GROUND), allowed(Creature.RABBIT))
        assertEquals(setOf(PerchKind.GROUND), allowed(Creature.SQUIRREL))
        assertEquals(setOf(PerchKind.LEAF, PerchKind.MOSS, PerchKind.ROCK, PerchKind.BIRDBATH_FOOT, PerchKind.WELL_RIM), allowed(Creature.SNAIL))
        // Only the snail goes onto a rock or the moss.
        for (creature in Creature.entries - Creature.SNAIL) {
            assertFalse("$creature", PerchKind.ROCK in allowed(creature) || PerchKind.MOSS in allowed(creature))
        }
    }

    @Test
    fun `the rabbit lopes in among the plants as the squirrel does`() {
        val grid = GardenGrid(map, listOf(at(3, 3, PlantSpecies.KABU.objectId, 1f), at(4, 4, LawnGrowth.MOSS_OBJECT_ID), at(5, 5, "deco_rock")))
        assertTrue(CreatureRoutes.canWalk(Creature.RABBIT, grid, 3, 3))
        assertTrue(CreatureRoutes.canWalk(Creature.RABBIT, grid, 4, 4))
        assertFalse(CreatureRoutes.canWalk(Creature.RABBIT, grid, 5, 5))
        assertFalse(CreatureRoutes.canWalk(Creature.SNAIL, grid, 4, 4))
    }

    // --- The rabbit ---

    private val carrot get() = at(3, 3, PlantSpecies.CARROT.objectId, 1f)
    private fun lawn(n: Int, id: String = LawnGrowth.OBJECT_ID) = (0 until n).map { at(it, 8, id) }

    @Test
    fun `the rabbit comes to a vegetable ready to pull, in the morning or the evening`() {
        val rule = VisitRule.RABBIT_VEG
        for (veg in listOf(PlantSpecies.CARROT, PlantSpecies.DAIKON, PlantSpecies.KABU, PlantSpecies.HAKUSAI)) {
            assertTrue(veg.name, rule.isMetBy(garden(listOf(at(3, 3, veg.objectId, 1f)), time = TimeOfDay.MORNING)))
            assertFalse(veg.name, rule.isMetBy(garden(listOf(at(3, 3, veg.objectId, 0.6f)), time = TimeOfDay.MORNING)))
        }
        assertFalse(rule.isMetBy(garden(listOf(at(3, 3, PlantSpecies.SATSUMAIMO.objectId, 1f)), time = TimeOfDay.MORNING)))
        assertTrue(rule.isMetBy(garden(listOf(carrot), time = TimeOfDay.EVENING)))
        assertFalse(rule.isMetBy(garden(listOf(carrot), time = TimeOfDay.DAY)))
        assertFalse(rule.isMetBy(garden(listOf(carrot), time = TimeOfDay.NIGHT)))
        assertEquals(0.15f, rule.chanceIn(garden(listOf(carrot) + lawn(2), time = TimeOfDay.MORNING)), 1e-6f)
        assertEquals(0.2f, rule.chanceIn(garden(listOf(carrot) + lawn(2) + lawn(1, LawnGrowth.MOSS_OBJECT_ID), time = TimeOfDay.MORNING)), 1e-6f)
        assertEquals(VisitPlacement.BESIDE_ANCHOR, rule.placement)
    }

    @Test
    fun `the rabbit comes to four cells of lawn or moss, not at night, and in every season`() {
        val rule = VisitRule.RABBIT_LAWN
        assertFalse(rule.isMetBy(garden(lawn(3))))
        assertTrue(rule.isMetBy(garden(lawn(2) + lawn(2, LawnGrowth.MOSS_OBJECT_ID).map { it.copy(gridY = 9) })))
        assertFalse(rule.isMetBy(garden(lawn(4), time = TimeOfDay.NIGHT)))
        for (season in Season.entries) for (weather in WeatherType.entries) assertTrue("$season $weather", rule.isMetBy(garden(lawn(4), weather, season)))
        assertEquals(0.1f, rule.chanceIn(garden(lawn(4))), 1e-6f)
        assertEquals(VisitPlacement.ON_ANCHOR, rule.placement)
    }

    @Test
    fun `a rabbit come for a lawn is out on it, hopping about over the ground`() {
        val objects = lawn(6).map { it.copy(gridY = 5) }
        val grid = GardenGrid(map, objects)
        val host = objects[2]
        val visit = CreatureVisit(VisitRule.RABBIT_LAWN, GridCell(host.gridX, host.gridY), host, 0L, 90_000L)
        val route = CreatureRoutes.plan(visit, grid)
        val poses = (500L until 85_000L step 250L).mapNotNull(route::poseAt)
        assertTrue(poses.isNotEmpty())
        assertTrue(poses.all { it.grounded || it.action == CreatureAction.APPEAR || it.action == CreatureAction.VANISH })
        assertTrue(poses.any { it.action == CreatureAction.STEP })
        // It starts on the lawn it came for.
        assertEquals(GridCell(host.gridX, host.gridY), GridCell(poses.first().x.toInt(), poses.first().y.toInt()))
    }

    @Test
    fun `in winter a rabbit is white about a third of the time, never in the other seasons`() {
        val winter = (0L until 4000L).count { CreatureVisits.variantFor(Creature.RABBIT, Season.WINTER, it) == CreatureVisits.WHITE_COAT } / 4000f
        assertTrue("white $winter", winter in 0.31f..0.39f)
        for (season in Season.entries - Season.WINTER) {
            assertTrue((0L until 500L).none { CreatureVisits.variantFor(Creature.RABBIT, season, it) != null })
        }
        assertTrue((0L until 500L).none { CreatureVisits.variantFor(Creature.SQUIRREL, Season.WINTER, it) != null })
    }

    @Test
    fun `the white rabbit is noted on the rabbit's page and counts no kind of its own`() {
        val met = NatureNotes(listOf(NatureNote(NatureNotes.creatureKey(Creature.RABBIT), 0L, 1), NatureNote(NatureNotes.ruleKey(VisitRule.RABBIT_LAWN), 0L, 1)))
        assertFalse(met.isNews(VisitRule.RABBIT_LAWN, null))
        assertTrue(met.isNews(VisitRule.RABBIT_LAWN, CreatureVisits.WHITE_COAT))
        val white = NatureNotes(listOf(NatureNote(NatureNotes.creatureKey(Creature.RABBIT), 0L, 1), NatureNote(NatureNotes.variantKey(Creature.RABBIT, CreatureVisits.WHITE_COAT), 0L, 1)))
        assertTrue(white.hasMet(Creature.RABBIT, CreatureVisits.WHITE_COAT))
        assertEquals(1, white.met)
        assertEquals("creature_rabbit_white_run", TileSetDefinition.coatOf(TileSetDefinition.RABBIT_RUNNING, CreatureVisits.WHITE_COAT))
        assertEquals("creature_squirrel", TileSetDefinition.coatOf("creature_squirrel", CreatureVisits.WHITE_COAT))
        assertEquals(TileSetDefinition.RABBIT_RUNNING, TileSetDefinition.coatOf(TileSetDefinition.RABBIT_RUNNING, null))
    }

    // --- The snail ---

    private fun crops(n: Int) = (0 until n).map { at(1 + it, 2, PlantSpecies.KABU.objectId, 0.5f) }

    @Test
    fun `the snail comes to the crops on a rainy day, more often by the moss`() {
        val rule = VisitRule.SNAIL_RAIN
        assertTrue(rule.isMetBy(garden(crops(3), WeatherType.RAINY)))
        assertFalse(rule.isMetBy(garden(crops(2), WeatherType.RAINY)))
        assertFalse(rule.isMetBy(garden(crops(3), WeatherType.CLOUDY)))
        assertEquals(0.25f, rule.chanceIn(garden(crops(3), WeatherType.RAINY)), 1e-6f)
        assertEquals(0.35f, rule.chanceIn(garden(crops(3) + lawn(2, LawnGrowth.MOSS_OBJECT_ID), WeatherType.RAINY)), 1e-6f)
        // It settles on a crop's leaves, never on a flower in bloom or the shiitake's log.
        assertTrue(rule.anchor.matches(at(1, 1, PlantSpecies.KABU.objectId, 1f)))
        assertFalse(rule.anchor.matches(at(1, 1, PlantSpecies.SUISEN.objectId, 1f)))
        assertFalse(rule.anchor.matches(at(1, 1, PlantSpecies.SHIITAKE.objectId, 1f)))
    }

    @Test
    fun `the snail comes onto three cells of moss on a grey morning or evening`() {
        val rule = VisitRule.SNAIL_MOSS
        val moss = lawn(3, LawnGrowth.MOSS_OBJECT_ID)
        assertTrue(rule.isMetBy(garden(moss, WeatherType.CLOUDY, time = TimeOfDay.MORNING)))
        assertTrue(rule.isMetBy(garden(moss, WeatherType.RAINY, time = TimeOfDay.EVENING)))
        assertFalse(rule.isMetBy(garden(moss, WeatherType.SUNNY, time = TimeOfDay.MORNING)))
        assertFalse(rule.isMetBy(garden(moss, WeatherType.CLOUDY, time = TimeOfDay.DAY)))
        assertFalse(rule.isMetBy(garden(lawn(2, LawnGrowth.MOSS_OBJECT_ID), WeatherType.CLOUDY, time = TimeOfDay.MORNING)))
    }

    @Test
    fun `no snail comes on a snowy day`() {
        val everything = crops(4) + lawn(4, LawnGrowth.MOSS_OBJECT_ID)
        for (season in Season.entries) for (time in TimeOfDay.entries) {
            assertTrue(VisitRule.forCreature(Creature.SNAIL).none { it.isMetBy(garden(everything, WeatherType.SNOWY, season, time)) })
        }
    }

    @Test
    fun `snails come two at a time only in the rain, and alone more often than not`() {
        assertTrue(CreatureCrowds.comesInNumbers(VisitRule.SNAIL_RAIN))
        assertFalse(CreatureCrowds.comesInNumbers(VisitRule.SNAIL_MOSS))
        assertEquals(listOf(0.7f, 0.3f), CreatureCrowds.chances(Creature.SNAIL, 0, 1))
        assertNull(CreatureCrowds.tiersFor(Creature.RABBIT))
        // Over many rainy periods, both happen.
        val objects = crops(6)
        val snails = (0 until 3000).flatMap { CreatureVisits.visitorsAt(it * CreatureVisits.PERIOD_MILLIS + 1, objects, WeatherType.RAINY, map) }
            .filter { it.creature == Creature.SNAIL && it.companion == 0 }
        assertTrue(snails.isNotEmpty())
        assertTrue(snails.any { it.crowd == 1 } && snails.any { it.crowd == 2 })
        assertTrue(snails.count { it.crowd == 1 } > snails.size / 2)
        assertTrue(snails.all { it.crowd <= 2 })
    }

    @Test
    fun `a snail settles on its crop's leaves and creeps very slowly`() {
        val kabu = at(4, 4, PlantSpecies.KABU.objectId, 0.6f)
        val grid = GardenGrid(map, listOf(kabu))
        val visit = CreatureVisit(VisitRule.SNAIL_RAIN, GridCell(4, 4), kabu, 0L, 120_000L)
        val poses = (1_000L until 110_000L step 500L).mapNotNull(CreatureRoutes.plan(visit, grid)::poseAt)
        assertTrue(poses.isNotEmpty())
        assertTrue(poses.all { it.onPlant || it.action == CreatureAction.APPEAR })
        val far = poses.maxOf { kotlin.math.hypot(it.x - poses.first().x, it.y - poses.first().y) }
        assertTrue("strayed $far", far < 0.2f)
    }

    // --- Calling, and the book ---

    @Test
    fun `the debug call sets down a lawn for the rabbit and moss for the snail, and may call the white rabbit`() {
        assertEquals(LawnGrowth.OBJECT_ID, CreatureCalls.perchFor(Creature.RABBIT))
        assertEquals(LawnGrowth.MOSS_OBJECT_ID, CreatureCalls.perchFor(Creature.SNAIL))
        val cell = GridCell(5, 5)
        val lawn = CreatureCalls.thingsFor(Creature.RABBIT, cell, 0L).single()
        assertEquals(1f, lawn.growthProgress)
        val objects = (3..7).map { at(it, 5, LawnGrowth.OBJECT_ID, 1f) }
        val visits = CreatureCalls.visitsAt(10_000L, listOf(Creature.RABBIT), objects, map, whiteRabbit = true)
        assertEquals(CreatureVisits.WHITE_COAT, visits.single().variant)
        assertNull(CreatureCalls.visitsAt(10_000L, listOf(Creature.RABBIT), objects, map).single().variant)
    }

    @Test
    fun `the book holds sixty kinds, the rabbit and the snail after the dragonfly`() {
        assertEquals(60, BookUnlocks.TOTAL_KINDS)
        assertEquals(listOf(Creature.DRAGONFLY, Creature.RABBIT, Creature.SNAIL), Creature.entries.takeLast(3))
        for (id in listOf("plant_kabu", "plant_satsumaimo", "plant_suisen", "plant_habotan", "ground_moss", "deco_scarecrow", "deco_birdbath")) {
            assertTrue(id, CollectionEntry.fromObjectId(id) != null)
        }
    }
}
