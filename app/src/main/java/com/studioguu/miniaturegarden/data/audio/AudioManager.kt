package com.studioguu.miniaturegarden.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.model.Season
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Short sounds. [resourceName] is the file name in `res/raw` without its extension,
 * e.g. `res/raw/se_tap.mp3`.
 *
 * [gain] takes the edge off the source files that were recorded hotter than the rest. SoundPool
 * can only turn a sound down, so the quiet files play at full volume and the loud ones are
 * trimmed a little, staying within 0.8 .. 1.0 so that every effect is clearly above the music.
 * Measured loudness (loudest 400 ms, dBFS, see `tools/measure_audio.py`) is noted per file;
 * re-measure when one is replaced.
 */
enum class SoundEffect(val resourceName: String, val gain: Float = 1f) {
    /** Not triggered by anything at the moment: selecting a tile on the map is silent. */
    TAP("se_tap", gain = 0.80f),          // -19.2 dBFS
    WATER("se_water"),                    // -24.6 dBFS, already below target
    FERTILIZE("se_fertilize"),            // -22.6 dBFS
    PLANT("se_plant"),                    // -22.4 dBFS
    HARVEST("se_harvest", gain = 0.80f),  // -12.0 dBFS: by far the loudest file
    COIN("se_coin"),                      // -29.0 dBFS: the quietest file, needs every bit
    LEVEL_UP("se_level_up", gain = 0.90f), // -16.1 dBFS
    ERROR("se_error"),                    // -25.3 dBFS
}

/**
 * Looping background music: the title's, and one for each season in the garden.
 * [resourceName] works like [SoundEffect.resourceName].
 *
 * A track whose own file is not there yet plays [FALLBACK_RESOURCE] (the spring tune) instead,
 * so new tunes can be dropped into `res/raw` under these names one at a time, with nothing else
 * to change. Two tracks that come down to the same file are one piece of music: moving between
 * them does not restart it.
 */
enum class BgmTrack(val resourceName: String) {
    TITLE("bgm_title"),
    SPRING("bgm_spring"),
    SUMMER("bgm_summer"),
    AUTUMN("bgm_autumn"),
    WINTER("bgm_winter");

    companion object {
        /** What plays for a track that has no file of its own. */
        const val FALLBACK_RESOURCE = "bgm_spring"

        /** The garden's music in [season]. */
        fun forSeason(season: Season): BgmTrack = when (season) {
            Season.SPRING -> SPRING
            Season.SUMMER -> SUMMER
            Season.AUTUMN -> AUTUMN
            Season.WINTER -> WINTER
        }
    }
}

/**
 * Plays sound effects (SoundPool) and looping background music (MediaPlayer).
 *
 * Sound files are optional. They are looked up in `res/raw` by name at runtime rather than
 * through `R.raw`, so the app compiles and runs with none, some or all of them present: a
 * missing or unreadable file simply makes that sound a silent no-op.
 *
 * Music moves from one tune to the next by crossfading ([AudioEngine.startBgm]); asking for a
 * track that comes down to the file already playing leaves it running ([BgmTrack]).
 *
 * Effects and music are mixed by the system and never cut each other off. To keep effects
 * clear, the music sits well below them by default and is briefly "ducked" (turned down
 * further) for as long as an effect is sounding.
 *
 * Call from the main thread.
 */
