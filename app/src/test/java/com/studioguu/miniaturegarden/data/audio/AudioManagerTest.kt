package com.studioguu.miniaturegarden.data.audio

import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.model.Season
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioManagerTest {

    private val engine = FakeAudioEngine()

    /** Files that "exist" in res/raw for a test, by resource name. */
    private val rawFiles = HashMap<String, Int>()
    private val lookups = ArrayList<String>()

    private val audio = AudioManager(
        resolveRawId = { name -> lookups += name; rawFiles[name] ?: 0 },
        engine = engine,
    )

    // --- No sound files at all (the state the project ships in) ---

    @Test
    fun `everything is a silent no-op when no sound file exists`() {
        audio.preload()
        SoundEffect.entries.forEach(audio::play)
        audio.playBgm(BgmTrack.SPRING)
        audio.pauseBgm()
        audio.resumeBgm()
        audio.bgmVolume = 0.3f
        audio.stopBgm()
        audio.release()

        assertTrue(engine.loaded.isEmpty())
        assertTrue(engine.played.isEmpty())
        assertTrue(engine.bgmStarts.isEmpty())
    }

    @Test
    fun `a missing effect is looked up only once`() {
        repeat(5) { audio.play(SoundEffect.TAP) }
        assertEquals(1, lookups.count { it == "se_tap" })
    }

    @Test
    fun `resource names are unique and follow the se_ and bgm_ prefixes kept by the shrinker`() {
        val names = SoundEffect.entries.map { it.resourceName } + BgmTrack.entries.map { it.resourceName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(SoundEffect.entries.all { it.resourceName.startsWith("se_") })
        assertTrue(BgmTrack.entries.all { it.resourceName.startsWith("bgm_") })
    }

    // --- Sound effects ---

    @Test
    fun `an effect with a file is loaded once and played every time`() {
        rawFiles["se_water"] = 11

        audio.play(SoundEffect.WATER)
        audio.play(SoundEffect.WATER)
        audio.play(SoundEffect.HARVEST) // no file

        assertEquals(listOf(11), engine.loaded)
        val volume = AudioManager.DEFAULT_SOUND_EFFECT_VOLUME // WATER has no trim of its own
        assertEquals(listOf(engine.handleFor(11) to volume, engine.handleFor(11) to volume), engine.played)
        assertTrue(engine.ducks.isEmpty()) // no music playing, nothing to duck
    }

    @Test
    fun `preload loads exactly the effects that have files`() {
        rawFiles["se_tap"] = 1
        rawFiles["se_coin"] = 2

        audio.preload()
        audio.preload()

        assertEquals(setOf(1, 2), engine.loaded.toSet())
        assertEquals(2, engine.loaded.size)
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun `an effect that fails to load stays silent`() {
        rawFiles["se_tap"] = 1
        engine.failingLoads += 1

        audio.play(SoundEffect.TAP)
        audio.play(SoundEffect.TAP)

        assertEquals(listOf(1), engine.loaded) // tried once, not again
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun `disabled effects are not played and volume is clamped`() {
        rawFiles["se_tap"] = 1

        audio.soundEffectsEnabled = false
        audio.play(SoundEffect.TAP)
        assertTrue(engine.played.isEmpty())

        audio.soundEffectsEnabled = true
        audio.soundEffectVolume = 7f
        audio.play(SoundEffect.TAP)
        audio.soundEffectVolume = -1f
        audio.play(SoundEffect.TAP)
        assertEquals(listOf(SoundEffect.TAP.gain, 0f), engine.played.map { it.second })
    }

    // --- Volume balance ---

    @Test
    fun `each effect plays at the master volume times its own gain`() {
        rawFiles["se_harvest"] = 1
        rawFiles["se_coin"] = 2

        audio.soundEffectVolume = 1f
        audio.play(SoundEffect.HARVEST)
        audio.play(SoundEffect.COIN)
        audio.soundEffectVolume = 0.5f
        audio.play(SoundEffect.HARVEST)

        assertEquals(
            listOf(SoundEffect.HARVEST.gain, SoundEffect.COIN.gain, 0.5f * SoundEffect.HARVEST.gain),
            engine.played.map { it.second },
        )
    }

    @Test
    fun `every effect plays between 80 and 100 percent, the quiet files at full volume`() {
        assertTrue(SoundEffect.entries.all { it.gain in 0.8f..1f })
        assertEquals(1f, SoundEffect.COIN.gain, 0f)
        // The hottest file is trimmed the most.
        assertEquals(SoundEffect.entries.minOf { it.gain }, SoundEffect.HARVEST.gain, 0f)
    }

    @Test
    fun `default music volume sits well below the effects`() {
        assertEquals(0.9f, audio.soundEffectVolume, 0f)
        assertEquals(0.35f, audio.bgmVolume, 0f)
        assertTrue(audio.bgmVolume < audio.soundEffectVolume * SoundEffect.entries.minOf { it.gain })
    }

    // --- Settings ---

    @Test
    fun `a new manager already matches a new player's settings`() {
        val defaults = AppSettings()
        assertEquals(defaults.bgmVolume, audio.bgmVolume, 0f)
        assertEquals(defaults.soundEffectVolume, audio.soundEffectVolume, 0f)
        assertEquals(defaults.bgmEnabled, audio.bgmEnabled)
        assertEquals(defaults.soundEffectsEnabled, audio.soundEffectsEnabled)
    }

    @Test
    fun `applying settings sets volumes and mute switches`() {
        rawFiles["se_coin"] = 2
        audio.apply(AppSettings(bgmVolume = 0.2f, soundEffectVolume = 0.5f, soundEffectsEnabled = true))
        audio.play(SoundEffect.COIN)
        assertEquals(0.2f, audio.bgmVolume, 0f)
        assertEquals(listOf(0.5f), engine.played.map { it.second })

        audio.apply(AppSettings(soundEffectsEnabled = false))
        audio.play(SoundEffect.COIN)
        assertEquals(1, engine.played.size)
    }

    @Test
    fun `a changed music volume reaches the player at once`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)

        audio.apply(AppSettings(bgmVolume = 0.8f))

        assertEquals(listOf(0.8f), engine.bgmVolumes)
        assertEquals(1, engine.bgmStarts.size) // adjusted in place, not restarted
    }


    @Test
    fun `muting through settings pauses the music and unmuting continues it at the saved volume`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)

        audio.apply(AppSettings(bgmEnabled = false, bgmVolume = 0.6f))
        assertEquals(listOf("pause"), engine.bgmCalls)

        audio.apply(AppSettings(bgmEnabled = true, bgmVolume = 0.6f))
        assertEquals(listOf("pause", "resume"), engine.bgmCalls)
        assertEquals(listOf(0.6f), engine.bgmVolumes)
        assertEquals(1, engine.bgmStarts.size)
    }

    @Test
    fun `settings applied before any music decide how it starts`() {
        rawFiles["bgm_spring"] = 50

        audio.apply(AppSettings(bgmEnabled = false))
        audio.playBgm(BgmTrack.SPRING)
        assertTrue(engine.bgmStarts.isEmpty())

        audio.apply(AppSettings(bgmEnabled = true, bgmVolume = 0.1f))
        assertEquals(listOf(50 to 0.1f), engine.bgmStarts)
    }

    @Test
    fun `re-applying the same settings does nothing`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)
        val settings = AppSettings(bgmVolume = 0.5f)

        audio.apply(settings)
        audio.apply(settings)
        audio.apply(settings)

        assertEquals(listOf(0.5f), engine.bgmVolumes)
        assertTrue(engine.bgmCalls.isEmpty())
        assertEquals(1, engine.bgmStarts.size)
    }

    @Test
    fun `out-of-range settings are clamped on the way in`() {
        audio.apply(AppSettings(bgmVolume = 3f, soundEffectVolume = -2f))
        assertEquals(1f, audio.bgmVolume, 0f)
        assertEquals(0f, audio.soundEffectVolume, 0f)
    }

    @Test
    fun `an effect ducks the music for its own length`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["se_coin"] = 2
        engine.durations[2] = 1_500L
        audio.playBgm(BgmTrack.SPRING)

        audio.play(SoundEffect.COIN)

        val volume = AudioManager.DEFAULT_BGM_VOLUME
        assertEquals(listOf(Duck(volume * AudioManager.BGM_DUCK_FACTOR, volume, 1_500L)), engine.ducks)
    }

    @Test
    fun `a long effect ducks the music only for the capped time`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["se_level_up"] = 3
        engine.durations[3] = 4_130L
        audio.playBgm(BgmTrack.SPRING)

        audio.play(SoundEffect.LEVEL_UP)

        assertEquals(AudioManager.MAX_DUCK_MILLIS, engine.ducks.single().holdMillis)
    }

    @Test
    fun `ducking follows the current music volume and is skipped while music is not sounding`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["se_tap"] = 1
        audio.playBgm(BgmTrack.SPRING)

        audio.bgmVolume = 0.8f
        audio.play(SoundEffect.TAP)
        assertEquals(0.4f, engine.ducks.single().duckedVolume, 1e-6f)
        assertEquals(0.8f, engine.ducks.single().restoreVolume, 1e-6f)

        audio.pauseBgm()
        audio.play(SoundEffect.TAP)
        audio.resumeBgm()
        audio.bgmEnabled = false
        audio.play(SoundEffect.TAP)
        assertEquals(1, engine.ducks.size)
        assertEquals(3, engine.played.size) // the effects themselves still played
    }

    // --- Background music ---

    @Test
    fun `bgm starts once and repeated requests do not restart it`() {
        rawFiles["bgm_spring"] = 50

        audio.playBgm(BgmTrack.SPRING)
        audio.playBgm(BgmTrack.SPRING)

        assertEquals(listOf(50 to AudioManager.DEFAULT_BGM_VOLUME), engine.bgmStarts)
    }

    @Test
    fun `pause and resume continue the same playback`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)

        audio.pauseBgm()
        audio.resumeBgm()

        assertEquals(1, engine.bgmStarts.size)
        assertEquals(listOf("pause", "resume"), engine.bgmCalls)
    }

    @Test
    fun `bgm requested while paused waits for resume`() {
        rawFiles["bgm_spring"] = 50

        audio.pauseBgm()
        audio.playBgm(BgmTrack.SPRING)
        assertTrue(engine.bgmStarts.isEmpty())

        audio.resumeBgm()
        assertEquals(1, engine.bgmStarts.size)
    }


    @Test
    fun `muting pauses the music and unmuting continues it from where it was`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)

        audio.bgmEnabled = false
        assertEquals(listOf("pause"), engine.bgmCalls)
        audio.playBgm(BgmTrack.SPRING) // asking again while muted changes nothing

        audio.bgmEnabled = true
        assertEquals(listOf("pause", "resume"), engine.bgmCalls)
        assertEquals(1, engine.bgmStarts.size) // never started over
    }

    @Test
    fun `music muted in the background stays silent on return and continues when unmuted`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)
        audio.bgmEnabled = false

        audio.pauseBgm()  // app to the background
        audio.resumeBgm() // and back
        assertFalse("resume" in engine.bgmCalls)

        audio.bgmEnabled = true
        assertEquals("resume", engine.bgmCalls.last())
        assertEquals(1, engine.bgmStarts.size)
    }

    @Test
    fun `stopped bgm does not come back on resume`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)

        audio.stopBgm()
        audio.pauseBgm()
        audio.resumeBgm()

        assertEquals(1, engine.bgmStarts.size)
    }

    @Test
    fun `bgm that cannot be decoded is retried on the next request instead of crashing`() {
        rawFiles["bgm_spring"] = 50
        engine.bgmStartSucceeds = false
        audio.playBgm(BgmTrack.SPRING)

        engine.bgmStartSucceeds = true
        audio.resumeBgm()

        assertEquals(2, engine.bgmStarts.size)
    }

    @Test
    fun `bgm volume is clamped and forwarded`() {
        audio.bgmVolume = 2f
        audio.bgmVolume = 0.25f
        assertEquals(listOf(1f, 0.25f), engine.bgmVolumes)
    }

    // --- Five tunes: the title and the four seasons ---

    @Test
    fun `there is a tune for the title and one for each season`() {
        assertEquals(
            listOf("bgm_title", "bgm_spring", "bgm_summer", "bgm_autumn", "bgm_winter"),
            BgmTrack.entries.map { it.resourceName },
        )
        assertEquals(
            listOf(BgmTrack.SPRING, BgmTrack.SUMMER, BgmTrack.AUTUMN, BgmTrack.WINTER),
            Season.entries.map(BgmTrack::forSeason),
        )
        // The spring tune is the one every other track falls back on.
        assertEquals(BgmTrack.SPRING.resourceName, BgmTrack.FALLBACK_RESOURCE)
    }

    @Test
    fun `all five tunes are in the app, one file each`() {
        val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }
        val files = raw.listFiles()!!.filter { it.name.startsWith("bgm_") }
        val tunes = files.map { it.nameWithoutExtension }
        for (track in BgmTrack.entries) {
            assertTrue("missing ${track.resourceName}", track.resourceName in tunes)
        }
        // Exactly the five, no stray or duplicate (an .mp3 and an .ogg of the same name would clash).
        assertEquals(BgmTrack.entries.map { it.resourceName }.sorted(), tunes.sorted())
        assertTrue(files.all { it.extension == "mp3" && it.length() > 0 })
    }

    @Test
    fun `with every tune present, each track plays its own and the fallback is not used`() {
        BgmTrack.entries.forEachIndexed { i, track -> rawFiles[track.resourceName] = 60 + i }

        assertEquals(BgmTrack.entries.indices.map { 60 + it }, BgmTrack.entries.map(audio::resourceFor))

        // The year going round: every change is a new tune, handed to the engine to crossfade.
        audio.playBgm(BgmTrack.TITLE)
        Season.entries.forEach { audio.playBgm(BgmTrack.forSeason(it)) }
        audio.playBgm(BgmTrack.SPRING)
        assertEquals(listOf(60, 61, 62, 63, 64, 61), engine.bgmStarts.map { it.first })
        assertFalse("stop" in engine.bgmCalls)
    }

    @Test
    fun `a track without its own file plays the spring tune`() {
        rawFiles["bgm_spring"] = 50

        audio.playBgm(BgmTrack.WINTER)

        assertEquals(listOf(50 to AudioManager.DEFAULT_BGM_VOLUME), engine.bgmStarts)
        assertEquals(BgmTrack.WINTER, audio.currentTrack)
    }

    @Test
    fun `moving between tracks that share a file carries the music on without a restart`() {
        rawFiles["bgm_spring"] = 50

        audio.playBgm(BgmTrack.TITLE)   // the title has no tune of its own yet
        audio.playBgm(BgmTrack.SPRING)  // into a spring garden
        audio.playBgm(BgmTrack.SUMMER)  // and on into summer, also without one

        assertEquals(1, engine.bgmStarts.size)
        assertFalse("stop" in engine.bgmCalls)
    }

    @Test
    fun `a season with its own tune takes over from the one before`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["bgm_summer"] = 51
        rawFiles["bgm_title"] = 52

        audio.playBgm(BgmTrack.TITLE)
        audio.playBgm(BgmTrack.SPRING)
        audio.playBgm(BgmTrack.SUMMER)
        audio.playBgm(BgmTrack.SUMMER)

        // Each change hands over to the engine, which crossfades; asking again changes nothing.
        assertEquals(listOf(52, 50, 51), engine.bgmStarts.map { it.first })
        assertFalse("stop" in engine.bgmCalls)
    }

    @Test
    fun `back to a season whose tune is shared, after one with its own, starts it again`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["bgm_winter"] = 53

        audio.playBgm(BgmTrack.WINTER)
        audio.playBgm(BgmTrack.AUTUMN) // no file: falls back on spring's
        audio.playBgm(BgmTrack.SPRING) // the same file as autumn's: no restart

        assertEquals(listOf(53, 50), engine.bgmStarts.map { it.first })
    }

    @Test
    fun `a season that turns while the music is muted is the tune heard when it comes back`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["bgm_autumn"] = 54
        audio.playBgm(BgmTrack.SPRING)
        audio.bgmEnabled = false

        audio.playBgm(BgmTrack.AUTUMN)
        assertEquals(1, engine.bgmStarts.size)

        audio.bgmEnabled = true
        assertEquals(54 to AudioManager.DEFAULT_BGM_VOLUME, engine.bgmStarts.last())
    }

    @Test
    fun `a season that turns in the background is the tune heard on return`() {
        rawFiles["bgm_spring"] = 50
        rawFiles["bgm_summer"] = 51
        audio.playBgm(BgmTrack.SPRING)
        audio.pauseBgm()

        audio.playBgm(BgmTrack.SUMMER)
        assertEquals(1, engine.bgmStarts.size)

        audio.resumeBgm()
        assertEquals(51, engine.bgmStarts.last().first)
    }

    // --- The settings screen and the music ---

    @Test
    fun `the slider and the switch reach the music as they move`() {
        rawFiles["bgm_spring"] = 50
        audio.playBgm(BgmTrack.SPRING)
        val settings = AppSettings()

        audio.apply(settings.copy(bgmVolume = 0.6f))
        audio.apply(settings.copy(bgmVolume = 0.1f))
        assertEquals(listOf(0.6f, 0.1f), engine.bgmVolumes)

        audio.apply(settings.copy(bgmVolume = 0.1f, bgmEnabled = false))
        assertEquals("pause", engine.bgmCalls.last())
        audio.apply(settings.copy(bgmVolume = 0.1f, bgmEnabled = true))
        assertEquals("resume", engine.bgmCalls.last())
        // A new tune after that starts at the chosen volume.
        rawFiles["bgm_winter"] = 53
        audio.playBgm(BgmTrack.WINTER)
        assertEquals(53 to 0.1f, engine.bgmStarts.last())
    }

    @Test
    fun `saved settings reach the music before it starts, and every change after`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val app = main.resolve("java/com/studioguu/miniaturegarden/PocketGardenApplication.kt").readText()
        assertTrue(app.contains("audio.apply(runBlocking { settingsRepository.settings.first() })"))
        assertTrue(app.contains("settingsRepository.settings.collect(audio::apply)"))
        // The settings screen plays each change at once, then saves it.
        val screen = main.resolve("java/com/studioguu/miniaturegarden/ui/settings/SettingsViewModel.kt").readText()
        val change = screen.substringAfter("private fun change(")
        assertTrue(change.indexOf("audio.apply(updated)") in 0 until change.indexOf("settingsRepository.update"))
    }

    @Test
    fun `the garden plays its season's tune and the title its own`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val garden = main.resolve("java/com/studioguu/miniaturegarden/ui/GardenViewModel.kt").readText()
        assertTrue(garden.contains("audio.playBgm(BgmTrack.forSeason(season))"))
        assertTrue(garden.contains("musicSeason?.let { audio.playBgm(BgmTrack.forSeason(it)) }"))
        val title = main.resolve("java/com/studioguu/miniaturegarden/ui/title/TitleViewModel.kt").readText()
        assertTrue(title.contains("audio.playBgm(BgmTrack.TITLE)"))
    }
}

