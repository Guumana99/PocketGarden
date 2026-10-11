package com.studioguu.miniaturegarden.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.domain.model.WeatherType

/** The sky behind the garden. */
fun skyColor(weather: WeatherType): Color = when (weather) {
    WeatherType.SUNNY -> Color(0xFFDCEFF5)
    WeatherType.CLOUDY -> Color(0xFFD3DBDF)
    WeatherType.RAINY -> Color(0xFFB4C2CB)
}

private const val DROP_COUNT = 90
private val DROP_COLOR = Color(0xFFF1F8FF)

/**
 * Falling rain, drawn over whatever is behind it. It has no pointer input, so touches pass
 * through, and it only runs while it is on screen.
 *
 * Each drop's lane, speed and starting point come from its index, so the pattern is the same
 * every frame and only the time moves it.
 */
@Composable
fun RainOverlay(modifier: Modifier = Modifier) {
    val timeMillis by produceState(0L) {
        while (true) withFrameMillis { value = it }
    }
    Canvas(modifier) {
        val length = 18.dp.toPx()
        val slant = length * 0.25f
        val stroke = 1.5.dp.toPx()
        val seconds = timeMillis / 1000f
        for (i in 0 until DROP_COUNT) {
            val lane = Math.floorMod(i * 7919, 1000) / 1000f
            val start = Math.floorMod(i * 104729, 1000) / 1000f
            val speed = 0.9f + Math.floorMod(i * 31, 7) * 0.08f // screen heights per second
            val fall = (seconds * speed + start) % 1f
            val x = lane * (size.width + slant * 8) - fall * slant * 8
            val y = fall * (size.height + length) - length
            drawLine(
                color = DROP_COLOR.copy(alpha = 0.35f + Math.floorMod(i, 3) * 0.12f),
                start = Offset(x, y),
                end = Offset(x - slant, y + length),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
