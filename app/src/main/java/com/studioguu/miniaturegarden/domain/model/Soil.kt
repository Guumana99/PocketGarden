package com.studioguu.miniaturegarden.domain.model

import kotlin.math.roundToInt

/**
 * How good a cell's soil has become over time (the long cycle; [Soil] has the short one). It only
 * ever helps a crop stay well and bear well: nothing about the soil makes anything grow faster.
 *
 * The food shown is for a plant's first harvest; a crop that bears again takes
 * [Soil.LATER_HARVEST_SHARE] of that at each harvest after it.
 *
 *     grade   name          food a harvest takes   dries out   "good harvest"
 *     PLAIN   ふつうの土    1 serving              as usual    never
 *     GOOD    よい土        1.25 servings          15 % later  1 in 10
 *     FLUFFY  ふかふかの土  1.5 servings           30 % later  1 in 5
 *
 * @property nutritionPerHarvest servings of food one harvest takes out of it.
 * @property dryOutFactor how many times longer its soil takes to dry out.
 * @property bountyChance the chance of a harvest being a good one ([Soil.BOUNTY_MULTIPLIER]).
 */
enum class SoilGrade(val nutritionPerHarvest: Float, val dryOutFactor: Float, val bountyChance: Float) {
    PLAIN(nutritionPerHarvest = 1f, dryOutFactor = 1f, bountyChance = 0f),
    GOOD(nutritionPerHarvest = 1.25f, dryOutFactor = 1.15f, bountyChance = 0.1f),
    FLUFFY(nutritionPerHarvest = 1.5f, dryOutFactor = 1.3f, bountyChance = 0.2f);

    companion object {
        /** The grade stored in [GridObject.soilQuality]; anything out of range is the nearest. */
        fun of(quality: Int): SoilGrade = entries[quality.coerceIn(0, entries.lastIndex)]
    }
}

/**
 * The soil of a cell: a tilled cell, a ridged one or a greenhouse bed ([GridObject.isSoil]).
 *
 * The short cycle is its food, held in the soil's [GridObject.nutritionLevel] as a share of what a
 * full cell holds, [SERVINGS] crops' worth. A harvest takes some, fertilizer fills it, a withered
 * crop pulled up or one that has gone past its season adds a serving. A plant keeps a copy of the level of its cell in its own
 * [GridObject.nutritionLevel], which the panel's bar and its status read; every change here goes
 * to both. With none left a crop is weak: it looks a little tired and bears no good harvests, but
 * it grows as fast as ever, does not wither and can be harvested as usual.
 *
 * The long cycle is its [SoilGrade]: harvests made with food in the soil raise it (changing crops
 * raises it sooner), and being left with nothing growing for long lowers it.
 *
 * Food and grade belong to the soil, not to what grows in it, and a log (the shiitake's) only lies
 * on it ([PlantGround.usesSoil]): it takes nothing and gives nothing.
 */
object Soil {
    /** Hoe strikes from one kind of ground to the next. */
    const val HOE_STRIKES = 2

    /** How many crops' worth of food a full cell holds. */
    const val SERVINGS = 3f

    /** Below this there is no food left: what rounding leaves behind is not food. */
    private const val EMPTY = 1e-4f

    /** What a grade takes, in points: a harvest of the same crop is 24, of a different one 30. */
    const val POINTS_PER_GRADE = 120
    const val SAME_CROP_POINTS = 24
    const val NEW_CROP_POINTS = 30

    /** What a crop's second and later harvests take from the soil and add to its grade. */
    const val LATER_HARVEST_SHARE = 0.5f

    /** Time with nothing growing that takes a grade away. */
    const val IDLE_MILLIS_PER_GRADE = 14L * 24 * 60 * 60 * 1000

    /** The most that one absence counts for: the limit on time away, so one alone costs no grade. */
    const val MAX_IDLE_PER_ABSENCE_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** Idle time is only written down in steps of this: the soil is not saved at every sync. */
    private const val IDLE_STEP_MILLIS = 60L * 60 * 1000

    /** What a good harvest multiplies its coins and experience by. */
    const val BOUNTY_MULTIPLIER = 1.5f

