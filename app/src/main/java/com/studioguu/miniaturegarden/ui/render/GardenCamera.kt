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
    /** How far out this camera may zoom: far enough to show the whole grid on this screen. */
    val minScale: Float = MIN_SCALE,
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
        val newScale = (scale * zoomChange).coerceIn(minScale, MAX_SCALE)
        val factor = newScale / scale
        val centroidFromCenter = centroid - viewport.center()
        val newPan = centroidFromCenter + (pan - centroidFromCenter) * factor + panChange
        return GardenCamera(newScale, newPan, minScale).clamped(metrics)
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
        /** Widest zoom on screens large enough not to need more; small screens may go further out. */
        const val MIN_SCALE = 0.5f

        /**
         * The fitted grid is drawn at this fraction of the screen. What is left over holds the
         * frame around the garden and a little air beyond that.
         */
        const val FIT_MARGIN = 0.86f
        const val MAX_SCALE = 2.5f

        /**
         * The zoom the garden is first shown at: a tile is 128 dp across at 1x, so this makes it
         * 48 dp, about what a fingertip covers. On a phone the whole of a large garden would
         * otherwise start with tiles too small to hit (a 13 x 13 garden fitted to a 411 dp
         * screen has tiles 27 dp across).
         */
        const val START_SCALE = 48f / 128f

        /**
         * Starts zoomed out so that every tile is on screen, and lets the player zoom back out
         * that far later.
         */
        fun fitting(viewport: Size, metrics: IsometricMetrics): GardenCamera {
            val bounds = metrics.gridBounds
            if (viewport.width <= 0f || viewport.height <= 0f) return GardenCamera()
            val fit = minOf(viewport.width / bounds.width, viewport.height / bounds.height) * FIT_MARGIN
            val scale = fit.coerceAtMost(1f)
            return GardenCamera(scale = scale, minScale = minOf(MIN_SCALE, scale))
        }

        /**
         * The garden's first view: centred, close enough for a tile to be finger-sized
         * ([START_SCALE]), or as [fitting] when that already shows everything with larger tiles.
         * Pinching out still goes as far as [fitting], to the whole garden.
         */
        fun starting(viewport: Size, metrics: IsometricMetrics): GardenCamera {
            val fit = fitting(viewport, metrics)
            if (fit.scale >= START_SCALE) return fit
            return fit.copy(scale = START_SCALE).clamped(metrics)
        }

        /**
         * A view kept from before (saved state, or the player's last visit) put back on this
         * screen: its zoom kept within what this screen allows, its pan within the garden.
         */
        fun restored(scale: Float, pan: Offset, viewport: Size, metrics: IsometricMetrics): GardenCamera {
            val fit = fitting(viewport, metrics)
            val usable = if (scale.isFinite() && scale > 0f) scale else fit.scale
            return GardenCamera(usable.coerceIn(fit.minScale, MAX_SCALE), pan, fit.minScale).clamped(metrics)
        }
    }
}

private fun Size.center(): Offset = Offset(width / 2f, height / 2f)
