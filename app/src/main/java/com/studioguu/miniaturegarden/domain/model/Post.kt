package com.studioguu.miniaturegarden.domain.model

/**
 * The advice that comes by post, one letter at a time, in this order. Each is sent once. Only
 * what the game really does is told here: the words are in `strings.xml` (`letter_tip_*`).
 *
 * New tips go at the end, so that a garden which has had some carries on with the next.
 */
enum class LetterTip(
    /** Sent once the player reaches this level, rather than in turn with the others; null for those. */
    val atLevel: Int? = null,
) {
    /** A thirsty crop wants water. */
    WATERING,

    /** Rain waters the whole garden. */
    RAIN,

    /** Clearing a withered crop leaves composted soil. */
    COMPOST,

    /** Anything placed can be put back in the bag and placed again. */
    STORE,

    /** Creatures come for what is in the garden and the season; the book keeps them. */
    CREATURES,

    /** The glass greenhouse grows crops out of season, even in winter. */
    GREENHOUSE,

    /** When to give fertilizer and what it does (#fb-1110): comes at Lv.3, not in turn. */
    FERTILIZER(atLevel = 3);

    companion object {
        /** The tips that come one after another, an interval apart. */
        val inTurn: List<LetterTip> get() = entries.filter { it.atLevel == null }

        fun fromName(name: String): LetterTip? = entries.firstOrNull { it.name == name }
    }
}

/** When the post brings what, worked out from the letters already in it (nothing else is stored). */
object PostOffice {
    /** The time between two letters of advice. The first comes this long after the garden's first letter. */
    const val TIP_INTERVAL_MILLIS = 2L * 24 * 60 * 60 * 1000

    /**
     * The letter of advice due at [now], or null when none is. One comes [TIP_INTERVAL_MILLIS]
     * after the last one (or after the first letter of all, when there has been none), so a
     * long time away brings one letter, not a pile; the next follows after another interval.
     *
     * @param letters every letter in the post.
     * @param season the season now, written on the letter.
     */
    fun nextTip(letters: List<Letter>, season: Season, now: Long): Letter? {
        if (letters.isEmpty()) return null
        val sent = letters.filter { it.kind == LetterKind.TIP }
        val tip = LetterTip.inTurn.firstOrNull { tip -> sent.none { it.topic == tip.name } } ?: return null
        val since = sent.maxOfOrNull { it.sentAt } ?: letters.minOf { it.sentAt }
        return if (now - since >= TIP_INTERVAL_MILLIS) Letter.tip(tip, season, now) else null
    }

    /** The advice due at [playerLevel] ([LetterTip.atLevel]) that has not been sent yet, or null. */
    fun levelTip(playerLevel: Int, letters: List<Letter>, season: Season, now: Long): Letter? {
        val tip = LetterTip.entries.firstOrNull { tip ->
            val level = tip.atLevel ?: return@firstOrNull false
            playerLevel >= level && letters.none { it.kind == LetterKind.TIP && it.topic == tip.name }
        } ?: return null
        return Letter.tip(tip, season, now)
    }

    /**
     * The letter saying the garden can grow, when the player's level is enough for the next size
     * and it has not been told so for that size before ([GardenExpansion.levelReached]); null
     * otherwise. The coins are not asked about: the workshop shows the price.
     */
    fun gardenCanGrow(
        gardenSize: Int,
        playerLevel: Int,
        letters: List<Letter>,
        season: Season,
        now: Long,
    ): Letter? {
        if (!GardenExpansion.levelReached(gardenSize, playerLevel)) return null
        val size = GardenExpansion.nextStage(gardenSize)?.size ?: return null
        val letter = Letter.gardenCanGrow(size, season, now)
        return letter.takeIf { letters.none { it.letterId == letter.letterId } }
    }

    /**
     * The letter about what a level-up from [fromLevel] to [toLevel] put in the shop, or null
     * when it put nothing there.
     */
    fun newStock(fromLevel: Int, toLevel: Int, season: Season, now: Long): Letter? =
        if (toLevel > fromLevel && GardenItem.unlockedBetween(fromLevel, toLevel).isNotEmpty()) {
            Letter.newStock(fromLevel, toLevel, season, now)
        } else {
            null
        }
}
