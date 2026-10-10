package com.studioguu.miniaturegarden.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short sounds. [resourceName] is the file name in `res/raw` without its extension,
 * e.g. `res/raw/se_tap.ogg`.
 */
enum class SoundEffect(val resourceName: String) {
    TAP("se_tap"),
    WATER("se_water"),
    FERTILIZE("se_fertilize"),
    PLANT("se_plant"),
    HARVEST("se_harvest"),
    COIN("se_coin"),
    LEVEL_UP("se_level_up"),
    ERROR("se_error"),
}

/** Looping background music; [resourceName] works like [SoundEffect.resourceName]. */
enum class BgmTrack(val resourceName: String) {
    GARDEN("bgm_garden"),
}

/**
 * Plays sound effects (SoundPool) and looping background music (MediaPlayer).
 *
 * Sound files are optional. They are looked up in `res/raw` by name at runtime rather than
 * through `R.raw`, so the app compiles and runs with none, some or all of them present: a
 * missing or unreadable file simply makes that sound a silent no-op.
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
    private val effectHandles = HashMap<SoundEffect, Int?>()

    private var wantedTrack: BgmTrack? = null
    private var playingTrack: BgmTrack? = null
    private var bgmPaused = false

    var soundEffectsEnabled: Boolean = true

    var bgmEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                startBgmIfPossible()
            } else {
                engine.stopBgm()
                playingTrack = null
            }
        }

    /** 0.0 (silent) .. 1.0 (full). */
    var soundEffectVolume: Float = 1f
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
     * Starts loading every effect that has a file. Loading is asynchronous, so call this early:
     * an effect played before its load finishes makes no sound.
     */
    fun preload() {
        SoundEffect.entries.forEach(::handleFor)
    }

    fun play(effect: SoundEffect) {
        if (!soundEffectsEnabled) return
        val handle = handleFor(effect) ?: return
        engine.playEffect(handle, soundEffectVolume)
    }

    /** Loops [track] until [stopBgm] or another track is requested. Does nothing if it is already playing. */
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
        engine.stopBgm()
    }

    /** Frees the native players. The manager can be used again afterwards. */
    fun release() {
        engine.release()
        effectHandles.clear()
        playingTrack = null
    }

    private fun handleFor(effect: SoundEffect): Int? {
        if (effect !in effectHandles) {
            val resId = resolveRawId(effect.resourceName)
            effectHandles[effect] = if (resId == 0) null else engine.loadEffect(resId)
        }
        return effectHandles[effect]
    }

    private fun startBgmIfPossible() {
        val track = wantedTrack ?: return
        if (!bgmEnabled || bgmPaused) return
        if (playingTrack == track) {
            engine.resumeBgm()
            return
        }
        val resId = resolveRawId(track.resourceName)
        playingTrack = if (resId != 0 && engine.startBgm(resId, bgmVolume)) track else null
    }

    companion object {
        /** Music sits under the effects by default. */
        const val DEFAULT_BGM_VOLUME = 0.6f
    }
}

/** The actual players, separated out so [AudioManager]'s rules can be tested without Android. */
internal interface AudioEngine {
    /** Starts loading an effect; returns its handle, or null if it cannot be loaded. */
    fun loadEffect(resId: Int): Int?
    fun playEffect(handle: Int, volume: Float)

    /** Replaces any current music with a looping [resId]; false if it could not be started. */
    fun startBgm(resId: Int, volume: Float): Boolean
    fun pauseBgm()
    fun resumeBgm()
    fun stopBgm()
    fun setBgmVolume(volume: Float)
    fun release()
}

/** Every call is guarded: a failing player must never take the game down with it. */
private class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private var soundPool: SoundPool? = null
    private var bgmPlayer: MediaPlayer? = null

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

    override fun loadEffect(resId: Int): Int? =
        runCatching { pool().load(context, resId, 1) }.getOrNull()?.takeIf { it != 0 }

    override fun playEffect(handle: Int, volume: Float) {
        runCatching { pool().play(handle, volume, volume, 1, 0, 1f) }
    }

    override fun startBgm(resId: Int, volume: Float): Boolean {
        stopBgm()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        // create() returns null for a file it cannot decode.
        val player = runCatching {
            val systemAudio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            MediaPlayer.create(context, resId, attributes, systemAudio.generateAudioSessionId())
        }.getOrNull() ?: return false
        return runCatching {
            player.isLooping = true
            player.setVolume(volume, volume)
            player.start()
            bgmPlayer = player
        }.onFailure { runCatching { player.release() } }.isSuccess
    }

    override fun pauseBgm() {
        runCatching { bgmPlayer?.takeIf { it.isPlaying }?.pause() }
    }

    override fun resumeBgm() {
        runCatching { bgmPlayer?.takeIf { !it.isPlaying }?.start() }
    }

    override fun stopBgm() {
        val player = bgmPlayer ?: return
        bgmPlayer = null
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    override fun setBgmVolume(volume: Float) {
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
    }
}
