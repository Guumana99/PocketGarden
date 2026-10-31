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
         * The untilled ground every cell starts as (`res/drawable/soil_wasteland.png`): pale,
         * sun-baked earth split by cracks, a slab on white (#57; measured by
         * tools/prepare_drawable_art.py, the white left out).
         */
        val WASTELAND = GroundSlab(anchorX = 0.498f, anchorY = 0.477f, topWidth = 0.812f)

        /** A bed of tilled soil, three furrows across it (`res/drawable/soil_tilled.png`, #57). */
        val TILLED = GroundSlab(anchorX = 0.499f, anchorY = 0.457f, topWidth = 0.979f)

        /**
         * Ground broken up by the hoe, not yet a bed: dark, crumbly, turned earth without furrows
         * (`res/drawable/soil_revived.png`, #57; the step between wasteland and a bed since #61).
         */
        val REVIVED = GroundSlab(anchorX = 0.502f, anchorY = 0.478f, topWidth = 0.988f)


        /** The pond (`res/drawable/pond.png`): its ring of stones is the face. */
        val POND = GroundSlab(anchorX = 0.502f, anchorY = 0.502f, topWidth = 0.962f)
    }
}
