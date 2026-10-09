package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** Unzoomed tile dimensions in pixels. */
data class IsometricMetrics(
    val tileWidth: Float,
    val tileHeight: Float,
    val heightStep: Float,
    val gridSize: Int,
) {
    /** Bounding box of the whole grid at 1x zoom. */
    val gridBounds: Size get() = Size(gridSize * tileWidth, gridSize * tileHeight)
}

/**
 * Zoom and pan state. [pan] is how far the grid's center sits from the viewport's center,
 * in screen pixels, so zooming at pan = 0 keeps the garden centered.
 */
data class GardenCamera(
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero,
) {
    /**
     * Applies one transform-gesture step: zooms around [centroid] (the point under the fingers
     * stays put), then pans, with both the zoom range and the pan bounds enforced.
     */
    fun transformedBy(
        centroid: Offset,
        panChange: Offset,
        zoomChange: Float,
        viewport: Size,
        metrics: IsometricMetrics,
    ): GardenCamera {
        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        val factor = newScale / scale
        val centroidFromCenter = centroid - viewport.center()
        val newPan = centroidFromCenter + (pan - centroidFromCenter) * factor + panChange
        return GardenCamera(newScale, newPan).clamped(metrics)
    }

    /**
     * Limits [pan] so the viewport's center never leaves the grid's bounding box. That keeps
     * part of the garden on screen at every zoom level without locking panning when it fits.
     */
    fun clamped(metrics: IsometricMetrics): GardenCamera {
        val maxX = metrics.gridBounds.width * scale / 2f
        val maxY = metrics.gridBounds.height * scale / 2f
        val clampedPan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
        return if (clampedPan == pan) this else copy(pan = clampedPan)
    }

    fun toTransform(metrics: IsometricMetrics, viewport: Size): IsometricTransform {
        val tileHeight = metrics.tileHeight * scale
        return IsometricTransform(
            tileWidth = metrics.tileWidth * scale,
            tileHeight = tileHeight,
            heightStep = metrics.heightStep * scale,
            offsetX = viewport.width / 2f + pan.x,
            // World (0, 0) is the grid's top corner, half the grid's height above its center.
            offsetY = viewport.height / 2f + pan.y - metrics.gridSize * tileHeight / 2f,
        )
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f

        /** Starts zoomed out far enough to show the whole grid, as far as the zoom range allows. */
        fun fitting(viewport: Size, metrics: IsometricMetrics): GardenCamera {
            val bounds = metrics.gridBounds
            if (viewport.width <= 0f || viewport.height <= 0f) return GardenCamera()
            val fit = minOf(viewport.width / bounds.width, viewport.height / bounds.height)
            return GardenCamera(scale = fit.coerceIn(MIN_SCALE, 1f))
        }
    }
}

private fun Size.center(): Offset = Offset(width / 2f, height / 2f)