    /** The soil [plant] stands in, if it stands in any. */
    fun under(plant: GridObject, garden: List<GridObject>): GridObject? =
        garden.firstOrNull { it.isSoil && it.gridX == plant.gridX && it.gridY == plant.gridY }

    /** Whether [level] (a [GridObject.nutritionLevel]) has no food left in it. */
    fun isEmpty(level: Float): Boolean = level < EMPTY

    /** How long fertilizer takes to fall onto the soil and soak in, before the food starts to rise. */
    const val SOAK_MILLIS = 2_000L

    /** How long the food takes to rise by one percent, once soaking in: from empty to full in 100 seconds. */
    const val MILLIS_PER_PERCENT = 1_000L

    /**
     * How long a clock may read before the feeding and still count as the same moment: the game's
     * clock and the screen's can be a moment apart. Further back than that, the clock was put back.
     */
    private const val CLOCK_SLACK_MILLIS = 60_000L

    /** How long the food of [obj] takes to fill, after soaking in: one second a percent still to come. */
    fun fillMillis(obj: GridObject): Long =
        ((obj.nutritionLevel - obj.fedFrom).coerceAtLeast(0f) * 100f * MILLIS_PER_PERCENT).toLong()

    /** From the giving of fertilizer to [obj]'s food being full. */
    fun feedingMillis(obj: GridObject): Long = SOAK_MILLIS + fillMillis(obj)

    /**
     * The food of [obj] at [now]: [GridObject.nutritionLevel], or on its way there from
     * [GridObject.fedFrom] while fertilizer soaks in: nothing more for [SOAK_MILLIS], then one
     * percent a second ([MILLIS_PER_PERCENT]) up to it. Everything that reads the food (a crop's
     * status, the panel's bar) reads it here; the time is all that is stored, so the filling goes
     * on while the app is closed and comes out the same however it is looked at, midnight or not.
     * A clock that reads well before the feeding was put back: that never holds the food back.
     */
    fun levelAt(obj: GridObject, now: Long): Float {
        if (obj.fedAt == 0L) return obj.nutritionLevel
        val since = now - obj.fedAt
        if (since < -CLOCK_SLACK_MILLIS) return obj.nutritionLevel
        val risen = (since - SOAK_MILLIS).coerceAtLeast(0L).toFloat() / MILLIS_PER_PERCENT / 100f
        return (obj.fedFrom + risen).coerceAtMost(obj.nutritionLevel)
    }

    /** Whether fertilizer given to [obj] is still falling, soaking in or filling at [now] (or about to). */
    fun isFeeding(obj: GridObject, now: Long): Boolean =
        obj.fedAt != 0L && now - obj.fedAt in -CLOCK_SLACK_MILLIS until feedingMillis(obj)

    /** [obj] fed at [now], from the food it has then up to full. */
    fun fed(obj: GridObject, now: Long): GridObject = obj.copy(nutritionLevel = 1f, fedAt = now, fedFrom = levelAt(obj, now))

    /** [obj] with its food as its soil's ([soil]), filling as that does; full where there is no soil. */
    fun withFoodOf(obj: GridObject, soil: GridObject?): GridObject =
        obj.copy(nutritionLevel = soil?.nutritionLevel ?: 1f, fedAt = soil?.fedAt ?: 0L, fedFrom = soil?.fedFrom ?: 0f)

    /**
     * Whether a second feeding of [soil] at [now] would find it still filling ("補給中"): its
     * fertilizer is soaking in or its food is on its way up, not yet full.
     */
    fun isFilling(soil: GridObject, now: Long): Boolean = isFeeding(soil, now) && levelAt(soil, now) < soil.nutritionLevel

