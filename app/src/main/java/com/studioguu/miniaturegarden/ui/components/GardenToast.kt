package com.studioguu.miniaturegarden.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import kotlinx.coroutines.delay

/** How long a toast of the given length stays up: a base time, plus reading time per character. */
fun toastMillis(text: String): Long =
    (GardenToastState.BASE_MILLIS + text.length * GardenToastState.MILLIS_PER_CHARACTER)
        .coerceAtMost(GardenToastState.MAX_MILLIS)

/**
 * The one short message the garden is currently showing, if any. A new message replaces the one
 * on show at once: these are passing remarks, and a queue of them would lag behind the play.
 */
@Stable
class GardenToastState {
    /** The message on show and a number that changes with every message, even a repeated one. */
    var current by mutableStateOf<Pair<Long, String>?>(null)
        private set
    private var nextId = 0L

    fun show(text: String) {
        current = nextId++ to text
    }

    /** Takes the message with this id down, unless a newer one has replaced it already. */
    fun dismiss(id: Long) {
        if (current?.first == id) current = null
    }

    companion object {
        /** Long enough to be read at a glance, short messages included (#49: 1.6 s was too quick). */
        const val BASE_MILLIS = 2_400L
        const val MILLIS_PER_CHARACTER = 55L
        const val MAX_MILLIS = 5_000L
    }
}

/**
 * Shows [state]'s message as a soft, rounded plaque of pale wood that pops in, stays a moment and
 * fades. It is laid over the garden where it is put and takes no room in any layout, so nothing
 * moves when a message comes or goes, and it lets touches through.
 */
@Composable
fun GardenToastHost(state: GardenToastState, modifier: Modifier = Modifier) {
    val current = state.current
    // Keeps the words while the plaque fades away.
    var last by remember { mutableStateOf("") }
    if (current != null) last = current.second
    LaunchedEffect(current?.first) {
        val shown = current ?: return@LaunchedEffect
        delay(toastMillis(shown.second))
        state.dismiss(shown.first)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            val shape = RoundedCornerShape(50)
            Text(
                text = last,
                color = GardenColors.Ink,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .shadow(elevation = 10.dp, shape = shape, ambientColor = GardenColors.WoodDark, spotColor = GardenColors.WoodDark)
                    // Pale wood, a little see-through, lighter along the top like a planed board.
                    .background(
                        Brush.verticalGradient(
                            listOf(GardenColors.Cream.copy(alpha = 0.95f), GardenColors.ParchmentDark.copy(alpha = 0.92f))
                        ),
                        shape,
                    )
                    .border(1.5.dp, GardenColors.WoodLight, shape)
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            )
        }
    }
}
