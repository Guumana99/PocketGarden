package com.studioguu.miniaturegarden.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
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
    /** The hoe knocking on hard ground, and a sprinkler being switched off. Selecting a tile is silent. */
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
 * Each tune is two files: [introResourceName] (`bgm_x_intro`, the opening, played once) and
 * [resourceName] (`bgm_x`, the part that repeats). The intro ends exactly where the loop begins,
 * so playing them back to back sounds like the original piece from its very start. A tune
 * without an intro file simply loops [resourceName] from its start.
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

    /** The opening played once before [resourceName] starts repeating. */
    val introResourceName: String get() = introOf(resourceName)

    companion object {
        const val INTRO_SUFFIX = "_intro"

        /** The intro file belonging to the loop file [loopResourceName]. */
        fun introOf(loopResourceName: String): String = loopResourceName + INTRO_SUFFIX

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
     * Keeps everything silent until the first [apply]: for the moment at start-up while the saved
     * settings are still being read, so that nothing plays at the defaults first.
     */
    fun holdUntilSettings() {
        soundEffectsEnabled = false
        bgmEnabled = false
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
    internal fun resourceFor(track: BgmTrack): Int = resourceNameFor(track)?.let(resolveRawId) ?: 0

    /** The name of the file [track] plays: its own, or the fallback; null when neither is there. */
    private fun resourceNameFor(track: BgmTrack): String? = when {
        resolveRawId(track.resourceName) != 0 -> track.resourceName
        resolveRawId(BgmTrack.FALLBACK_RESOURCE) != 0 -> BgmTrack.FALLBACK_RESOURCE
        else -> null
    }

    private fun startBgmIfPossible() {
        val track = wantedTrack ?: return
        if (!bgmEnabled || bgmPaused) return
        if (playingTrack == track) {
            engine.resumeBgm()
            return
        }
        val resourceName = resourceNameFor(track)
        val resId = resourceName?.let(resolveRawId) ?: 0
        if (resId != 0 && resId == playingResId) {
            // Another name for the music already playing: it simply carries on.
            playingTrack = track
            engine.resumeBgm()
            return
        }
        // The intro that belongs to the file actually played (the fallback's, for a track without one).
        val introId = resourceName?.let { resolveRawId(BgmTrack.introOf(it)) } ?: 0
        val started = resId != 0 && engine.startBgm(resId, introId, bgmVolume)
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
internal data class LoadedEffect(
    val handle: Int,
    /** May be filled in later, from another thread, once the file's real length has been read. */
    @Volatile var durationMillis: Long,
)

/** The actual players, separated out so [AudioManager]'s rules can be tested without Android. */
internal interface AudioEngine {
    /** Starts loading an effect; null if it cannot be loaded. */
    fun loadEffect(resId: Int): LoadedEffect?
    fun playEffect(handle: Int, volume: Float)

    /**
     * Replaces any current music with a looping [resId], crossfading: what was sounding fades
     * out while the new tune fades in to [volume]. When [introResId] is not 0 that file plays
     * once first and [resId] follows it seamlessly, then repeats; pausing, volume, ducking and
     * fades apply to whichever of the two is sounding. False if it could not be started.
     */
    fun startBgm(resId: Int, introResId: Int, volume: Float): Boolean
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

/**
 * One piece of music: an optional [intro] played once, chained ([MediaPlayer.setNextMediaPlayer])
 * to the [loop] that then repeats. Once the intro has finished it is released and dropped, and
 * everything that acts on the music acts on the loop alone.
 */
private class Tune(var intro: MediaPlayer?, val loop: MediaPlayer) {
    /** Players whose preparation has not finished yet; the tune can start when this is empty. */
    val unprepared = HashSet<MediaPlayer>().apply { add(loop); intro?.let(::add) }

    /** The player that is sounding (or will sound first). */
    val head: MediaPlayer get() = intro ?: loop

    val players: List<MediaPlayer> get() = listOfNotNull(intro, loop)

    val isPlaying: Boolean get() = runCatching { head.isPlaying }.getOrDefault(false)

    fun setVolume(volume: Float) {
        players.forEach { runCatching { it.setVolume(volume, volume) } }
    }
}

/**
 * Every call is guarded: a failing player must never take the game down with it.
 *
 * Nothing slow happens on the main thread: a tune is prepared in the background
 * ([MediaPlayer.prepareAsync]) and starts, fading in, once it is ready, and an effect's length is
 * read on a thread of its own. Both used to be done in place, which held up the first frames of
 * the title and of the garden.
 */
private class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private var soundPool: SoundPool? = null

    /** The tune that is playing, or the one being prepared to play next ([bgmPrepared] false). */
    private var bgmTune: Tune? = null

    /** Whether [bgmTune] has been prepared; until then it is not touched, only released. */
    private var bgmPrepared = false

    /** The music was paused while [bgmTune] was still being prepared: it waits for [resumeBgm]. */
    private var startHeld = false

    /** The tune still playing while the one replacing it is prepared; it fades out once that starts. */
    private var waitingPrevious: Tune? = null

    /** The tune being faded out after another took its place; released when the fade ends. */
    private var fadingTune: Tune? = null
    private val fadeToken = Any()

    /** The music's volume as last asked for; fades work towards it. */
    private var bgmVolume = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val duckToken = Any()
    private var duckEndUptime = 0L

    /** Reads the effects' lengths, one after another, away from the main thread. */
    private val durationReader by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "effect-durations").apply { isDaemon = true } }
    }

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
        // Assumed until its real length has been read.
        val effect = LoadedEffect(handle, DEFAULT_EFFECT_MILLIS)
        runCatching { durationReader.execute { durationOf(resId)?.let { effect.durationMillis = it } } }
        return effect
    }

    private fun durationOf(resId: Int): Long? = runCatching {
        context.resources.openRawResourceFd(resId).use { fd ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            } finally {
                retriever.release()
            }
        }
    }.getOrNull()

    override fun playEffect(handle: Int, volume: Float) {
        runCatching { pool().play(handle, volume, volume, 1, 0, 1f) }
    }

    private fun newPlayer(resId: Int, looping: Boolean): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            context.resources.openRawResourceFd(resId).use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            isLooping = looping
        }
    }.getOrNull()

    override fun startBgm(resId: Int, introResId: Int, volume: Float): Boolean {
        // What is sounding now fades out over the same time the new tune fades in; a paused
        // or broken player simply goes. A tune still being prepared is dropped for this one, and
        // what was playing before it is the one that fades out.
        val previous = if (bgmPrepared) {
            bgmTune?.takeIf { it.isPlaying }
        } else {
            bgmTune?.let(::releaseTune)
            bgmTune = null
            waitingPrevious.also { waitingPrevious = null }
        }
        if (previous == null) {
            stopBgm()
        } else {
            bgmTune = null
            bgmPrepared = false
            cancelDuck()
            endFade()
        }
        startHeld = false
        val loop = newPlayer(resId, looping = true) ?: run {
            previous?.let(::releaseTune)
            return false
        }
        // An intro that cannot be opened is left out: the loop then plays from its own start.
        val intro = if (introResId != 0) newPlayer(introResId, looping = false) else null
        val tune = Tune(intro, loop)
        bgmVolume = volume
        bgmTune = tune
        bgmPrepared = false
        waitingPrevious = previous
        for (player in tune.players) {
            player.setOnPreparedListener { ready -> onPlayerPrepared(tune, ready) }
            player.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "BGM player error what=$what extra=$extra")
                onPlayerError(tune, failed)
                true
            }
        }
        intro?.setOnCompletionListener { finished -> onIntroFinished(tune, finished) }
        return runCatching { tune.players.forEach { it.prepareAsync() } }.onFailure {
            bgmTune = null
            waitingPrevious = null
            releaseTune(tune)
            previous?.let(::releaseTune)
        }.isSuccess
    }

    private fun onPlayerPrepared(tune: Tune, player: MediaPlayer) {
        // Replaced or stopped while it was being prepared: it has been released already.
        if (bgmTune !== tune || bgmPrepared) return
        tune.unprepared.remove(player)
        if (tune.unprepared.isEmpty()) onBgmPrepared(tune)
    }

    private fun onPlayerError(tune: Tune, failed: MediaPlayer) {
        if (failed === tune.intro && bgmTune === tune && !bgmPrepared) {
            // The intro could not be read: the loop plays on its own, from its start.
            tune.intro = null
            tune.unprepared.remove(failed)
            releasePlayer(failed)
            if (tune.unprepared.isEmpty()) onBgmPrepared(tune)
            return
        }
        if (bgmTune === tune) {
            // The tune that was playing, if any, carries on in its place.
            bgmTune = waitingPrevious
            bgmPrepared = waitingPrevious != null
            waitingPrevious = null
        }
        releaseTune(tune)
    }

    /**
     * The intro has played to its end. The loop normally took over by itself, without a gap
     * ([MediaPlayer.setNextMediaPlayer]); if that chaining was refused it is started here instead.
     * From now on the loop is the whole tune.
     */
    private fun onIntroFinished(tune: Tune, intro: MediaPlayer) {
        if (tune.intro !== intro) return
        tune.intro = null
        val handedOver = runCatching { tune.loop.isPlaying }.getOrDefault(false)
        if (!handedOver) runCatching { tune.loop.start() }
        Log.d(TAG, "BGM intro finished, loop playing (chained=$handedOver)")
        releasePlayer(intro)
    }

    /** [tune] is ready: it starts quietly and fades in while what played before fades out. */
    private fun onBgmPrepared(tune: Tune) {
        bgmPrepared = true
        tune.intro?.let { intro ->
            // Both are prepared, as setNextMediaPlayer requires.
            if (runCatching { intro.setNextMediaPlayer(tune.loop) }.isFailure) {
                Log.w(TAG, "BGM could not chain intro to loop; it will start on completion")
            }
        }
        val previous = waitingPrevious
        waitingPrevious = null
        if (startHeld) {
            // Paused meanwhile: it starts, at its full volume, when the music resumes.
            previous?.let(::releaseTune)
            applyBgmVolume(bgmVolume)
            return
        }
        val started = runCatching {
            tune.setVolume(0f)
            tune.head.start()
        }.isSuccess
        if (!started) {
            bgmTune = null
            bgmPrepared = false
            releaseTune(tune)
            previous?.let(::releaseTune)
            return
        }
        Log.d(TAG, "BGM started (intro=${tune.intro != null})")
        fadingTune = previous
        val volume = bgmVolume
        val start = SystemClock.uptimeMillis()
        for (step in 1..FADE_STEPS) {
            handler.postAtTime(
                {
                    val t = step.toFloat() / FADE_STEPS
                    applyBgmVolume(bgmVolume * t)
                    fadingTune?.setVolume(volume * (1f - t))
                    if (step == FADE_STEPS) endFade()
                },
                fadeToken,
                start + FADE_MILLIS * step / FADE_STEPS,
            )
        }
    }

    /** Ends any fade where it is: the old tune goes, the new one is set to its full volume. */
    private fun endFade() {
        handler.removeCallbacksAndMessages(fadeToken)
        fadingTune?.let(::releaseTune)
        fadingTune = null
        applyBgmVolume(bgmVolume)
    }

    private fun releaseTune(tune: Tune) {
        tune.players.forEach(::releasePlayer)
    }

    private fun releasePlayer(player: MediaPlayer) {
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    override fun pauseBgm() {
        // A tune halfway out is not worth coming back to.
        endFade()
        if (!bgmPrepared) {
            // Not ready yet: it waits, and the old tune it was to replace goes now.
            if (bgmTune != null) startHeld = true
            waitingPrevious?.let(::releaseTune)
            waitingPrevious = null
            return
        }
        // During the intro this pauses the intro; the loop stays chained behind it.
        runCatching { bgmTune?.head?.takeIf { it.isPlaying }?.pause() }
    }

    override fun resumeBgm() {
        startHeld = false
        // A tune still being prepared starts by itself once it is ready.
        if (!bgmPrepared) return
        runCatching { bgmTune?.head?.takeIf { !it.isPlaying }?.start() }
    }

    override fun stopBgm() {
        cancelDuck()
        handler.removeCallbacksAndMessages(fadeToken)
        fadingTune?.let(::releaseTune)
        fadingTune = null
        waitingPrevious?.let(::releaseTune)
        waitingPrevious = null
        startHeld = false
        val tune = bgmTune ?: return
        bgmTune = null
        bgmPrepared = false
        releaseTune(tune)
    }

    override fun setBgmVolume(volume: Float) {
        // An explicit volume change wins over a duck still waiting to restore the old one, and
        // over a fade still on its way to the old one.
        cancelDuck()
        bgmVolume = volume
        endFade()
    }

    override fun duckBgm(duckedVolume: Float, restoreVolume: Float, holdMillis: Long) {
        if (bgmTune == null || !bgmPrepared) return
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
        // A tune still being prepared may not be touched; it gets its volume once ready. Both the
        // intro and the loop waiting behind it are set, so the hand-over keeps the level.
        if (!bgmPrepared) return
        bgmTune?.setVolume(volume)
    }

    override fun release() {
        stopBgm()
        runCatching { soundPool?.release() }
        soundPool = null
    }

    private companion object {
        const val TAG = "AudioManager"

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
