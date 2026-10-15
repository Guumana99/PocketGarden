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

/** What a letter in the post is about. Its words are written by the screen, from the kind and the season. */
enum class LetterKind {
    /** The season is about to turn: [Letter.season] is the one coming. */
    SEASON_COMING,

    /** A new season has come: [Letter.season] is the one that came. */
    SEASON_ARRIVED;

    companion object {
        fun fromName(name: String): LetterKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A letter in the garden's post.
 *
 * @property letterId says what it is about and when (e.g. `arrived:SUMMER:1700000000000`), so the
 *   same news is never posted twice.
 */
data class Letter(
    val letterId: String,
    val kind: LetterKind,
    val season: Season,
    val sentAt: Long,
    val read: Boolean = false,
) {
    companion object {
        fun arrived(season: Season, at: Long) = Letter("arrived:${season.name}:$at", LetterKind.SEASON_ARRIVED, season, at)

        /** About [season], which comes at [comesAt]; posted at [sentAt]. */
        fun coming(season: Season, comesAt: Long, sentAt: Long) =
            Letter("coming:${season.name}:$comesAt", LetterKind.SEASON_COMING, season, sentAt)
    }
}
