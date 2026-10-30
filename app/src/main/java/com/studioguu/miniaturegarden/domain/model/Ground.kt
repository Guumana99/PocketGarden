package com.studioguu.miniaturegarden.domain.model

/** What a cell's ground is, as far as planting goes ([GardenGrid.groundAt], [PlantGround]). */
enum class Ground {
    /** Open earth: the wasteland, or ground the hoe has begun to break. */
    BARE_EARTH,

    /** Tilled earth: the hoe has been over it once. */
    LOOSE_SOIL,

    /** Ridged soil (畝): the hoe has been over it twice. */
    TILLED_SOIL,

    /** The bed of a glass greenhouse. */
    GREENHOUSE,

    /** Paving: cobblestone, a wooden deck. */
    PAVING,

    /** A lawn, grown or still coming up. */
    LAWN,

    /** Ground cover laid on the earth: pebbles, wild flowers. */
    GROUND_COVER,

    /** The river, or a pond. */
    WATER,
}

/**
 * Where a crop is put down and how it lives with the soil (#68). Each [PlantSpecies] has one;
 * the rules are read from here, so a new kind of crop is a new entry, not a new special case.
 *
 * @property grounds the [Ground]s it can be put on.
 * @property usesSoil whether it lives in the soil ([Soil]): its harvests take the soil's food and
 *   raise its grade, and pulled up it goes to compost.
 * @property leansInWind whether it stands up and bends in the wind; a log lies still.
 */
enum class PlantGround(val grounds: Set<Ground>, val usesSoil: Boolean, val leansInWind: Boolean = true) {
    /** The vegetables: sown on ridges, or in a greenhouse. */
    BED(setOf(Ground.TILLED_SOIL, Ground.GREENHOUSE), usesSoil = true),

    /** The flowers: content with tilled earth as well. */
    FLOWER_BED(setOf(Ground.LOOSE_SOIL, Ground.TILLED_SOIL, Ground.GREENHOUSE), usesSoil = true),

    /**
     * A log laid on the earth (the shiitake): on open earth or on what the hoe made, but not on
     * paving, a lawn, ground cover or water, and not under glass. It lies on the soil rather than
     * growing in it, so the soil's food is neither used nor added to.
     */
    LOG(setOf(Ground.BARE_EARTH, Ground.LOOSE_SOIL, Ground.TILLED_SOIL), usesSoil = false, leansInWind = false);

    fun allows(ground: Ground): Boolean = ground in grounds
}
