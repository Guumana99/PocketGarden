package com.studioguu.miniaturegarden.promo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
    val view = PromoCamera.view(scene.zoom, scene.center, map.size, viewport, density)
    Box(modifier.fillMaxSize()) {
        GardenBackdrop(scene.weather, Modifier.fillMaxSize(), season = scene.season, hourOfDay = scene.hour)
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
        scene.dayLabel?.let { label ->
            Text(
                text = label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color(0xCC3E2A14), Offset(0f, 3f), blurRadius = 10f),
                ),
                // Below the very top (TikTok's own row) and clear of its right-hand buttons.
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 120.dp)
                    .graphicsLayer { alpha = scene.dayLabelAlpha },
            )
        }
    }
}

/** The camera of a script, as the garden's own ([GardenCamera]) takes it. */
object PromoCamera {
    private const val TILE_WIDTH_DP = 128f
    private const val TILE_HEIGHT_DP = 64f
    private const val HEIGHT_STEP_DP = 16f

    fun metrics(size: Int, density: Density) = with(density) {
        IsometricMetrics(TILE_WIDTH_DP.dp.toPx(), TILE_HEIGHT_DP.dp.toPx(), HEIGHT_STEP_DP.dp.toPx(), size)
    }

    /** [zoom] times the zoom that fits the whole garden, with the point [center] (in cells) in the middle. */
    fun view(zoom: Double, center: Pair<Double, Double>, size: Int, viewport: Size, density: Density): GardenView {
        val metrics = metrics(size, density)
        val scale = (GardenCamera.fitting(viewport, metrics).scale * zoom).toFloat()
        val (cx, cy) = center
        val panX = -((cx - cy) * metrics.tileWidth * scale / 2).toFloat()
        val panY = -(((cx + cy) - size) * metrics.tileHeight * scale / 2).toFloat()
        return GardenView(scale, panX, panY)
    }

    fun transform(view: GardenView, size: Int, viewport: Size, density: Density): IsometricTransform {
        val metrics = metrics(size, density)
        return GardenCamera.restored(view.scale, Offset(view.panX, view.panY), viewport, metrics).toTransform(metrics, viewport)
    }
}