private data class Duck(val duckedVolume: Float, val restoreVolume: Float, val holdMillis: Long)

private class FakeAudioEngine : AudioEngine {
    val loaded = ArrayList<Int>()
    val failingLoads = HashSet<Int>()
    val played = ArrayList<Pair<Int, Float>>()
    val bgmStarts = ArrayList<Pair<Int, Float>>()
    val bgmCalls = ArrayList<String>()
    val bgmVolumes = ArrayList<Float>()
    var bgmStartSucceeds = true
    val durations = HashMap<Int, Long>()
    val ducks = ArrayList<Duck>()

    fun handleFor(resId: Int) = resId + 1000

    override fun loadEffect(resId: Int): LoadedEffect? {
        loaded += resId
        return if (resId in failingLoads) null else LoadedEffect(handleFor(resId), durations[resId] ?: 500L)
    }

    override fun duckBgm(duckedVolume: Float, restoreVolume: Float, holdMillis: Long) {
        ducks += Duck(duckedVolume, restoreVolume, holdMillis)
    }

    override fun playEffect(handle: Int, volume: Float) {
        played += handle to volume
    }

    override fun startBgm(resId: Int, volume: Float): Boolean {
        bgmStarts += resId to volume
        return bgmStartSucceeds
    }

    override fun pauseBgm() {
        bgmCalls += "pause"
    }

    override fun resumeBgm() {
        bgmCalls += "resume"
    }

    override fun stopBgm() {
        bgmCalls += "stop"
    }

    override fun setBgmVolume(volume: Float) {
        bgmVolumes += volume
    }

    override fun release() = Unit
}
