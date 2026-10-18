package com.studioguu.miniaturegarden.domain.model

/**
 * How the seasons go round. Each garden keeps its own clock: a new garden starts in spring on the
 * day it is made, and every [SEASON_MILLIS] the next season comes. The clock runs on real time,
 * the same as the plants, so it also turns while the player is away.
 */
object SeasonClock {
    /** How many days one season lasts. The one number to change to make the year faster or slower. */
    const val DAYS_PER_SEASON = 10

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** How long one season lasts. */
    const val SEASON_MILLIS = DAYS_PER_SEASON * DAY_MILLIS

    /** How long before the end of a season the post brings word of the next one. */
    const val NOTICE_MILLIS = 2 * DAY_MILLIS

    /**
     * Where the year stands at [now], for a garden that is in [season] since [startedAt].
     *
     * Several seasons may have gone by while the player was away; then the result is the season
     * of now, started when it really started. A clock that has gone backwards (the device's
     * time was set back) turns nothing back: the season stays as it is.
     */
    fun at(season: Season, startedAt: Long, now: Long): SeasonState {
        if (now - startedAt < SEASON_MILLIS) return SeasonState(season, startedAt)
        val passed = (now - startedAt) / SEASON_MILLIS
        return SeasonState(season.after(passed.toInt()), startedAt + passed * SEASON_MILLIS)
    }

    /** When the season that started at [startedAt] gives way to the next. */
    fun endOf(startedAt: Long): Long = startedAt + SEASON_MILLIS

    /** Whether, at [now], the end of the season that started at [startedAt] is near enough to announce. */
    fun isEndNear(startedAt: Long, now: Long): Boolean = now >= endOf(startedAt) - NOTICE_MILLIS
}

/** A season and the moment it began. */
data class SeasonState(val season: Season, val startedAt: Long)

/** The season [count] seasons after this one. */
fun Season.after(count: Int = 1): Season = Season.entries[Math.floorMod(ordinal + count, Season.entries.size)]

/** What a letter in the post is about. Its words are written by the screen, from the kind, the season and the topic. */
enum class LetterKind {
    /** The season is about to turn: [Letter.season] is the one coming. */
    SEASON_COMING,

    /** A new season has come: [Letter.season] is the one that came. */
    SEASON_ARRIVED,

    /** A word of advice about the garden: [Letter.topic] names a [LetterTip]. */
    TIP,

    /** New things in the shop after a level-up: [Letter.topic] is `from-to` ([Letter.stockLevels]). */
    NEW_STOCK,

    /** The garden can grow now: [Letter.topic] is the size it can grow to ([Letter.growSize]). */
    GARDEN_CAN_GROW;

    companion object {
        fun fromName(name: String): LetterKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A letter in the garden's post. Letters are kept: the post is an archive the player can read
 * back through, and each letter is read on its own, when its page is opened.
 *
 * @property letterId says what it is about and when (e.g. `arrived:SUMMER:1700000000000`), so the
 *   same news is never posted twice.
 * @property season the season the letter is about, or the one it was sent in.
 * @property topic what in particular, for the kinds that need it (see [LetterKind]); empty otherwise.
 */
data class Letter(
    val letterId: String,
    val kind: LetterKind,
    val season: Season,
    val sentAt: Long,
    val read: Boolean = false,
    val topic: String = "",
) {
    /** For a [LetterKind.TIP]: which piece of advice. Null for other kinds, or one this version does not know. */
    val tip: LetterTip? get() = if (kind == LetterKind.TIP) LetterTip.fromName(topic) else null

    /** For a [LetterKind.NEW_STOCK]: the level before and after. */
    /** For a [LetterKind.GARDEN_CAN_GROW]: the size the garden can grow to. */
    val growSize: Int? get() = if (kind == LetterKind.GARDEN_CAN_GROW) topic.toIntOrNull() else null

    val stockLevels: IntRange? get() = if (kind != LetterKind.NEW_STOCK) {
        null
    } else {
        topic.split('-').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { (from, to) -> from..to }
    }

    companion object {
        fun arrived(season: Season, at: Long) = Letter("arrived:${season.name}:$at", LetterKind.SEASON_ARRIVED, season, at)

        /** About [season], which comes at [comesAt]; posted at [sentAt]. */
        fun coming(season: Season, comesAt: Long, sentAt: Long) =
            Letter("coming:${season.name}:$comesAt", LetterKind.SEASON_COMING, season, sentAt)

        /** A piece of advice, sent in [season]. Each one comes once. */
        fun tip(tip: LetterTip, season: Season, at: Long) =
            Letter("tip:${tip.name}", LetterKind.TIP, season, at, topic = tip.name)

        /** The garden can grow to [size] x [size]. Sent once for each size. */
        fun gardenCanGrow(size: Int, season: Season, at: Long) =
            Letter("grow:$size", LetterKind.GARDEN_CAN_GROW, season, at, topic = size.toString())

        /** What the shop put out when the level went from [fromLevel] to [toLevel]. */
        fun newStock(fromLevel: Int, toLevel: Int, season: Season, at: Long) =
            Letter("stock:$toLevel", LetterKind.NEW_STOCK, season, at, topic = "$fromLevel-$toLevel")
    }
}
