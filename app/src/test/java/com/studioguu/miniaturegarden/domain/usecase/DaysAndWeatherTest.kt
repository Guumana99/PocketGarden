package com.studioguu.miniaturegarden.domain.usecase

import androidx.compose.ui.graphics.Color
import com.studioguu.miniaturegarden.data.security.TrustedClock
import com.studioguu.miniaturegarden.domain.model.GameDay
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.SeasonPhase
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.WeatherOutlook
import com.studioguu.miniaturegarden.domain.model.WeatherPlanner
import com.studioguu.miniaturegarden.domain.model.WeatherTable
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.SceneTint
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The garden's days, its times of day, its own weather, and the game's clock. */
class DaysAndWeatherTest {

    private val hour = 3_600_000L
    private val tokyo = ZoneId.of("Asia/Tokyo")

    // --- Days and times of day ---

    @Test
    fun `a day begins at five in the morning, local time`() {
        val day = 20_000L
        val start = GameDay.startOf(day, tokyo)
        assertEquals(day, GameDay.of(start, tokyo))
        assertEquals(day - 1, GameDay.of(start - 1, tokyo))
        // Midnight and two at night still belong to the day before.
        assertEquals(day, GameDay.of(start + 19 * hour, tokyo))
        assertEquals(day, GameDay.of(start + 23 * hour, tokyo))
        assertEquals(day + 1, GameDay.of(start + 24 * hour, tokyo))
    }

    @Test
    fun `the times of day follow the season`() {
        fun times(season: Season, vararg hours: Float) = hours.map { TimeOfDay.at(it, season) }
        val m = TimeOfDay.MORNING
        val d = TimeOfDay.DAY
        val e = TimeOfDay.EVENING
        val n = TimeOfDay.NIGHT
        assertEquals(listOf(n, m, m, d, d, e, e, n, n), times(Season.SPRING, 4.99f, 5f, 10.99f, 11f, 15.99f, 16f, 18.99f, 19f, 0f))
        assertEquals(times(Season.SPRING, 4.99f, 5f, 10.99f, 11f, 15.99f, 16f, 18.99f, 19f, 0f), times(Season.AUTUMN, 4.99f, 5f, 10.99f, 11f, 15.99f, 16f, 18.99f, 19f, 0f))
        assertEquals(listOf(d, e, e, n), times(Season.SUMMER, 16.99f, 17f, 18.99f, 19f))
        assertEquals(listOf(d, e, e, n, n), times(Season.WINTER, 14.99f, 15f, 17.99f, 18f, 4.99f))
        // The debug menu's hours fall inside their own times.
        for (season in Season.entries) for (time in TimeOfDay.entries) assertEquals(time, TimeOfDay.at(time.typicalHour(season), season))
    }

    @Test
    fun `the light moves over from one time of day to the next within an hour`() {
        assertEquals(Color.Transparent.alpha, SceneTint.at(13f, Season.SPRING).alpha, 0f)
        assertEquals(SceneTint.of(TimeOfDay.NIGHT), SceneTint.at(22f, Season.SPRING))
        // Half an hour before nightfall the evening is all there is; half an hour after, the night.
        assertEquals(SceneTint.of(TimeOfDay.EVENING).alpha, SceneTint.at(18.5f, Season.SPRING).alpha, 1e-4f)
        assertEquals(SceneTint.of(TimeOfDay.NIGHT).alpha, SceneTint.at(19.5f, Season.SPRING).alpha, 1e-4f)
        val dusk = SceneTint.at(19f, Season.SPRING).alpha
        assertTrue(dusk > SceneTint.of(TimeOfDay.EVENING).alpha && dusk < SceneTint.of(TimeOfDay.NIGHT).alpha)
        // In winter the night falls at six.
        assertEquals(SceneTint.of(TimeOfDay.NIGHT).alpha, SceneTint.at(18.5f, Season.WINTER).alpha, 1e-4f)
        // The lamps come on with the night.
        assertEquals(0f, SceneTint.lampsAt(15f, Season.SPRING), 0f)
        assertEquals(0.5f, SceneTint.lampsAt(19f, Season.SPRING), 1e-4f)
        assertEquals(1f, SceneTint.lampsAt(2f, Season.SPRING), 0f)
        assertEquals(0.5f, SceneTint.lampsAt(5f, Season.SPRING), 1e-4f)
    }

