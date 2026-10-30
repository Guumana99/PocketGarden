package com.studioguu.miniaturegarden.domain.model

/**
 * A small word of advice, given once, the first time the player meets what it is about. The
 * order here is the order they come in when several are due.
 */
enum class GardenTip {
    /** A crop with no food left in its soil. */
    NO_FOOD,

    /** A crop that has withered. */
    WITHERED,

    /** A page of the book waiting to be filled in. */
    NEW_PAGE,

    /** A creature not met yet, in the garden now. */
    VISITOR;

    companion object {
        /** The first tip the garden calls for that the player has not seen ([seen], by name). */
        fun due(seen: Set<String>, objects: List<GridObject>, pagesReady: Int, unmetVisitor: Boolean): GardenTip? = entries.firstOrNull { tip ->
            tip.name !in seen && when (tip) {
                NO_FOOD -> objects.any { it.isPlant && it.status == PlantStatus.UNNOURISHED }
                WITHERED -> objects.any { it.isPlant && it.status == PlantStatus.WITHERED }
                NEW_PAGE -> pagesReady > 0
                VISITOR -> unmetVisitor
            }
        }
    }
}

/**
 * When the store's own review sheet may be asked for: after a happy moment, by someone who has
 * been playing for a while, and not again for a good while after that. What is on screen at the
 * time is for the garden screen to tell.
 */
object StoreReviewRule {
    const val MIN_PLAY_DAYS = 5
    const val MIN_LEVEL = 4
    const val PAUSE_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun canAsk(settings: AppSettings, level: Int, step: TutorialStep, now: Long): Boolean =
        step == TutorialStep.DONE && settings.playDays >= MIN_PLAY_DAYS && level >= MIN_LEVEL &&
            (settings.reviewAskedAt == 0L || now - settings.reviewAskedAt >= PAUSE_MILLIS)

    /** Whether [objects] have a crop in trouble: no time to ask. */
    fun cropsInTrouble(objects: List<GridObject>): Boolean =
        objects.any { it.isPlant && (it.status == PlantStatus.UNNOURISHED || it.status == PlantStatus.WITHERED) }
}
