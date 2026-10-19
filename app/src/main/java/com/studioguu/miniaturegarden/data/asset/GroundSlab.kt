package com.studioguu.miniaturegarden.data.asset

/**
 * Where the top face sits in a picture of one cell drawn as a slab of ground, the way
 * `soil_wasteland.png` and `pond.png` are: an isometric diamond with its sides showing below
 * it, and here and there something (a tuft, a stone) standing past its edge. All in shares of
 * the picture, so they hold whatever size it is decoded at.
 *
 * @property anchorX the middle of the top face, across the picture.
 * @property anchorY the middle of the top face, down the picture.
 * @property topWidth how wide the top face is, corner to corner, as a share of the picture's width.
 */
data class GroundSlab(val anchorX: Float, val anchorY: Float, val topWidth: Float) {
    /**
     * How much to scale a picture [imageWidth] pixels wide for its top face to be [coverage] of a
     * cell [tileWidth] pixels across.
     */
    fun scaleFor(imageWidth: Int, tileWidth: Float, coverage: Float = 1f): Float =
        tileWidth * coverage / (imageWidth * topWidth)

    companion object {
        /**
         * The untilled ground every cell starts as (`res/drawable/soil_wasteland.png`): cracked
         * dark earth, its face measured from the dark soil alone, without the dry tufts at its
         * corners.
         */
        val WASTELAND = GroundSlab(anchorX = 0.502f, anchorY = 0.519f, topWidth = 0.884f)

        /** The pond (`res/drawable/pond.png`): its ring of stones is the face. */
        val POND = GroundSlab(anchorX = 0.502f, anchorY = 0.502f, topWidth = 0.962f)
    }
}