    // --- The weather's odds ---

    @Test
    fun `the odds of each phase add up, and only winter has snow`() {
        for (season in Season.entries) for (phase in SeasonPhase.entries) {
            val odds = WeatherTable.odds(season, phase)
            assertEquals("$season $phase", 100, odds.values.sum())
            assertEquals("$season $phase", season == Season.WINTER, WeatherType.SNOWY in odds)
        }
        assertEquals(45, WeatherTable.odds(Season.SUMMER, SeasonPhase.EARLY)[WeatherType.RAINY])
        assertEquals(40, WeatherTable.odds(Season.WINTER, SeasonPhase.MID)[WeatherType.SNOWY])
        assertFalse(WeatherType.RAINY in WeatherTable.odds(Season.WINTER, SeasonPhase.MID))
    }

    @Test
    fun `the planner keeps to the odds, and to no more than three days alike`() {
        val seed = 12345L
        val history = ArrayList<WeatherType>()
        val counts = HashMap<WeatherType, Int>()
        val days = 20_000
        for (day in 0L until days) {
            val weather = WeatherPlanner.decide(seed, day, Season.SUMMER, SeasonPhase.MID, history)
            history += weather
            counts.merge(weather, 1, Int::plus)
        }
        assertEquals(0, history.windowed(4).count { it.toSet().size == 1 })
        // Close to 70 / 15 / 15; the rule against long runs takes a little from the sunny days.
        val sunny = counts.getValue(WeatherType.SUNNY) * 100.0 / days
        assertTrue("sunny $sunny%", sunny in 58.0..72.0)
        assertTrue(counts.getValue(WeatherType.RAINY) * 100.0 / days in 12.0..22.0)
    }

    @Test
    fun `the same garden always gets the same day, other gardens other days`() {
        val a = (0L until 60L).map { WeatherPlanner.decide(1L, it, Season.SPRING, SeasonPhase.EARLY, emptyList()) }
        val again = (0L until 60L).map { WeatherPlanner.decide(1L, it, Season.SPRING, SeasonPhase.EARLY, emptyList()) }
        val b = (0L until 60L).map { WeatherPlanner.decide(2L, it, Season.SPRING, SeasonPhase.EARLY, emptyList()) }
        assertEquals(a, again)
        assertNotEquals(a, b)
    }

    // --- The diary ---

    private val repo = FakeGardenRepository()
    private var seeds = 0L
    private val keep = KeepWeatherUseCase(repo, { ++seeds }, tokyo)
    private val today = 20_400L
    private val noon = GameDay.startOf(today, tokyo) + 7 * hour

    @Test
    fun `the diary keeps a week back and tomorrow, and a decided day never changes`() = runBlocking {
        repo.progress = GardenProgress(seasonStartedAt = noon)
        val diary = keep(noon)

        assertEquals(((today - KeepWeatherUseCase.KEEP_DAYS)..(today + 1)).toSet(), repo.weatherDays.keys)
        assertEquals(1L, repo.progress?.weatherSeed)
        // Tomorrow's weather is known today, and is what tomorrow brings.
        val tomorrow = diary.outlook(noon).tomorrow
        assertEquals(tomorrow, keep(noon + 24 * hour).at(noon + 24 * hour))
        // A day once kept stays as it was, whatever the season's clock says afterwards.
        repo.weatherDays[today + 1] = WeatherType.SNOWY
        assertEquals(WeatherType.SNOWY, keep(noon + 24 * hour).at(noon + 24 * hour))
        assertEquals(1L, seeds)
    }

    @Test
    fun `days away are filled in, and old ones let go`() = runBlocking {
        repo.progress = GardenProgress(seasonStartedAt = noon, weatherSeed = 99L)
        keep(noon)
        val later = noon + 20 * 24 * hour
        val diary = keep(later)
        val first = today + 20 - KeepWeatherUseCase.KEEP_DAYS
        assertEquals(((first - WeatherPlanner.MAX_RUN)..(today + 21)).toSet().intersect(repo.weatherDays.keys), repo.weatherDays.keys)
        assertTrue((first..today + 21).all { it in repo.weatherDays })
        // No four days alike anywhere along it.
        val run = (first..today + 21).map { repo.weatherDays.getValue(it) }
        assertEquals(0, run.windowed(4).count { it.toSet().size == 1 })
        assertEquals(repo.weatherDays.getValue(today + 20), diary.at(later))
        assertEquals(0L, seeds)
    }