    /**
     * How [soil] stands after a harvest of [cropId], with food in it or not. The harvest takes its
     * share from the food as it is at [now] ([levelAt]); fertilizer still filling the cell goes on
     * filling it, from what is left, at the same pace and up to full (#fb-1107): the food belongs
     * to the cell, not to the crop, and the next crop sown there takes it as it comes.
     *
     * @param later it is not this plant's first harvest: it counts [LATER_HARVEST_SHARE] of one.
     */
    fun afterHarvest(soil: GridObject, cropId: String, now: Long, later: Boolean = false): GridObject {
        val grade = SoilGrade.of(soil.soilQuality)
        val share = if (later) LATER_HARVEST_SHARE else 1f
        val level = levelAt(soil, now)
        val left = (level - grade.nutritionPerHarvest * share / SERVINGS).let { if (isEmpty(it)) 0f else it }
        val filling = isFilling(soil, now)
        val fed = !isEmpty(level)
        var quality = soil.soilQuality
        var points = soil.soilProgress
        if (fed && quality < SoilGrade.entries.lastIndex) {
            val rotated = soil.lastCropId != null && soil.lastCropId != cropId
            points += ((if (rotated) NEW_CROP_POINTS else SAME_CROP_POINTS) * share).toInt()
            if (points >= POINTS_PER_GRADE) {
                quality++
                points = if (quality < SoilGrade.entries.lastIndex) points - POINTS_PER_GRADE else 0
            }
        }
        return soil.copy(
            nutritionLevel = if (filling) soil.nutritionLevel else left,
            soilQuality = quality,
            soilProgress = points,
            lastCropId = cropId,
            idleMillis = 0L,
            lastUpdatedTimestamp = now,
            // Still soaking in: it soaks in as it was. Rising: it rises on from now, from what is left.
            fedAt = if (!filling) 0L else if (now - soil.fedAt < SOAK_MILLIS) soil.fedAt else now - SOAK_MILLIS,
            fedFrom = if (filling) left else 0f,
        )
    }

    /** [soil] with a crop dug into it: a serving more, up to full, now (and on the way up, if it is filling). */
    fun withCompost(soil: GridObject): GridObject {
        val serving = 1f / SERVINGS
        return soil.copy(
            nutritionLevel = (soil.nutritionLevel + serving).coerceAtMost(1f),
            fedFrom = if (soil.fedAt != 0L) (soil.fedFrom + serving).coerceAtMost(1f) else soil.fedFrom,
        )
    }

    /**
     * [soil] brought up to [now]. While something grows in it ([planted]) nothing changes. Left
     * alone, or with a withered plant on it, the time goes towards losing a grade, one absence
     * counting for [MAX_IDLE_PER_ABSENCE_MILLIS] at most. Plain soil has nothing to lose.
     */
    fun afterTime(soil: GridObject, planted: Boolean, now: Long): GridObject {
        if (soil.soilQuality <= 0) return if (soil.idleMillis == 0L) soil else soil.copy(idleMillis = 0L)
        val elapsed = now - soil.lastUpdatedTimestamp
        if (elapsed < IDLE_STEP_MILLIS) return soil
        if (planted) return soil.copy(idleMillis = 0L, lastUpdatedTimestamp = now)
        val idle = soil.idleMillis + elapsed.coerceAtMost(MAX_IDLE_PER_ABSENCE_MILLIS)
        return if (idle >= IDLE_MILLIS_PER_GRADE) {
            soil.copy(
                soilQuality = soil.soilQuality - 1, soilProgress = 0, idleMillis = idle - IDLE_MILLIS_PER_GRADE,
                lastUpdatedTimestamp = now,
            )
        } else {
            soil.copy(idleMillis = idle, lastUpdatedTimestamp = now)
        }
    }

    /** [reward] as a good harvest pays it. */
    fun bounty(reward: HarvestReward): HarvestReward = HarvestReward(
        coins = (reward.coins * BOUNTY_MULTIPLIER).roundToInt(),
        xp = (reward.xp * BOUNTY_MULTIPLIER).roundToInt(),
    )

    /** What fertilizer costs a player at [level]. */
    fun fertilizerCost(level: Int): Int = 10 + 5 * level

    /**
     * What a cell's ground goes back to from [soil] ("1 つ前の地面"): ridges to tilled earth, tilled
     * earth to the ground it was.
     */
    fun groundBelow(soil: GridObject): Ground = if (soil.isTilledSoil) Ground.LOOSE_SOIL else Ground.BARE_EARTH

    /** Whether turning [soil] back takes [plant] with it: it does not grow on the ground below. */
    fun takesPlantBack(soil: GridObject, plant: GridObject?): Boolean =
        plant != null && !PlantSpecies.forObjectId(plant.objectId).ground.allows(groundBelow(soil))
}
