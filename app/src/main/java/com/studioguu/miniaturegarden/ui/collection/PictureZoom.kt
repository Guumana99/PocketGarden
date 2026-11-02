package com.studioguu.miniaturegarden.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.withTick

/**
 * How a picture of the encyclopedia is looked at closely: how far it goes and how it moves. Pure,
 * so that it can be looked at without a screen.
 *
 * The pictures are drawn afresh from the atlas, whose tiles are 512 wide, under the zoom (a
 * [graphicsLayer] is only a transform here, not a bitmap of what was on the page), so that even
 * at [MAX_SCALE] on a phone they are drawn from no less than the atlas has.
 */
object PictureZoom {
    /** How large the magnifying glass makes it, and the most a pinch can. */
    const val START_SCALE = 2f
    const val MAX_SCALE = 4f

    fun scaleAfter(scale: Float, pinch: Float): Float = (scale * pinch).coerceIn(1f, MAX_SCALE)

    /** [pan] kept to what leaves the picture filling a frame of [size] at [scale]. */
    fun clamp(pan: Offset, scale: Float, size: Size): Offset {
        val reachX = size.width * (scale - 1f) / 2f
        val reachY = size.height * (scale - 1f) / 2f
        return Offset(pan.x.coerceIn(-reachX, reachX), pan.y.coerceIn(-reachY, reachY))
    }

    /**
     * Where the picture goes when it is moved by [drag] and scaled from [from] to [to] round
     * [centroid] (from the middle of the frame): what is under the fingers stays under them.
     */
    fun panAfter(pan: Offset, drag: Offset, centroid: Offset, from: Float, to: Float, size: Size): Offset =
        clamp((pan - centroid) * (to / from) + centroid + drag, to, size)
}

/** Test tags of the magnifying glass and of the picture under it. */
const val MAGNIFIER_TAG = "picture_magnifier"
const val ZOOMED_PICTURE_TAG = "picture_zoomed"

/**
 * A picture that can be looked at closely, inside its frame: the frame's [clip][Modifier] keeps it
 * there. While [zoomed], pinching scales it, dragging moves it, and a double tap puts it back;
 * otherwise it leaves every touch to the page round it. [content] takes up the whole box.
 */
@Composable
internal fun ZoomablePicture(zoomed: Boolean, onUnzoom: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(zoomed) {
        scale = if (zoomed) PictureZoom.START_SCALE else 1f
        pan = Offset.Zero
    }
    val gestures = if (zoomed) {
        Modifier
            .testTag(ZOOMED_PICTURE_TAG)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onUnzoom() }) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, drag, pinch, _ ->
                    val size = Size(frame.width.toFloat(), frame.height.toFloat())
                    val middle = Offset(size.width / 2f, size.height / 2f)
                    val next = PictureZoom.scaleAfter(scale, pinch)
                    pan = PictureZoom.panAfter(pan, drag, centroid - middle, scale, next, size)
                    scale = next
                }
            }
    } else {
        Modifier
    }
    Box(modifier.onSizeChanged { frame = it }.then(gestures), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** The small magnifying glass in the corner of a frame: it brings the picture close, and puts it back. */
@Composable
internal fun Magnifier(zoomed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(if (zoomed) R.string.collection_zoom_out else R.string.collection_zoom_in)
    Box(
        modifier
            .size(MAGNIFIER_TOUCH)
            .clickable(role = Role.Button, onClick = withTick(onToggle))
            .semantics { contentDescription = name }
            .testTag(MAGNIFIER_TAG),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .size(MAGNIFIER_DISC)
                .background(if (zoomed) GoldLeaf else Paper.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("🔍", fontSize = 13.sp) }
    }
}

/** A finger's width, though it looks small. */
private val MAGNIFIER_TOUCH = 44.dp
private val MAGNIFIER_DISC = 26.dp
