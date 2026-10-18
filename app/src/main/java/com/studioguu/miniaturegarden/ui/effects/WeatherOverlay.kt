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

private val DROP_COLOR = Color(0xFFF1F8FF)
private val BLESSED_COLOR = Color(0xFFFFF4D6)
private val GLINT_COLOR = Color(0xFFFFFBEA)

/**
 * Falling rain, drawn over whatever is behind it. It has no pointer input, so touches pass
 * through, and it only runs while it is on screen.
 *
 * Where each drop is comes from [RainField]: every drop falls at its own speed, from its own
 * place, after its own pause, and is a little fainter or clearer than the next (#47). The
 * blessed rain ([RainStyle.BLESSED]) is slower and warmer, and some of its drops glint.
 */
@Composable
fun RainOverlay(modifier: Modifier = Modifier, style: RainStyle = RainStyle.RAIN) {
    val timeMillis by produceState(0L) {
        while (true) withFrameMillis { value = it }
    }
    Canvas(modifier) {
        val stroke = (if (style == RainStyle.RAIN) 1.5.dp else 2.dp).toPx()
        val colour = if (style == RainStyle.RAIN) DROP_COLOR else BLESSED_COLOR
        for (drop in RainField.drops(timeMillis, size.width, size.height, style)) {
            if (drop.glint) {
                // A drop catching the light: a small bright point with a soft halo.
                val at = Offset(drop.x, drop.y)
                drawCircle(GLINT_COLOR.copy(alpha = drop.alpha * 0.35f), radius = stroke * 2.6f, center = at)
                drawCircle(GLINT_COLOR.copy(alpha = drop.alpha), radius = stroke * 0.9f, center = at)
                continue
            }
            drawLine(
                color = colour.copy(alpha = drop.alpha),
                start = Offset(drop.x, drop.y),
                end = Offset(drop.x - drop.slant * drop.length, drop.y + drop.length),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
