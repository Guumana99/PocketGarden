package com.studioguu.miniaturegarden.ui.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.pressScale

/** Room kept clear round the frame, and under it for the shutter. */
val CAMERA_MARGIN: Dp = 20.dp
val CAMERA_RESERVED_BOTTOM: Dp = 150.dp

/**
 * Everything camera mode puts on screen: a viewfinder frame over the garden, a shutter button
 * and a way back. Nothing else of the game's interface is shown while it is up.
 *
 * What lies outside the frame is dimmed a little, so that it is plain what the picture will take
 * in; the garden underneath can still be dragged and pinched into place.
 *
 * @param shots goes up by one with every photograph taken; each change plays the shutter's flash.
 * @param onShutter take the photograph.
 * @param onClose leave camera mode.
 */
@Composable
fun CameraOverlay(
    shots: Int,
    onShutter: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(shots) {
        if (shots == 0) return@LaunchedEffect
        flash.snapTo(0.85f)
        flash.animateTo(0f, tween(320))
    }
    Box(modifier = modifier.fillMaxSize()) {
        // The frame and the dimming round it. A Canvas takes no touches, so the garden under it
        // stays free to move.
        Canvas(Modifier.fillMaxSize()) {
            val frame = CameraFrame.rect(size.width, size.height, CAMERA_MARGIN.toPx(), CAMERA_RESERVED_BOTTOM.toPx())
            drawOutside(frame, Color.Black.copy(alpha = 0.32f))
            drawCorners(frame, length = 26.dp.toPx(), stroke = 3.dp.toPx())
        }
        // The flash covers the whole screen for a moment when the shutter goes.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 26.dp),
        ) {
            // The way back, bottom left.
            val backSource = remember { MutableInteractionSource() }
            Text(
                text = stringResource(R.string.camera_back),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .pressScale(backSource)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable(interactionSource = backSource, indication = null, onClick = onClose)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
            // The shutter: a white disc in a ring, in the middle.
            val shutterSource = remember { MutableInteractionSource() }
            val shutterLabel = stringResource(R.string.camera_shutter)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(76.dp)
                    .pressScale(shutterSource)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(Color.White, CircleShape)
                    .clickable(interactionSource = shutterSource, indication = null, onClick = onShutter)
                    .semantics { contentDescription = shutterLabel },
            )
        }
    }
}

/** Fills everything but [frame]. */
private fun DrawScope.drawOutside(frame: FrameRect, color: Color) {
    drawRect(color, Offset.Zero, Size(size.width, frame.top))
    drawRect(color, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
    drawRect(color, Offset(0f, frame.top), Size(frame.left, frame.height))
    drawRect(color, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
}

/** The four corner marks of a viewfinder, just outside [frame] so that they are not in the picture. */
private fun DrawScope.drawCorners(frame: FrameRect, length: Float, stroke: Float) {
    val out = stroke / 2f
    val corners = listOf(
        Triple(Offset(frame.left - out, frame.top - out), 1f, 1f),
        Triple(Offset(frame.right + out, frame.top - out), -1f, 1f),
        Triple(Offset(frame.right + out, frame.bottom + out), -1f, -1f),
        Triple(Offset(frame.left - out, frame.bottom + out), 1f, -1f),
    )
    for ((corner, dx, dy) in corners) {
        drawLine(Color.White, corner, corner + Offset(length * dx, 0f), stroke, StrokeCap.Round)
        drawLine(Color.White, corner, corner + Offset(0f, length * dy), stroke, StrokeCap.Round)
    }
}
