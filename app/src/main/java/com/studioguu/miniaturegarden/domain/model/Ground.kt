package com.studioguu.miniaturegarden.domain.model

/** What a cell's ground is, as far as planting goes ([GardenGrid.groundAt], [PlantGround]). */
enum class Ground {
    /** Open earth: the wasteland, or ground the hoe has begun to break. */
    BARE_EARTH,

    /** A bed tilled with the hoe, composted or not. */
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
 * @property usesSoil whether it lives in the soil of a bed: it gets the compost's head start when
 *   planted in a composted bed (and uses the compost up), and leaves compost behind when it is
 *   cleared away withered.
 */
enum class PlantGround(val grounds: Set<Ground>, val usesSoil: Boolean) {
    /** Sown in a bed: tilled soil, or a greenhouse. */
    BED(setOf(Ground.TILLED_SOIL, Ground.GREENHOUSE), usesSoil = true),

    /**
     * A log laid on the earth (the shiitake): on open earth or on a bed, but not on paving, a
     * lawn, ground cover or water, and not under glass. It lies on the soil rather than growing
     * in it, so a bed's compost is neither used nor added to.
     */
    LOG(setOf(Ground.BARE_EARTH, Ground.TILLED_SOIL), usesSoil = false);

    fun allows(ground: Ground): Boolean = ground in grounds
}
