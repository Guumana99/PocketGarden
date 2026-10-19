package com.studioguu.miniaturegarden.data.asset

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/**
 * A PNG read for tests, where neither `javax.imageio` nor a real `BitmapFactory` is at hand.
 * Only what the game's pictures are: 8 bits a channel, RGB or RGBA, not interlaced.
 */
class TestPng private constructor(val width: Int, val height: Int, private val rgba: ByteArray) {

    /** Alpha of the pixel at ([x], [y]), 0 to 255. */
    fun alpha(x: Int, y: Int): Int = rgba[(y * width + x) * 4 + 3].toInt() and 0xFF

    /** Brightness of the pixel at ([x], [y]), 0 to 255, weighted the way the eye sees it. */
    fun luminance(x: Int, y: Int): Int {
        val i = (y * width + x) * 4
        val r = rgba[i].toInt() and 0xFF
        val g = rgba[i + 1].toInt() and 0xFF
        val b = rgba[i + 2].toInt() and 0xFF
        return (r * 3 + g * 6 + b) / 10
    }

    companion object {
        fun read(file: File): TestPng {
            val input = DataInputStream(file.inputStream().buffered())
            input.use {
                val signature = ByteArray(8).also(input::readFully)
                require(signature.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) { "$file is not a PNG" }
                var width = 0
                var height = 0
                var channels = 0
                val data = ByteArrayOutputStream()
                while (true) {
                    val length = input.readInt()
                    val type = String(ByteArray(4).also(input::readFully), Charsets.US_ASCII)
                    val chunk = ByteArray(length).also(input::readFully)
                    input.readInt() // CRC
                    when (type) {
                        "IHDR" -> {
                            width = chunk.int(0)
                            height = chunk.int(4)
                            require(chunk[8].toInt() == 8 && chunk[12].toInt() == 0) { "$file: only 8-bit, non-interlaced" }
                            channels = when (chunk[9].toInt()) {
                                2 -> 3
                                6 -> 4
                                else -> error("$file: only RGB or RGBA")
                            }
                        }
                        "IDAT" -> data.write(chunk)
                        "IEND" -> break
                    }
                }
                val raw = InflaterInputStream(data.toByteArray().inputStream()).readBytes()
                return TestPng(width, height, unfilter(raw, width, height, channels))
            }
        }

        private fun ByteArray.int(at: Int): Int =
            ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
                ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

        /** Undoes each row's filter, and widens RGB to RGBA. */
        private fun unfilter(raw: ByteArray, width: Int, height: Int, channels: Int): ByteArray {
            val stride = width * channels
            val previous = IntArray(stride)
            val row = IntArray(stride)
            val out = ByteArray(width * height * 4)
            var at = 0
            for (y in 0 until height) {
                val filter = raw[at++].toInt()
                for (i in 0 until stride) {
                    val x = raw[at++].toInt() and 0xFF
                    val a = if (i >= channels) row[i - channels] else 0
                    val b = previous[i]
                    val c = if (i >= channels) previous[i - channels] else 0
                    row[i] = (x + when (filter) {
                        0 -> 0
                        1 -> a
                        2 -> b
                        3 -> (a + b) / 2
                        4 -> paeth(a, b, c)
                        else -> error("unknown PNG filter $filter")
                    }) and 0xFF
                }
                for (px in 0 until width) {
                    val o = (y * width + px) * 4
                    for (ch in 0 until 3) out[o + ch] = row[px * channels + ch].toByte()
                    out[o + 3] = if (channels == 4) row[px * channels + 3].toByte() else -1
                }
                row.copyInto(previous)
            }
            return out
        }

        private fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = abs(p - a)
            val pb = abs(p - b)
            val pc = abs(p - c)
            return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
        }
    }
}
