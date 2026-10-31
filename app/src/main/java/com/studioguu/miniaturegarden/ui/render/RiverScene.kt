package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.studioguu.miniaturegarden.domain.model.GridCell
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** A way across a cell, along the grid. */
enum class FlowDirection(val dx: Int, val dy: Int) {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    val opposite: FlowDirection get() = entries[(ordinal + 2) % 4]
}

/** How a river runs through a cell: in along [from], out along [to]. They differ on a bend. */
data class CellFlow(val from: FlowDirection, val to: FlowDirection) {
    val isBend: Boolean get() = from != to
}

/**
 * Which way the water runs, cell by cell. Each stream runs from one of its ends (the furthest
 * back in the garden, or for a closed ring its furthest cell back) along its own course, turning
 * with it: worked out from where the river lies, nothing about it is stored.
 */
object RiverFlow {
    fun of(cells: Set<GridCell>): Map<GridCell, CellFlow> {
        val flows = HashMap<GridCell, CellFlow>()
        fun neighbours(cell: GridCell) = FlowDirection.entries.filter { GridCell(cell.x + it.dx, cell.y + it.dy) in cells }
        val starts = cells.sortedWith(compareBy({ neighbours(it).size > 1 }, { it.x + it.y }, { it.y }))
        for (start in starts) {
            if (start in flows) continue
            val first = neighbours(start).firstOrNull() ?: FlowDirection.SOUTH
            val queue = ArrayDeque(listOf(start to first))
            flows[start] = CellFlow(first, first)
            while (queue.isNotEmpty()) {
                val (cell, from) = queue.removeFirst()
                val onward = neighbours(cell).filter { GridCell(cell.x + it.dx, cell.y + it.dy) !in flows }
                // Straight on if it can; any other way out is a branch of its own.
                val to = onward.firstOrNull { it == from } ?: onward.firstOrNull()
                if (to != null) flows[cell] = CellFlow(from, to)
                for (way in onward) {
                    val next = GridCell(cell.x + way.dx, cell.y + way.dy)
                    flows[next] = CellFlow(way, way)
                    queue.addLast(next to way)
                }
            }
        }
        return flows
    }
}

/** What fills one quarter of a cell of river. */
enum class BankPiece {
    /** Open water. */
    NONE,

    /** Bank along the quarter's first side. */
    ALONG_FIRST,

    /** Bank along its second side. */
    ALONG_SECOND,

    /** Land on both sides: the bank bends round the inside of the cell. */
    BEND,

    /** Water on both sides and land at the corner between them: the bank bends round that corner. */
    POINT,
}

/**
 * The banks of a river, laid out cell by cell. Each cell is cut into four quarters, turned round
 * from its north-west one, and each quarter looks at the two sides it touches and the corner
 * between them. So four pieces of picture make any shape of river: straight, bend, end, and pool.
 */
object RiverBanks {
    /**
     * The piece for quarter [quarter] (0 north-west, then clockwise) of the river cell ([x], [y]).
     * Its first side is the one clockwise of its corner (north, for the north-west quarter), its
     * second the one before.
     */
    inline fun pieceAt(x: Int, y: Int, quarter: Int, isRiver: (x: Int, y: Int) -> Boolean): BankPiece {
        val first = FlowDirection.entries[quarter]
        val second = FlowDirection.entries[(quarter + 3) % 4]
        val landFirst = !isRiver(x + first.dx, y + first.dy)
        val landSecond = !isRiver(x + second.dx, y + second.dy)
        return when {
            landFirst && landSecond -> BankPiece.BEND
            landFirst -> BankPiece.ALONG_FIRST
            landSecond -> BankPiece.ALONG_SECOND
            !isRiver(x + first.dx + second.dx, y + first.dy + second.dy) -> BankPiece.POINT
            else -> BankPiece.NONE
        }
    }
}

/** The river's pictures (tools/prepare_water_art.py). */
class WaterArt(
    /** The water, one copy to [SURFACE_CELLS] cells. */
    val surface: Bitmap,
    /** Fine waves, white on clear, made to repeat. */
    val rippleA: Bitmap,
    val rippleB: Bitmap,
    /** The straight bank, one copy to a cell's side. */
    val bank: Bitmap,
    val bankBend: Bitmap,
    val bankPoint: Bitmap,
)

