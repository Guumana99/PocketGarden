package com.studioguu.miniaturegarden.promo

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.ui.components.GardenClock
import com.studioguu.miniaturegarden.ui.components.LocalGardenClock
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject

/**
 * The promo recorder (promo build only; promo/README.md). Started from adb with a script:
 *
 *     adb shell am start -n com.studioguu.miniaturegarden.promo/com.studioguu.miniaturegarden.promo.PromoActivity \
 *         --es scenario_b64 <the script as JSON, in base64>
 *
 * It plays it one frame at a time
 * on a clock of its own and writes every frame as a 1080 x 1920 PNG to `files/promo/out/<id>/`,
 * with `cues.json` (the sound effects, when) and, last, `done`. Nothing waits on real time: however
 * slow the device, no frame is missed. It never touches the game's saved garden (it reads none and
 * writes none, and as a build of its own it has its own storage), shows no ads, no notifications,
 * no first steps and no review request: none of the game's screens are opened.
 */
class PromoActivity : ComponentActivity() {

    private var scene by mutableStateOf<Scene?>(null)
    private var art by mutableStateOf<Pair<SpriteAtlas, TerrainTextures>?>(null)
    private var nowMillis = 0L
    private var layer: GraphicsLayer? = null

    private val clock = object : GardenClock {
        override fun nowMillis(): Long = nowMillis
        override val throttles: Boolean get() = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // The script comes in the intent itself (base64 JSON), or as a file of the app's own: a
        // file adb puts in the app's folder belongs to the shell and cannot always be read.
        val root = File(getExternalFilesDir(null), "promo").apply { mkdirs() }
        File(root, "error.txt").delete()
        val text = intent.getStringExtra(EXTRA_SCENARIO_BASE64)?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
            ?: intent.getStringExtra(EXTRA_SCENARIO)?.let { name -> (File(name).takeIf { it.isAbsolute } ?: File(root, name)).takeIf { it.exists() }?.readText() }
            ?: return fail("no script: give --es scenario_b64 <base64 JSON> (or --es scenario <file>)")
        val scenario = runCatching { Scenario.parse(text) }.getOrElse { return fail("cannot read the script: $it") }
        val player = ScenarioPlayer(scenario)
        val out = File(root, "out/${scenario.id}").apply { deleteRecursively(); mkdirs() }

        // The garden's own frame clock, stepped here one frame at a time: every animation in it
        // (the wind, the visitors, the hoe's crumbs, things fading) goes by this and nothing else.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(AndroidUiDispatcher.Main + frameClock)
        lifecycleScope.launch(AndroidUiDispatcher.Main + frameClock) { recomposer.runRecomposeAndApplyChanges() }

        val composeView = ComposeView(this).apply {
            setParentCompositionContext(recomposer)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f), LocalGardenClock provides clock) {
                    val captured = rememberGraphicsLayer()
                    layer = captured
                    val current = scene
                    val loaded = art
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                captured.record { this@drawWithContent.drawContent() }
                                drawLayer(captured)
                            }
                    ) {
                        if (current != null && loaded != null) {
                            PromoScene(
                                current, player, loaded.first, loaded.second, player.map,
                                Size(WIDTH.toFloat(), HEIGHT.toFloat()), Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        // Laid out at exactly 1080 x 1920, whatever the screen; shown shrunk to fit it.
        val frame = FrameLayout(this)
        frame.addView(composeView, FrameLayout.LayoutParams(WIDTH, HEIGHT))
        setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.post {
            val fit = minOf(frame.width.toFloat() / WIDTH, frame.height.toFloat() / HEIGHT)
            composeView.pivotX = 0f
            composeView.pivotY = 0f
            composeView.scaleX = fit
            composeView.scaleY = fit
        }

        lifecycleScope.launch {
            val assets = AssetRepository(applicationContext)
            art = assets.loadAtlas() to assets.loadTerrainTextures(withWasteland = true)
            record(scenario, player, frameClock, composeView, out)
        }
    }

    private suspend fun record(scenario: Scenario, player: ScenarioPlayer, frameClock: BroadcastFrameClock, view: ComposeView, out: File) {
        val fps = scenario.fps
        val frames = (scenario.duration * fps).toInt()
        val started = System.currentTimeMillis()
        val pngs = Channel<Pair<Int, Bitmap>>(capacity = 3)
        val writer = lifecycleScope.launch(Dispatchers.IO) {
            for ((index, bitmap) in pngs) {
                File(out, "frame_%05d.png".format(index)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        for (i in -WARM_UP_FRAMES until frames) {
            val seconds = maxOf(i, 0).toDouble() / fps
            val next = player.sceneAt(seconds)
            nowMillis = next.nowMillis
            scene = next
            Snapshot.sendApplyNotifications()
            // The frame's time, counted like the device's uptime (small numbers, as the game's
            // frames are), and going on through the warm-up so that it never stands still.
            frameClock.sendFrame((FRAME_EPOCH_MILLIS * 1_000_000L) + (i + WARM_UP_FRAMES).toLong() * 1_000_000_000L / fps)
            repeat(SETTLE_YIELDS) { yield() }
            view.invalidate()
            awaitDrawn()
            awaitDrawn()
            if (i < 0) continue
            val picture = layer?.toImageBitmap()?.asAndroidBitmap() ?: continue
            pngs.send(i to picture.copy(Bitmap.Config.ARGB_8888, false))
            if (i % fps == 0) Log.i(TAG, "frame $i / $frames (${(System.currentTimeMillis() - started) / 1000}s)")
        }
        pngs.close()
        writer.join()
        withContext(Dispatchers.IO) {
            val sounds = JSONArray()
            for (s in player.allSounds()) sounds.put(JSONObject().put("t", s.at).put("name", s.name).put("action", s.action))
            val cues = JSONObject()
                .put("id", scenario.id)
                .put("title", scenario.title)
                .put("fps", fps)
                .put("frames", frames)
                .put("width", WIDTH)
                .put("height", HEIGHT)
                .put("duration", frames.toDouble() / fps)
                .put("hook_from_end", scenario.hookFromEnd)
                .put("sounds", sounds)
                .put("warnings", JSONArray(player.warnings))
            File(out, "cues.json").writeText(cues.toString(2))
            File(out, "done").writeText("$frames frames in ${(System.currentTimeMillis() - started) / 1000}s\n")
        }
        Log.i(TAG, "done: $frames frames to $out")
        finish()
    }

    /** Until the screen has been drawn once more: the next frame's callbacks, then past its drawing. */
    private suspend fun awaitDrawn() = suspendCancellableCoroutine { done ->
        Choreographer.getInstance().postFrameCallback {
            Handler(Looper.getMainLooper()).post { if (done.isActive) done.resume(Unit) }
        }
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        File(getExternalFilesDir(null), "promo").mkdirs()
        File(getExternalFilesDir(null), "promo/error.txt").writeText(message + "\n")
        finish()
    }

    companion object {
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_SCENARIO_BASE64 = "scenario_b64"
        private const val TAG = "PromoRecorder"
        const val WIDTH = 1080
        const val HEIGHT = 1920

        /** 420 dpi: a 1080 x 1920 phone of the usual kind, so the garden is the size a player sees. */
        const val DENSITY = 2.625f

        /** Frames drawn and thrown away first, while the pictures settle and the camera takes its place. */
        private const val WARM_UP_FRAMES = 20
        private const val SETTLE_YIELDS = 6
        private const val FRAME_EPOCH_MILLIS = 600_000L
    }
}
