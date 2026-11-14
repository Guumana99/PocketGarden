package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many kinds a keen player can have in the book at the end of each phase of the first year
 * (device feedback 11/03), and where that puts the things the book opens ([BookUnlocks]).
 *
 * The keen player goes up the levels as [StandardPlayer] does (the garden growing, with the ads:
 * the fastest the model goes), and besides: sows every crop open and in season the moment it can
 * and harvests it, puts down one of everything open, and meets every creature whose combination
 * the garden can then offer (rain, which brings the frog, being taken to come in spring). Every
 * page is filled in and every creature's page looked at at once. Coins are not counted.
 * The figures are written to the test's output, for tuning.
 */
class BookUnlockPaceTest {

    /** The end of a phase, in days from the start of the first spring. */
    private data class Phase(val season: Season, val phase: SeasonPhase) {
        val end: Double get() = season.ordinal * SeasonClock.DAYS_PER_SEASON + (phase.ordinal + 1) * SeasonClock.DAYS_PER_PHASE.toDouble()
    }

    private val phases = Season.entries.flatMap { season -> SeasonPhase.entries.map { Phase(season, it) } }

    private fun seasonOn(day: Double): Season = Season.entries[(day / SeasonClock.DAYS_PER_SEASON).toInt() % 4]

    private fun phaseOn(day: Double): SeasonPhase =
        SeasonPhase.entries[((day % SeasonClock.DAYS_PER_SEASON) / SeasonClock.DAYS_PER_PHASE).toInt().coerceAtMost(2)]

    /** What the keen player has in the book by [end], kind by kind. */
    private class Book(val crops: Int, val terrain: Int, val structures: Int, val creatures: Int, val opened: List<CollectionEntry>) {
        val total: Int get() = crops + terrain + structures + creatures

        /** Without the things the book opens: what opens them. */
        val beforeBook: Int get() = total - opened.size
    }

    private fun bookBy(end: Double, levels: Map<Int, Double>): Book {
        fun levelOpenBy(level: Int): Double = levels[level] ?: Double.MAX_VALUE
        // The first day a crop can be harvested: sown on the first day it is open and in season.
        fun harvestDay(species: PlantSpecies): Double? {
            val seed = StandardPlayer.seedOf(species)
            if (!seed.inShop) return null
            var day = levelOpenBy(seed.unlockLevel)
            while (day <= end) {
                if (seasonOn(day) in StandardPlayer.seasonsOf(species)) return day + species.growthHours / 24.0
                day = Math.floor(day / SeasonClock.DAYS_PER_SEASON + 1) * SeasonClock.DAYS_PER_SEASON
            }
            return null
        }
        val grown = PlantSpecies.entries.filter { (harvestDay(it) ?: Double.MAX_VALUE) <= end }.toSet()
        val byObject = GardenItem.entries.filter { it.inShop }.groupBy { it.objectId }
        // Everything a level opens (or open from the start); the book's own are added below.
        fun placed(): List<CollectionEntry> = CollectionEntry.entries.filter { it.category != CollectionCategory.CROPS }.filter { entry ->
            byObject[entry.objectId].orEmpty().any { it.unlock !is UnlockCondition.CollectionPages && levelOpenBy(it.unlockLevel) <= end }
        }
        fun open(item: GardenItem) = levelOpenBy(item.unlockLevel) <= end
        // When the combination a creature comes for can be offered: CreatureTable's visitor table.
        val water = open(GardenItem.TILE_RIVER)
        val inLateSummerOrAutumn = (0..end.toInt()).any { d ->
            val day = d.toDouble()
            seasonOn(day) == Season.AUTUMN || (seasonOn(day) == Season.SUMMER && phaseOn(day) == SeasonPhase.LATE)
        }
        val met = buildList {
            if (grown.count { it.isFlower } >= 1) add(Creature.BUTTERFLY) // two of a flower in bloom
            if (open(GardenItem.GROUND_GRASS)) add(Creature.LADYBUG) // three crops of any size and a lawn
            if (open(GardenItem.DECO_BENCH) || open(GardenItem.STRUCT_FENCE)) add(Creature.BIRD)
            if ((PlantSpecies.SUNFLOWER in grown && open(GardenItem.DECO_ROCK)) || PlantSpecies.SHIITAKE in grown) add(Creature.SQUIRREL)
            if (water) add(Creature.FROG) // on a rainy day, or any day of summer
            if (water && inLateSummerOrAutumn) add(Creature.DRAGONFLY)
            // #book60: four cells of lawn (any season), and three crops on a rainy day.
            if (open(GardenItem.GROUND_GRASS) || open(GardenItem.GROUND_MOSS)) add(Creature.RABBIT)
            if (grown.isNotEmpty() || open(GardenItem.SEED_TULIP)) add(Creature.SNAIL)
        }
        var things = placed()
        val pagesSoFar = grown.size + things.size + met.size
        // The things the book opens, once it has enough in it.
        val gated = CollectionEntry.entries.filter { e -> byObject[e.objectId].orEmpty().any { it.unlock is UnlockCondition.CollectionPages } }
        val opened = mutableListOf<CollectionEntry>()
        var count = pagesSoFar
        for (entry in gated.sortedBy { e -> byObject[e.objectId]!!.minOf { (it.unlock as? UnlockCondition.CollectionPages)?.pages ?: Int.MAX_VALUE } }) {
            val needs = byObject[entry.objectId]!!.minOf { (it.unlock as? UnlockCondition.CollectionPages)?.pages ?: Int.MAX_VALUE }
            if (count >= needs && entry !in things) {
                things = things + entry
                count++
                opened += entry
            }
        }
        return Book(
            crops = grown.size,
            terrain = things.count { it.category == CollectionCategory.TERRAIN },
            structures = things.count { it.category == CollectionCategory.STRUCTURES },
            creatures = met.size,
            opened = opened,
        )
    }