/**
 * One garden's river, ready to draw: the shapes are made once, when the river changes, and every
 * frame only slides the waves over them. Its cost per frame is a handful of draws (the water, two
 * layers of waves for each way the water runs, some points of light, and the banks' picture),
 * however many cells of river there are; nothing is drawn cell by cell.
 *
 * Everything is kept in units of [UNITS] to a cell, with the grid's axes, and put on the ground
 * by one matrix.
 */
class RiverScene(private val art: WaterArt, cells: Set<GridCell>) {
    private val cellList = cells.sortedWith(compareBy({ it.y }, { it.x }))
    private val water = Path()
    private val bySide = HashMap<FlowDirection, Path>()
    private val banks = Picture()

    init {
        val flows = RiverFlow.of(cells)
        fun isRiver(x: Int, y: Int) = GridCell(x, y) in cells
        for (cell in cellList) {
            val shape = cellShape(cell, ::isRiver)
            water.addPath(shape)
            val flow = flows.getValue(cell)
            if (flow.isBend) {
                // The two halves of a bend run the way each side of it runs.
                val parts = halves(cell, flow)
                side(flow.from).addPath(parts.first.apply { op(shape, Path.Op.INTERSECT) })
                side(flow.to).addPath(parts.second.apply { op(shape, Path.Op.INTERSECT) })
            } else {
                side(flow.to).addPath(shape)
            }
        }
        val canvas = banks.beginRecording(1, 1)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (cell in cellList) for (quarter in 0 until 4) drawQuarter(canvas, paint, cell, quarter, RiverBanks.pieceAt(cell.x, cell.y, quarter, ::isRiver))
        banks.endRecording()
    }

    private fun side(direction: FlowDirection) = bySide.getOrPut(direction) { Path() }

    /** A cell's water: square, but round at the corners where the bank bends. */
    private fun cellShape(cell: GridCell, isRiver: (Int, Int) -> Boolean): Path {
        val radii = FloatArray(8)
        for (quarter in 0 until 4) {
            val bent = RiverBanks.pieceAt(cell.x, cell.y, quarter, isRiver) == BankPiece.BEND
            radii[quarter * 2] = if (bent) UNITS / 2f else 0f
            radii[quarter * 2 + 1] = radii[quarter * 2]
        }
        val rect = RectF(cell.x * UNITS, cell.y * UNITS, (cell.x + 1) * UNITS, (cell.y + 1) * UNITS)
        return Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
    }

    /** The two halves of a bend, either side of the diagonal through the corner it turns round. */
    private fun halves(cell: GridCell, flow: CellFlow): Pair<Path, Path> {
        val x0 = cell.x * UNITS
        val y0 = cell.y * UNITS
        val u = UNITS
        // The side the water comes in by and the side it goes out by share one corner of the cell.
        val inner = flow.from.opposite
        val cornerX = x0 + if (inner == FlowDirection.EAST || flow.to == FlowDirection.EAST) u else 0f
        val cornerY = y0 + if (inner == FlowDirection.SOUTH || flow.to == FlowDirection.SOUTH) u else 0f
        val otherX = 2 * x0 + u - cornerX
        val otherY = 2 * y0 + u - cornerY
        fun triangle(x: Float, y: Float) = Path().apply {
            moveTo(cornerX, cornerY)
            lineTo(x, y)
            lineTo(otherX, otherY)
            close()
        }
        // First the half that touches the side the water comes in by.
        val first = if (inner.dx != 0) triangle(cornerX, otherY) else triangle(otherX, cornerY)
        val second = if (inner.dx != 0) triangle(otherX, cornerY) else triangle(cornerX, otherY)
        return first to second
    }