@Singleton
class AudioManager internal constructor(
    /** Raw resource id for a file name, or 0 when there is no such file. */
    private val resolveRawId: (resourceName: String) -> Int,
    private val engine: AudioEngine,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        resolveRawId = { name -> context.resources.getIdentifier(name, "raw", context.packageName) },
        engine = AndroidAudioEngine(context),
    )

    // A null value records that the effect is unavailable, so it is not looked up again.
    private val loadedEffects = HashMap<SoundEffect, LoadedEffect?>()

    private var wantedTrack: BgmTrack? = null
    private var playingTrack: BgmTrack? = null

    /** The raw resource [playingTrack] is playing, which another track may share. */
    private var playingResId = 0
    private var bgmPaused = false

    var soundEffectsEnabled: Boolean = true

    var bgmEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                startBgmIfPossible()
            } else {
                // Paused, not stopped: turning the music back on continues from the same place.
                engine.pauseBgm()
            }
        }

    /** Master level for all effects, 0.0 (silent) .. 1.0 (full); each effect's own gain applies on top. */
    var soundEffectVolume: Float = DEFAULT_SOUND_EFFECT_VOLUME
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /** 0.0 (silent) .. 1.0 (full). */
    var bgmVolume: Float = DEFAULT_BGM_VOLUME
        set(value) {
            field = value.coerceIn(0f, 1f)
            engine.setBgmVolume(field)
        }

    /**
     * Takes over the player's saved settings. Safe to call with every change: only what
     * actually differs is touched, so music that is playing is not interrupted.
     */
    fun apply(settings: AppSettings) {
        val wanted = settings.normalized()
        soundEffectsEnabled = wanted.soundEffectsEnabled
        soundEffectVolume = wanted.soundEffectVolume
        if (bgmVolume != wanted.bgmVolume) bgmVolume = wanted.bgmVolume
        if (bgmEnabled != wanted.bgmEnabled) bgmEnabled = wanted.bgmEnabled
    }

    /**
     * Starts loading every effect that has a file. Loading is asynchronous, so call this early:
     * an effect played before its load finishes makes no sound.
     */
    fun preload() {
        SoundEffect.entries.forEach(::loadedEffectFor)
    }

    fun play(effect: SoundEffect) {
        if (!soundEffectsEnabled) return
        val loaded = loadedEffectFor(effect) ?: return
        engine.playEffect(loaded.handle, soundEffectVolume * effect.gain)
        if (playingTrack != null && !bgmPaused && bgmEnabled) {
            engine.duckBgm(
                duckedVolume = bgmVolume * BGM_DUCK_FACTOR,
                restoreVolume = bgmVolume,
                holdMillis = min(loaded.durationMillis, MAX_DUCK_MILLIS),
            )
        }
    }

    /**
     * Loops [track] until [stopBgm] or another track is requested. Does nothing if it is already
     * playing, or if it comes down to the same file as what is; otherwise the music crossfades.
     */
    fun playBgm(track: BgmTrack) {
        wantedTrack = track
        startBgmIfPossible()
    }

    /** For when the app leaves the foreground; [resumeBgm] continues from the same position. */
    fun pauseBgm() {
        bgmPaused = true
        engine.pauseBgm()
    }

    fun resumeBgm() {
        bgmPaused = false
        startBgmIfPossible()
    }

    fun stopBgm() {
        wantedTrack = null
        playingTrack = null
        playingResId = 0
        engine.stopBgm()
    }

    /** Frees the native players. The manager can be used again afterwards. */
    fun release() {
        engine.release()
        loadedEffects.clear()
        playingTrack = null
        playingResId = 0
    }

    private fun loadedEffectFor(effect: SoundEffect): LoadedEffect? {
        if (effect !in loadedEffects) {
            val resId = resolveRawId(effect.resourceName)
            loadedEffects[effect] = if (resId == 0) null else engine.loadEffect(resId)
        }
        return loadedEffects[effect]
    }

    /** The track that should be heard now, or null while the music is stopped. */
    val currentTrack: BgmTrack? get() = wantedTrack

    /** The file [track] plays: its own, or the fallback; 0 when neither is there. */
    internal fun resourceFor(track: BgmTrack): Int =
        resolveRawId(track.resourceName).takeIf { it != 0 } ?: resolveRawId(BgmTrack.FALLBACK_RESOURCE)

    private fun startBgmIfPossible() {
        val track = wantedTrack ?: return
        if (!bgmEnabled || bgmPaused) return
        if (playingTrack == track) {
            engine.resumeBgm()
            return
        }
        val resId = resourceFor(track)
        if (resId != 0 && resId == playingResId) {
            // Another name for the music already playing: it simply carries on.
            playingTrack = track
            engine.resumeBgm()
            return
        }
        val started = resId != 0 && engine.startBgm(resId, bgmVolume)
        playingTrack = if (started) track else null
        playingResId = if (started) resId else 0
    }

    companion object {
        /** The values in effect until [apply] is called; the same as a new player's settings. */
        const val DEFAULT_SOUND_EFFECT_VOLUME = AppSettings.DEFAULT_SOUND_EFFECT_VOLUME

        /**
         * The music file is mastered loud (about -18 dBFS average, peaks at 0) while most effects
         * sit around -22 to -29, so it has to come down a long way to stay in the background.
         */
        const val DEFAULT_BGM_VOLUME = AppSettings.DEFAULT_BGM_VOLUME

        /** While an effect sounds, the music plays at this fraction of its volume (about -6 dB). */
        const val BGM_DUCK_FACTOR = 0.5f

        /** Long effects only hold the music down for their opening, not their whole tail. */
        const val MAX_DUCK_MILLIS = 2_500L
    }
}

