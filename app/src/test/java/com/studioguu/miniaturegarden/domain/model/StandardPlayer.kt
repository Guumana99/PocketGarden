package com.studioguu.miniaturegarden.domain.model

import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase

/**
 * The player the numbers are set for: one who looks in three times a day, in the morning, at
 * lunch and in the evening ([VISIT_HOURS]), harvests what is ready and sows again at once. A crop
 * is only taken in at a visit, so one that is ready at 23:00 waits for the morning: that is what
 * makes a long crop worth as much as a quick one to this player.
 */
object StandardPlayer {
    /** The hours of the day the garden is looked at. */
    val VISIT_HOURS = listOf(8, 13, 21)

    private const val DAYS = 360

    /** The seed (or seedling) of [species] in the shop. */
    fun seedOf(species: PlantSpecies): GardenItem = GardenItem.entries.single { it.objectId == species.objectId }

    fun unlockLevel(species: PlantSpecies): Int = seedOf(species).unlockLevel

    /** The seasons [species] can be sown in, all four for a crop of every season. */
    fun seasonsOf(species: PlantSpecies): Set<Season> = seedOf(species).seasons.ifEmpty { Season.entries.toSet() }

    /**
     * How many times a day a bed is harvested, in the long run, for a crop that takes [hours] to
     * ripen: sown at the first visit, taken in at the first visit once it is ready, sown again.
     */
    fun harvestsPerDay(hours: Double): Double {
        val visits = (0 until DAYS + 2).flatMap { day -> VISIT_HOURS.map { day * 24.0 + it } }
        var at = visits.first()
        var harvests = 0
        var next = 0
        while (true) {
            val ready = at + hours
            while (next < visits.size && visits[next] < ready - 1e-9) next++
            if (next >= visits.size || visits[next] > DAYS * 24.0) break
            at = visits[next]
            harvests++
        }
        return harvests.toDouble() / DAYS
    }

    /**
     * What one bed of [species] earns a day, in coins after the seed: for a crop that bears again,
     * by its later harvests (no seed to buy then).
     */
    fun coinsPerDay(species: PlantSpecies): Double =
        if (species.isRepeatable) {
            harvestsPerDay(species.regrowHours.toDouble()) * species.regrowCoins
        } else {
            harvestsPerDay(species.growthHours.toDouble()) * (species.harvestCoins - seedOf(species).price)
        }

    /** The same in experience from the harvests (watering not counted). */
    fun xpPerDay(species: PlantSpecies): Double =
        if (species.isRepeatable) {
            harvestsPerDay(species.regrowHours.toDouble()) * species.regrowXp
        } else {
            harvestsPerDay(species.growthHours.toDouble()) * species.harvestXp
        }

    /** How the season after [season] follows it. */
    private fun next(season: Season): Season = Season.entries[(season.ordinal + 1) % Season.entries.size]

    /**
     * How many days it takes the standard player, from a new garden, to reach [targetLevel] with
     * [beds] beds, all sown from the first morning.
     *
     * Kept simple, and on the generous side: the weather is always fair (so every drop of water is
     * the player's, and paid for in experience), the watering can stays new (35 points a pour,
     * five XP each, poured until the soil is at the watering line), the soil is plain, there is
     * always food in it, and no experience comes from buildings. Each empty bed gets the crop
     * that is open, in season and affordable and earns the most experience a day for this player.
     *
     * @param ads the player also takes the day's ad rewards: the blessed rain once, at the evening
     *   visit, and the five coin bonuses at the morning one.
     */
    fun daysToLevel(targetLevel: Int, beds: Int, ads: Boolean): Double = play(targetLevel, ads, BedPlan.fixed(beds)).days

    /**
     * How many beds the player keeps: [start] on the first morning, then one more each morning
     * ([perDay]) up to as many as the garden's size holds ([bySize]). The garden grows as soon
     * as the level and the coins for its next size are there (all of [GardenExpansion.STAGES],
     * the planned sizes included), keeping [seedMoney] back for the next sowing.
     */
    class BedPlan(val start: Int, val bySize: Map<Int, Int>, val perDay: Int = 1, val seedMoney: Long = 100) {
        companion object {
            fun fixed(beds: Int) = BedPlan(beds, emptyMap(), perDay = 0)

            /** 6 to start with, 20 at 11 x 11, 28 at 13 x 13, 37 at 15 x 15, 48 at 17 x 17. */
            val GROWING = BedPlan(6, mapOf(11 to 20, 13 to 28, 15 to 37, 17 to 48))
        }
    }

    /** How a game went: when the target level came, and on which day the garden grew to each size. */
    class Pace(val days: Double, val grewTo: List<Pair<Int, Double>>, val levels: Map<Int, Double>)

