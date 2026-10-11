package com.studioguu.miniaturegarden.data.audio

import org.junit.Assert.assertEquals
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
        audio.playBgm(BgmTrack.GARDEN)
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
        assertEquals(listOf(engine.handleFor(11) to 1f, engine.handleFor(11) to 1f), engine.played)
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
    fun `gains only ever turn an effect down, and the quiet files stay at full volume`() {
        assertTrue(SoundEffect.entries.all { it.gain > 0f && it.gain <= 1f })
        assertEquals(1f, SoundEffect.COIN.gain, 0f)
        // The hottest file is trimmed the most.
        assertEquals(SoundEffect.HARVEST, SoundEffect.entries.minBy { it.gain })
    }

    @Test
    fun `default music volume sits well below the effects`() {
        assertEquals(1f, audio.soundEffectVolume, 0f)
        assertTrue(AudioManager.DEFAULT_BGM_VOLUME in 0.3f..0.4f)
        assertEquals(AudioManager.DEFAULT_BGM_VOLUME, audio.bgmVolume, 0f)
    }

    @Test
    fun `an effect ducks the music for its own length`() {
        rawFiles["bgm_garden"] = 50
        rawFiles["se_coin"] = 2
        engine.durations[2] = 1_500L
        audio.playBgm(BgmTrack.GARDEN)

        audio.play(SoundEffect.COIN)

        val volume = AudioManager.DEFAULT_BGM_VOLUME
        assertEquals(listOf(Duck(volume * AudioManager.BGM_DUCK_FACTOR, volume, 1_500L)), engine.ducks)
    }

    @Test
    fun `a long effect ducks the music only for the capped time`() {
        rawFiles["bgm_garden"] = 50
        rawFiles["se_level_up"] = 3
        engine.durations[3] = 4_130L
        audio.playBgm(BgmTrack.GARDEN)

        audio.play(SoundEffect.LEVEL_UP)

        assertEquals(AudioManager.MAX_DUCK_MILLIS, engine.ducks.single().holdMillis)
    }

    @Test
    fun `ducking follows the current music volume and is skipped while music is not sounding`() {
        rawFiles["bgm_garden"] = 50
        rawFiles["se_tap"] = 1
        audio.playBgm(BgmTrack.GARDEN)

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
        rawFiles["bgm_garden"] = 50

        audio.playBgm(BgmTrack.GARDEN)
        audio.playBgm(BgmTrack.GARDEN)

        assertEquals(listOf(50 to AudioManager.DEFAULT_BGM_VOLUME), engine.bgmStarts)
    }

    @Test
    fun `pause and resume continue the same playback`() {
        rawFiles["bgm_garden"] = 50
        audio.playBgm(BgmTrack.GARDEN)

        audio.pauseBgm()
        audio.resumeBgm()

        assertEquals(1, engine.bgmStarts.size)
        assertEquals(listOf("pause", "resume"), engine.bgmCalls)
    }

    @Test
    fun `bgm requested while paused waits for resume`() {
        rawFiles["bgm_garden"] = 50

        audio.pauseBgm()
        audio.playBgm(BgmTrack.GARDEN)
        assertTrue(engine.bgmStarts.isEmpty())

        audio.resumeBgm()
        assertEquals(1, engine.bgmStarts.size)
    }

    @Test
    fun `disabling bgm stops it and enabling starts it again`() {
        rawFiles["bgm_garden"] = 50
        audio.playBgm(BgmTrack.GARDEN)

        audio.bgmEnabled = false
        assertEquals(listOf("stop"), engine.bgmCalls)
        audio.playBgm(BgmTrack.GARDEN)
        assertEquals(1, engine.bgmStarts.size)

        audio.bgmEnabled = true
        assertEquals(2, engine.bgmStarts.size)
    }

    @Test
    fun `stopped bgm does not come back on resume`() {
        rawFiles["bgm_garden"] = 50
        audio.playBgm(BgmTrack.GARDEN)

        audio.stopBgm()
        audio.pauseBgm()
        audio.resumeBgm()

        assertEquals(1, engine.bgmStarts.size)
    }

    @Test
    fun `bgm that cannot be decoded is retried on the next request instead of crashing`() {
        rawFiles["bgm_garden"] = 50
        engine.bgmStartSucceeds = false
        audio.playBgm(BgmTrack.GARDEN)

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
