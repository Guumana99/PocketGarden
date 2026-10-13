package com.studioguu.miniaturegarden.ui.share

import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle on screen, in pixels: the part of the garden a photograph takes in. */
data class FrameRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * The viewfinder of camera mode: where on the screen the frame sits. What is inside it is what
 * the photograph shows; the picture is cut to it.
 */
object CameraFrame {
    /** Width to height of a photograph: 4 : 5, the tallest shape that timelines show uncropped. */
    const val ASPECT = 4f / 5f

    /**
     * The largest frame of that shape that fits on a screen [width] by [height], keeping
     * [margin] clear at the sides and the top and [reservedBottom] clear at the bottom for the
     * shutter button. It is centred in the room that leaves.
     */
    fun rect(width: Float, height: Float, margin: Float, reservedBottom: Float): FrameRect {
        val roomWidth = (width - 2 * margin).coerceAtLeast(1f)
        val roomHeight = (height - margin - reservedBottom).coerceAtLeast(1f)
        val frameWidth = min(roomWidth, roomHeight * ASPECT)
        val frameHeight = frameWidth / ASPECT
        val left = (width - frameWidth) / 2f
        val top = margin + (roomHeight - frameHeight) / 2f
        return FrameRect(left, top, left + frameWidth, top + frameHeight)
    }

    /**
     * The same frame as whole pixels of a picture [imageWidth] by [imageHeight], never reaching
     * outside it: left, top, width, height.
     */
    fun cropBox(frame: FrameRect, imageWidth: Int, imageHeight: Int): IntArray {
        val left = frame.left.roundToInt().coerceIn(0, (imageWidth - 1).coerceAtLeast(0))
        val top = frame.top.roundToInt().coerceIn(0, (imageHeight - 1).coerceAtLeast(0))
        val width = frame.width.roundToInt().coerceIn(1, (imageWidth - left).coerceAtLeast(1))
        val height = frame.height.roundToInt().coerceIn(1, (imageHeight - top).coerceAtLeast(1))
        return intArrayOf(left, top, width, height)
    }
}

/** Cuts [image] down to [frame]. */
fun cropToFrame(image: Bitmap, frame: FrameRect): Bitmap {
    // A picture captured from the screen may live in graphics memory, which cannot be cut up.
    val source = if (image.config == Bitmap.Config.HARDWARE) image.copy(Bitmap.Config.ARGB_8888, false) else image
    val (left, top, width, height) = CameraFrame.cropBox(frame, source.width, source.height)
    return Bitmap.createBitmap(source, left, top, width, height)
}