    private fun drawQuarter(canvas: Canvas, paint: Paint, cell: GridCell, quarter: Int, piece: BankPiece) {
        if (piece == BankPiece.NONE) return
        val u = UNITS
        val half = u / 2f
        val over = OVERHANG * u
        canvas.save()
        canvas.translate(cell.x * u + half, cell.y * u + half)
        val strip = Rect(0, 0, art.bank.width, art.bank.height)
        fun straight(side: Int, firstHalf: Boolean) {
            canvas.save()
            canvas.rotate(side * 90f)
            val share = art.bank.width / 2
            strip.left = if (firstHalf) 0 else share
            strip.right = strip.left + share
            val left = if (firstHalf) -half else 0f
            val top = -half - over
            canvas.drawBitmap(art.bank, strip, RectF(left, top, left + half, top + art.bank.height * u / art.bank.width), paint)
            canvas.restore()
        }
        when (piece) {
            BankPiece.ALONG_FIRST -> straight(quarter, firstHalf = true)
            BankPiece.ALONG_SECOND -> straight((quarter + 3) % 4, firstHalf = false)
            BankPiece.BEND -> {
                canvas.rotate(quarter * 90f)
                canvas.drawBitmap(art.bankBend, null, RectF(-half - over, -half - over, 0f, 0f), paint)
            }
            BankPiece.POINT -> {
                canvas.rotate(quarter * 90f)
                val size = art.bankPoint.width * u / art.bank.width
                canvas.drawBitmap(art.bankPoint, null, RectF(-half, -half, -half + size, -half + size), paint)
            }
            BankPiece.NONE -> Unit
        }
        canvas.restore()
    }