/** An effect accepted by the engine: the handle to play it with and how long it lasts. */
internal data class LoadedEffect(val handle: Int, val durationMillis: Long)

/** The actual players, separated out so [AudioManager]'s rules can be tested without Android. */
internal interface AudioEngine {
    /** Starts loading an effect; null if it cannot be loaded. */
    fun loadEffect(resId: Int): LoadedEffect?
    fun playEffect(handle: Int, volume: Float)

    /**
     * Replaces any current music with a looping [resId], crossfading: what was sounding fades
     * out while the new tune fades in to [volume]. False if it could not be started.
     */
    fun startBgm(resId: Int, volume: Float): Boolean
    fun pauseBgm()
    fun resumeBgm()
    fun stopBgm()
    fun setBgmVolume(volume: Float)

    /**
     * Drops the music to [duckedVolume] now and brings it back to [restoreVolume] once
     * [holdMillis] have passed. A call during an earlier duck extends it, never shortens it.
     */
    fun duckBgm(duckedVolume: Float, restoreVolume: Float, holdMillis: Long)
    fun release()
}

/** Every call is guarded: a failing player must never take the game down with it. */
private class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private var soundPool: SoundPool? = null
    private var bgmPlayer: MediaPlayer? = null

    /** The tune being faded out after another took its place; released when the fade ends. */
    private var fadingPlayer: MediaPlayer? = null
    private val fadeToken = Any()

    /** The music's volume as last asked for; fades work towards it. */
    private var bgmVolume = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val duckToken = Any()
    private var duckEndUptime = 0L

    private fun pool(): SoundPool = soundPool ?: SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .also { soundPool = it }

    override fun loadEffect(resId: Int): LoadedEffect? {
        val handle = runCatching { pool().load(context, resId, 1) }.getOrNull()?.takeIf { it != 0 } ?: return null
        return LoadedEffect(handle, durationOf(resId))
    }

    private fun durationOf(resId: Int): Long = runCatching {
        context.resources.openRawResourceFd(resId).use { fd ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            } finally {
                retriever.release()
            }
        }
    }.getOrNull() ?: DEFAULT_EFFECT_MILLIS

    override fun playEffect(handle: Int, volume: Float) {
        runCatching { pool().play(handle, volume, volume, 1, 0, 1f) }
    }

    override fun startBgm(resId: Int, volume: Float): Boolean {
        // What is sounding now fades out over the same time the new tune fades in; a paused
        // or broken player simply goes.
        val previous = bgmPlayer?.takeIf { runCatching { it.isPlaying }.getOrDefault(false) }
        if (previous == null) {
            stopBgm()
        } else {
            bgmPlayer = null
            cancelDuck()
            endFade()
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        // create() returns null for a file it cannot decode.
        val player = runCatching {
            val systemAudio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            MediaPlayer.create(context, resId, attributes, systemAudio.generateAudioSessionId())
        }.getOrNull() ?: run {
            previous?.let(::releasePlayer)
            return false
        }
        bgmVolume = volume
        val started = runCatching {
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.start()
            bgmPlayer = player
        }.onFailure { runCatching { player.release() } }.isSuccess
        if (!started) {
            previous?.let(::releasePlayer)
            return false
        }
        fadingPlayer = previous
        val start = SystemClock.uptimeMillis()
        for (step in 1..FADE_STEPS) {
            handler.postAtTime(
                {
                    val t = step.toFloat() / FADE_STEPS
                    applyBgmVolume(bgmVolume * t)
                    fadingPlayer?.let { old -> runCatching { old.setVolume(volume * (1f - t), volume * (1f - t)) } }
                    if (step == FADE_STEPS) endFade()
                },
                fadeToken,
                start + FADE_MILLIS * step / FADE_STEPS,
            )
        }
        return true
    }

    /** Ends any fade where it is: the old tune goes, the new one is set to its full volume. */
    private fun endFade() {
        handler.removeCallbacksAndMessages(fadeToken)
        fadingPlayer?.let(::releasePlayer)
        fadingPlayer = null
        applyBgmVolume(bgmVolume)
    }

    private fun releasePlayer(player: MediaPlayer) {
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    override fun pauseBgm() {
        // A tune halfway out is not worth coming back to.
        endFade()
        runCatching { bgmPlayer?.takeIf { it.isPlaying }?.pause() }
    }

    override fun resumeBgm() {
        runCatching { bgmPlayer?.takeIf { !it.isPlaying }?.start() }
    }

    override fun stopBgm() {
        cancelDuck()
        handler.removeCallbacksAndMessages(fadeToken)
        fadingPlayer?.let(::releasePlayer)
        fadingPlayer = null
        val player = bgmPlayer ?: return
        bgmPlayer = null
        releasePlayer(player)
    }

    override fun setBgmVolume(volume: Float) {
        // An explicit volume change wins over a duck still waiting to restore the old one, and
        // over a fade still on its way to the old one.
        cancelDuck()
        bgmVolume = volume
        endFade()
    }

    override fun duckBgm(duckedVolume: Float, restoreVolume: Float, holdMillis: Long) {
        if (bgmPlayer == null) return
        handler.removeCallbacksAndMessages(duckToken)
        applyBgmVolume(duckedVolume)
        duckEndUptime = max(duckEndUptime, SystemClock.uptimeMillis() + holdMillis)
        // Come back up in a few small steps; a single jump is audible as a "pump".
        for (step in 1..RESTORE_STEPS) {
            val volume = duckedVolume + (restoreVolume - duckedVolume) * step / RESTORE_STEPS
            handler.postAtTime(
                { applyBgmVolume(volume) },
                duckToken,
                duckEndUptime + RESTORE_MILLIS * step / RESTORE_STEPS,
            )
        }
    }

    private fun cancelDuck() {
        handler.removeCallbacksAndMessages(duckToken)
        duckEndUptime = 0L
    }

    private fun applyBgmVolume(volume: Float) {
        runCatching { bgmPlayer?.setVolume(volume, volume) }
    }

    override fun release() {
        stopBgm()
        runCatching { soundPool?.release() }
        soundPool = null
    }

    private companion object {
        /** How many effects may overlap before the oldest is cut off. */
        const val MAX_STREAMS = 6

        /** Assumed length of an effect whose duration cannot be read. */
        const val DEFAULT_EFFECT_MILLIS = 600L

        const val RESTORE_MILLIS = 300L
        const val RESTORE_STEPS = 6

        /** How long one tune takes to give way to the next. */
        const val FADE_MILLIS = 1_200L
        const val FADE_STEPS = 12
    }
}
