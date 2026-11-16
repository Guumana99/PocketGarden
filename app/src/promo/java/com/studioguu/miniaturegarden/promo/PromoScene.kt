package com.studioguu.miniaturegarden.promo

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.ui.components.BareGround
import com.studioguu.miniaturegarden.ui.components.GardenCanvas
import com.studioguu.miniaturegarden.ui.effects.GardenBackdrop
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import com.studioguu.miniaturegarden.ui.render.IsometricMetrics
import com.studioguu.miniaturegarden.ui.render.IsometricTransform

/**
 * One frame of the recording: the sky behind, the garden as the game draws it, and over it what
 * only a recording has (the finger's rings, the day). No buttons.
 */
@Composable
fun PromoScene(
    scene: Scene,
    player: ScenarioPlayer,
    atlas: SpriteAtlas,
    terrain: TerrainTextures,
    map: GardenMap,
    viewport: Size,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val view = PromoCamera.view(scene.zoom, scene.center, map.size, viewport, density, scene.lift)
    Box(modifier.fillMaxSize()) {
        if (scene.backdrop) GardenBackdrop(scene.weather, Modifier.fillMaxSize(), season = scene.season, hourOfDay = scene.hour)
        GardenCanvas(
            gridObjects = scene.objects,
            selectedTile = null,
            onTileTapped = {},
            atlas = atlas,
            terrain = terrain,
            gardenMap = map,
            showGrid = false,
            weather = scene.weather,
            rainGlint = { scene.glint },
            visitors = scene.visitors,
            savedView = view,
            season = scene.season,
            bareGround = BareGround.WASTELAND,
            brokenCells = scene.broken,
            hourOfDay = scene.hour,
            lightByTimeOfDay = true,
            visitorDirections = player::directionOf,
            modifier = Modifier.fillMaxSize(),
        )
        val transform = remember(view, viewport, map.size, density) { PromoCamera.transform(view, map.size, viewport, density) }
        Canvas(Modifier.fillMaxSize()) {
            for ((cell, p) in scene.rings) {
                val centre = transform.tileCenter(cell.x, cell.y)
                // A finger coming down: a filled dot that opens into a ring and fades.
                val alpha = (1f - p).coerceIn(0f, 1f)
                val radius = transform.tileWidth * (0.16f + 0.22f * p)
                drawCircle(Color.White.copy(alpha = 0.35f * alpha), radius * 0.7f, centre)
                drawCircle(Color.White.copy(alpha = 0.95f * alpha), radius, centre, style = Stroke(width = 4.dp.toPx()))
                drawCircle(Color(0x55000000).copy(alpha = 0.33f * alpha), radius + 2.dp.toPx(), centre, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        // A harvest's coins, as the game floats them up from the middle of the screen
        // (FloatingEffects.FloatingEffectText: the same words, look, rise and fade).
        scene.coins?.let { coins ->
            GardenTheme {
                val t = LinearOutSlowInEasing.transform(scene.coinsProgress)
                Text(
                    text = stringResource(R.string.effect_harvest, coins),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationY = -(96.dp.toPx() * 0.8f * t)
                            alpha = 1f - ((t - 0.72f) / (1f - 0.72f)).coerceIn(0f, 1f)
                        }
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        scene.dayLabel?.let { label ->
            // A board in the app's own colours (parchment, dark ink), not the captions' look: the
            // first line large, the second (after a line break) smaller under it. When only the
            // second line changes, it cross-fades.
            val first = label.substringBefore(NEWLINE)
            val second = label.substringAfter(NEWLINE, "").takeIf { it.isNotBlank() }
            val before = scene.dayLabelBefore?.substringAfter(NEWLINE, "")?.takeIf { it.isNotBlank() }
            val board = RoundedCornerShape(14.dp)
            // Below the very top (TikTok's own row) and clear of its right-hand buttons.
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 112.dp)
                    // Full size at first and when its second line changes; otherwise 70 %, a little
                    // faint (0.75) and drawn into the corner, so as not to take the eye.
                    .graphicsLayer {
                        val full = scene.dayLabelFull
                        val scale = 0.7f + 0.3f * full
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = -12.dp.toPx() * (1f - full)
                        translationY = -12.dp.toPx() * (1f - full)
                        alpha = scene.dayLabelAlpha * (0.75f + 0.25f * full) * scene.dayLabelDim
                    }
                    .shadow(6.dp, board)
                    .background(GardenColors.Parchment, board)
                    .border(2.dp, GardenColors.WoodLight, board)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(first, style = TextStyle(color = GardenColors.Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold))
                if (second != null || before != null) {
                    Box {
                        before?.let {
                            Text(it, Modifier.graphicsLayer { alpha = 1f - scene.dayLabelSwap }, style = TextStyle(color = GardenColors.InkSoft, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        }
                        second?.let {
                            Text(it, Modifier.graphicsLayer { alpha = scene.dayLabelSwap }, style = TextStyle(color = GardenColors.InkSoft, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

/** The camera of a script, as the garden's own ([GardenCamera]) takes it. */
object PromoCamera {
    const val TILE_WIDTH_DP = 128f
    private const val TILE_HEIGHT_DP = 64f
    private const val HEIGHT_STEP_DP = 16f

    fun metrics(size: Int, density: Density) = with(density) {
        IsometricMetrics(TILE_WIDTH_DP.dp.toPx(), TILE_HEIGHT_DP.dp.toPx(), HEIGHT_STEP_DP.dp.toPx(), size)
    }

    /**
     * [zoom] times the zoom that fits the whole garden (no closer than the game's own camera comes),
     * with the point [center] (in cells), [lift] tile widths up, in the middle.
     */
    fun view(zoom: Double, center: Pair<Double, Double>, size: Int, viewport: Size, density: Density, lift: Double = 0.0): GardenView {
        val metrics = metrics(size, density)
        val scale = (GardenCamera.fitting(viewport, metrics).scale * zoom).toFloat().coerceAtMost(GardenCamera.MAX_SCALE)
        val (cx, cy) = center
        val panX = -((cx - cy) * metrics.tileWidth * scale / 2).toFloat()
        val panY = (-(((cx + cy) - size) * metrics.tileHeight * scale / 2) + lift * metrics.tileWidth * scale).toFloat()
        return GardenView(scale, panX, panY)
    }

    fun transform(view: GardenView, size: Int, viewport: Size, density: Density): IsometricTransform {
        val metrics = metrics(size, density)
        return GardenCamera.restored(view.scale, Offset(view.panX, view.panY), viewport, metrics).toTransform(metrics, viewport)
    }
}

private const val NEWLINE = '\n'