    /** Puts the river's units on the ground of [transform], for as long as [block] draws. */
    private inline fun DrawScope.onGround(transform: IsometricTransform, block: (Canvas) -> Unit) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.concat(groundMatrix(transform))
            block(canvas)
            canvas.restore()
        }
    }

    /** The water, its waves running along the river at [clockMillis], and its points of light. */
    fun drawWater(scope: DrawScope, transform: IsometricTransform, clockMillis: Long) = with(scope) {
        val seconds = clockMillis / 1000f
        onGround(transform) { canvas ->
            shaderPaint(art.surface).let { paint ->
                paint.shader.setLocalMatrix(tileMatrix(art.surface, SURFACE_CELLS, 0f, 0f))
                canvas.drawPath(water, paint)
            }
            for ((direction, area) in bySide) {
                drawWaves(canvas, art.rippleA, area, RIPPLE_A_CELLS, RIPPLE_A_SPEED, RIPPLE_A_ALPHA, direction, seconds)
                drawWaves(canvas, art.rippleB, area, RIPPLE_B_CELLS, RIPPLE_B_SPEED, RIPPLE_B_ALPHA, direction, seconds + 40f)
            }
            drawGlints(canvas, clockMillis)
        }
    }

    private fun drawWaves(
        canvas: Canvas, picture: Bitmap, area: Path, copy: Float, speed: Float, alpha: Int, direction: FlowDirection, seconds: Float,
    ) {
        // Down the stream, swaying slowly across it. The offset comes round after one copy, so
        // that the clock can run for ever.
        val along = (seconds * speed) % copy
        val across = sin(seconds * SWAY_RATE) * SWAY_CELLS
        val shiftX = direction.dx * along + direction.dy * across
        val shiftY = direction.dy * along + direction.dx * across
        val paint = shaderPaint(picture)
        paint.alpha = alpha
        paint.shader.setLocalMatrix(tileMatrix(picture, copy, shiftX * UNITS, shiftY * UNITS))
        canvas.drawPath(area, paint)
    }

    private fun drawGlints(canvas: Canvas, clockMillis: Long) {
        val count = min(MAX_GLINTS, cellList.size / CELLS_PER_GLINT + 1)
        for (i in 0 until count) {
            val t = clockMillis + (RiverNoise.at(i, 0, 0) * GLINT_MILLIS).toLong()
            val round = (t / GLINT_MILLIS).toInt()
            val phase = (t % GLINT_MILLIS).toFloat() / GLINT_MILLIS
            val cell = cellList[(RiverNoise.at(i, round, 1) * cellList.size).toInt().coerceAtMost(cellList.size - 1)]
            val x = (cell.x + 0.25f + 0.5f * RiverNoise.at(i, round, 2)) * UNITS
            val y = (cell.y + 0.25f + 0.5f * RiverNoise.at(i, round, 3)) * UNITS
            val size = (0.05f + 0.05f * RiverNoise.at(i, round, 4)) * UNITS
            val light = sin(phase * PI).toFloat().let { it * it }
            glintPaint.alpha = (light * 200).toInt()
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(size, size)
            canvas.drawCircle(0f, 0f, 1f, glintPaint)
            canvas.restore()
        }
    }

    /** The banks, which do not move. */
    fun drawBanks(scope: DrawScope, transform: IsometricTransform) = with(scope) {
        onGround(transform) { it.drawPicture(banks) }
    }

    companion object {
        /** Units to a cell in a [RiverScene]. */
        const val UNITS = 512f

        /** How far the land edge of the bank lies outside the cell, in cells (tools/prepare_water_art.py). */
        const val OVERHANG = 0.12f

        /** How many cells one copy of a picture covers. */
        const val SURFACE_CELLS = 2f
        const val RIPPLE_A_CELLS = 1.3f
        const val RIPPLE_B_CELLS = 2.1f

        /** How fast each layer of waves slides, in cells a second: two speeds, so that they never repeat together. */
        const val RIPPLE_A_SPEED = 0.16f
        const val RIPPLE_B_SPEED = 0.09f

        private const val RIPPLE_A_ALPHA = 66
        private const val RIPPLE_B_ALPHA = 44

        /** How far the waves sway across the stream, in cells, and how slowly. */
        private const val SWAY_CELLS = 0.05f
        private const val SWAY_RATE = 0.7f

        /** Points of light at once: one for so many cells of river, and no more than [MAX_GLINTS]. */
        const val CELLS_PER_GLINT = 3
        const val MAX_GLINTS = 10
        private const val GLINT_MILLIS = 2_400L

        private val shaderPaints = HashMap<Bitmap, Paint>()
        private val localMatrix = Matrix()
        private val worldMatrix = Matrix()

        private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }

        /** A paint that repeats [picture]; made once. All drawing happens on the main thread. */
        private fun shaderPaint(picture: Bitmap): Paint = shaderPaints.getOrPut(picture) {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(picture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }

        private fun tileMatrix(picture: Bitmap, cells: Float, x: Float, y: Float): Matrix {
            val k = cells * UNITS / picture.width
            localMatrix.setScale(k, k)
            localMatrix.postTranslate(x, y)
            return localMatrix
        }

        /** From a [RiverScene]'s units to the screen. */
        private fun groundMatrix(transform: IsometricTransform): Matrix {
            val origin = transform.worldToScreen(0f, 0f)
            val alongX = transform.worldToScreen(1f, 0f) - origin
            val alongY = transform.worldToScreen(0f, 1f) - origin
            worldMatrix.setValues(
                floatArrayOf(alongX.x / UNITS, alongY.x / UNITS, origin.x, alongX.y / UNITS, alongY.y / UNITS, origin.y, 0f, 0f, 1f),
            )
            return worldMatrix
        }

        /** The waves over a pond's water, which goes nowhere: they only sway. Clipped to [area], on the screen. */
        fun drawStillWaves(scope: DrawScope, art: WaterArt, transform: IsometricTransform, area: androidx.compose.ui.graphics.Path, clockMillis: Long) {
            val seconds = clockMillis / 1000f
            scope.clipPath(area) {
                drawIntoCanvas {
                    val canvas = it.nativeCanvas
                    canvas.save()
                    canvas.concat(groundMatrix(transform))
                    val world = Path()
                    val inverse = Matrix()
                    worldMatrix.invert(inverse)
                    area.asAndroidPath().transform(inverse, world)
                    for ((picture, copy, alpha, rate) in listOf(
                        Layer(art.rippleA, RIPPLE_A_CELLS, RIPPLE_A_ALPHA, 1f),
                        Layer(art.rippleB, RIPPLE_B_CELLS, RIPPLE_B_ALPHA, -0.7f),
                    )) {
                        val paint = shaderPaint(picture)
                        paint.alpha = alpha
                        val sway = sin(seconds * SWAY_RATE * rate) * SWAY_CELLS * UNITS
                        paint.shader.setLocalMatrix(tileMatrix(picture, copy, sway, -sway))
                        canvas.drawPath(world, paint)
                    }
                    canvas.restore()
                }
            }
        }

        private data class Layer(val picture: Bitmap, val copy: Float, val alpha: Int, val rate: Float)
    }
}

/** Numbers from 0 to 1 that depend only on their arguments, for where a point of light comes. */
object RiverNoise {
    fun at(a: Int, b: Int, c: Int): Float {
        var h = a * 374_761_393 + b * 668_265_263 + c * 1_274_126_177
        h = (h xor (h ushr 13)) * 1_103_515_245
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2_147_483_648f
    }
}