    private fun table(ads: Boolean): List<Pair<Phase, Book>> {
        val levels = StandardPlayer.play(20, ads, StandardPlayer.BedPlan.GROWING).levels + (1 to 0.0)
        return phases.map { it to bookBy(it.end, levels) }
    }

    @Test
    fun `the first year, phase by phase, for the keen player`() {
        val lines = mutableListOf("ads   phase          day  Lv  before total crops terrain structures creatures  opened")
        for (ads in listOf(false, true)) {
            val levels = StandardPlayer.play(20, ads, StandardPlayer.BedPlan.GROWING).levels + (1 to 0.0)
            for ((phase, book) in table(ads)) {
                val level = levels.filterValues { it <= phase.end }.keys.maxOrNull() ?: 1
                lines += "%-5s %-6s %-5s %5.0f %3d %7d %5d %5d %7d %10d %9d  %s".format(
                    ads, phase.season, phase.phase, phase.end, level, book.beforeBook, book.total, book.crops, book.terrain, book.structures, book.creatures,
                    book.opened.joinToString(",") { it.name },
                )
            }
        }
        println(lines.joinToString("\n"))
    }

    @Test
    fun `the wood deck opens in the middle of the first summer, the lantern in the first autumn`() {
        for (ads in listOf(false, true)) {
            val year = table(ads)
            fun opened(season: Season, phase: SeasonPhase) = year.first { it.first == Phase(season, phase) }.second.opened
            // However keen: nothing in the first spring, not even at its end.
            assertTrue("ads=$ads", opened(Season.SPRING, SeasonPhase.LATE).isEmpty())
            assertTrue("ads=$ads", CollectionEntry.DECK !in opened(Season.SUMMER, SeasonPhase.EARLY))
            assertTrue("ads=$ads", CollectionEntry.DECK in opened(Season.SUMMER, SeasonPhase.MID))
            assertTrue("ads=$ads", CollectionEntry.LANTERN !in opened(Season.SUMMER, SeasonPhase.LATE))
            assertTrue("ads=$ads", CollectionEntry.LANTERN in opened(Season.AUTUMN, SeasonPhase.EARLY))
        }
    }

    @Test
    fun `the book opens them, and its numbers are kept in one place`() {
        assertEquals(UnlockCondition.CollectionPages(BookUnlocks.WOOD_DECK), GardenItem.FLOOR_WOOD.unlock)
        assertEquals(UnlockCondition.CollectionPages(BookUnlocks.LANTERN), GardenItem.DECO_LANTERN.unlock)
        assertEquals(
            listOf(GardenItem.FLOOR_WOOD, GardenItem.DECO_LANTERN),
            GardenItem.entries.filter { it.unlock is UnlockCondition.CollectionPages },
        )
        // The creatures the table above reckons with: a new one has to be added to it.
        assertEquals(8, Creature.entries.size)
        assertEquals(60, CollectionEntry.entries.size + Creature.entries.size)
    }
}