    @Test
    fun `the diary follows the season's phases, with snow in midwinter`() = runBlocking {
        // Winter began two days ago: its middle days come with snow now and then, never rain.
        repo.progress = GardenProgress(season = Season.WINTER, seasonStartedAt = noon - 2 * 24 * hour, weatherSeed = 7L)
        val diary = keep(noon)
        assertEquals(Season.WINTER to SeasonPhase.EARLY, SeasonClock.on(today, Season.WINTER, noon - 2 * 24 * hour, tokyo))
        // A week back was autumn: no snow then.
        for (day in today - KeepWeatherUseCase.KEEP_DAYS until today - 2) assertNotEquals(WeatherType.SNOWY, repo.weatherDays[day])
        assertTrue(diary.days.isNotEmpty())
    }

    @Test
    fun `the forecast shows once a weathervane stands in the garden`() {
        val vane = GridObject("v", 1, 1, objectId = WeatherOutlook.WEATHERVANE_OBJECT_ID, lastUpdatedTimestamp = 0L)
        assertFalse(WeatherOutlook.isReadable(emptyList()))
        assertTrue(WeatherOutlook.isReadable(listOf(vane)))
    }

    @Test
    fun `the first play stays sunny whatever the diary says`() = runBlocking {
        repo.progress = GardenProgress.NEW_GARDEN
        val diary = keep(noon)
        val forecast = com.studioguu.miniaturegarden.domain.model.WeatherForecast.forGarden(TutorialStep.OPEN_CRATE, diary)
        assertEquals(WeatherType.SUNNY, forecast.at(noon + 24 * hour))
    }

    // --- The game's clock ---

    private val boot = 3

    @Test
    fun `within one boot the game's time runs on the elapsed time alone`() {
        val anchor = TrustedClock.Anchor(time = 1_000_000L, mono = 50_000L, boot = boot)
        // The device's clock says a week later; it is not asked.
        val (now, kept) = TrustedClock.resolve(anchor, TrustedClock.Reading(mono = 80_000L, boot = boot)) { 1_000_000L + 7 * 24 * hour }
        assertEquals(1_030_000L, now)
        assertEquals(anchor, kept)
        // A minute on, the anchor moves along with it, to be written down.
        val (later, moved) = TrustedClock.resolve(anchor, TrustedClock.Reading(mono = 50_000L + 90_000L, boot = boot)) { 0L }
        assertEquals(1_090_000L, later)
        assertEquals(TrustedClock.Anchor(1_090_000L, 140_000L, boot), moved)
    }

    @Test
    fun `after a restart the outside time is taken, but never earlier than before`() {
        val anchor = TrustedClock.Anchor(time = 5_000_000L, mono = 900_000L, boot = boot)
        val ahead = TrustedClock.resolve(anchor, TrustedClock.Reading(mono = 10_000L, boot = boot + 1)) { 9_000_000L }
        assertEquals(9_000_000L, ahead.first)
        assertEquals(TrustedClock.Anchor(9_000_000L, 10_000L, boot + 1), ahead.second)
        // A clock set back: the garden waits where it was rather than going back.
        val behind = TrustedClock.resolve(anchor, TrustedClock.Reading(mono = 10_000L, boot = boot + 1)) { 1_000L }
        assertEquals(5_000_000L, behind.first)
        // The elapsed clock going backwards also means a restart, even with the same boot count.
        assertEquals(9_000_000L, TrustedClock.resolve(anchor, TrustedClock.Reading(mono = 5L, boot = boot)) { 9_000_000L }.first)
        // The very first time, the outside time is all there is.
        assertEquals(42L, TrustedClock.resolve(null, TrustedClock.Reading(1L, boot)) { 42L }.first)
    }

    @Test
    fun `the day turns in the same place for the weather and the seasons`() {
        val utc = ZoneOffset.UTC
        val start = GameDay.startOf(100L, utc) + 3 * hour
        val end = SeasonClock.endOf(start, utc)
        assertEquals(GameDay.startOf(109L, utc), end)
        assertEquals(109L, GameDay.of(end, utc))
    }
}