    /** [daysToLevel], with the beds as [plan] has them. */
    fun play(targetLevel: Int, ads: Boolean, plan: BedPlan): Pace {
        class Bed(var crop: PlantSpecies? = null, var growth: Double = 0.0, var moisture: Double = 0.0, var harvested: Int = 0)

        val garden = MutableList(plan.start) { Bed() }
        var size = GridObject.GRID_SIZE
        val grewTo = mutableListOf<Pair<Int, Double>>()
        val levels = sortedMapOf<Int, Double>()
        var profile = UserProfile(coins = UserProfile.STARTING_COINS)
        var lastVisit = VISIT_HOURS.first().toDouble()
        val water = CalculateOfflineProgressUseCase.THIRSTY_THRESHOLD
        val pour = WateringCanSkill.waterAmount(1).toDouble()
        for (day in 0 until 10 * 365) {
            val season = generateSequence(Season.SPRING, ::next).elementAt((day / SeasonClock.DAYS_PER_SEASON) % 4)
            for (hour in VISIT_HOURS) {
                val now = day * 24.0 + hour
                val elapsed = now - lastVisit
                lastVisit = now
                // Time passes: a bed grows while its soil is above the thirsty line.
                for (bed in garden) {
                    val crop = bed.crop ?: continue
                    val drain = elapsed / crop.dryOutHours
                    val wetHours = ((bed.moisture - water) / (1.0 / crop.dryOutHours)).coerceIn(0.0, elapsed)
                    bed.moisture = (bed.moisture - drain).coerceAtLeast(0.0)
                    bed.growth = (bed.growth + wetHours / crop.growthHours).coerceAtMost(1.0)
                }
                if (ads && hour == VISIT_HOURS.first()) {
                    repeat(RewardType.COIN_BONUS.dailyLimit) {
                        profile = profile.copy(coins = profile.coins + RewardType.coinBonusBase(profile.playerLevel) * 105 / 100)
                    }
                }
                if (hour == VISIT_HOURS.first() && plan.bySize.isNotEmpty()) {
                    // The garden grows when it can, and a bed or so is dug each morning while there is room.
                    val next = GardenExpansion.STAGES.firstOrNull { it.size > size }
                    if (next != null && profile.playerLevel >= next.playerLevel && profile.coins >= next.price + plan.seedMoney) {
                        profile = profile.copy(coins = profile.coins - next.price)
                        size = next.size
                        grewTo += size to now / 24.0
                    }
                    val room = (plan.bySize[size] ?: garden.size) - garden.size
                    repeat(minOf(plan.perDay, room.coerceAtLeast(0))) { garden += Bed() }
                }
                for (bed in garden) {
                    val crop = bed.crop
                    // Harvest what is ready.
                    if (crop != null && bed.growth >= 1.0 - 1e-9) {
                        val reward = crop.harvestReward(bed.harvested)
                        profile = profile.copy(coins = profile.coins + reward.coins).gainXp(reward.xp)
                        bed.harvested++
                        if (crop.isRepeatable) bed.growth = crop.regrowFrom.toDouble() else bed.crop = null
                    }
                    // A bush that keeps bearing is pulled up, right after a harvest, for a better crop.
                    val best = bestCrop(profile, season) ?: continue
                    val kept = bed.crop
                    if (kept != null && !(kept.isRepeatable && bed.growth == kept.regrowFrom.toDouble() && xpPerDay(best) > xpPerDay(kept))) continue
                    profile = profile.copy(coins = profile.coins - seedOf(best).price)
                    bed.crop = best
                    bed.growth = 0.0
                    bed.harvested = 0
                }
                // Water every growing bed up to the watering line, five XP a pour.
                for (bed in garden) {
                    if (bed.crop == null) continue
                    while (bed.moisture < CareForPlantUseCase.WATERING_THRESHOLD) {
                        bed.moisture = (bed.moisture + pour).coerceAtMost(1.0)
                        profile = profile.gainXp(5)
                    }
                }
                if (ads && hour == VISIT_HOURS.last()) {
                    for (bed in garden) {
                        if (bed.crop == null) continue
                        bed.growth += (1.0 - bed.growth) * RewardType.BLESSED_RAIN_REDUCTION
                        bed.moisture = 1.0
                    }
                }
                levels.putIfAbsent(profile.playerLevel, now / 24.0)
                if (profile.playerLevel >= targetLevel) return Pace(now / 24.0, grewTo, levels)
            }
        }
        error("Lv.$targetLevel not reached")
    }

    private fun bestCrop(profile: UserProfile, season: Season): PlantSpecies? =
        PlantSpecies.entries
            .filter { seedOf(it).inShop && unlockLevel(it) <= profile.playerLevel && season in seasonsOf(it) }
            .filter { seedOf(it).price <= profile.coins }
            .maxByOrNull(::xpPerDay)
}
