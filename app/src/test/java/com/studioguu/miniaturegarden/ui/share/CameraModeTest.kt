package com.studioguu.miniaturegarden.ui.share

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraModeTest {

    // --- The viewfinder ---

    @Test
    fun `the frame is four by five and sits inside the screen with its margins`() {
        val frame = CameraFrame.rect(width = 1080f, height = 2400f, margin = 60f, reservedBottom = 450f)

        assertEquals(CameraFrame.ASPECT, frame.width / frame.height, 1e-4f)
        assertTrue(frame.left >= 60f && frame.right <= 1080f - 60f)
        assertTrue(frame.top >= 60f && frame.bottom <= 2400f - 450f)
        // On a tall phone the width is what limits it: it is as wide as the margins allow.
        assertEquals(1080f - 120f, frame.width, 1e-3f)
        assertEquals(1200f, frame.height, 1e-2f)
    }

    @Test
    fun `it is centred side to side, and up and down in the room above the shutter`() {
        val frame = CameraFrame.rect(width = 1080f, height = 2400f, margin = 60f, reservedBottom = 450f)

        assertEquals(1080f - frame.right, frame.left, 1e-3f)
        val roomTop = 60f
        val roomBottom = 2400f - 450f
        assertEquals(frame.top - roomTop, roomBottom - frame.bottom, 1e-2f)
    }

    @Test
    fun `on a short, wide screen the height is what limits it`() {
        val frame = CameraFrame.rect(width = 2000f, height = 1200f, margin = 40f, reservedBottom = 300f)

        assertEquals(CameraFrame.ASPECT, frame.width / frame.height, 1e-4f)
        assertEquals(1200f - 40f - 300f, frame.height, 1e-2f)
        assertTrue(frame.width < 2000f - 80f)
        assertEquals(2000f - frame.right, frame.left, 1e-3f)
    }

    @Test
    fun `a screen with no room at all still gives a frame of some size`() {
        val frame = CameraFrame.rect(width = 100f, height = 100f, margin = 80f, reservedBottom = 300f)
        assertTrue(frame.width > 0f && frame.height > 0f)
    }

    // --- Cutting the picture ---

    @Test
    fun `the picture is cut to the frame, in whole pixels`() {
        val frame = CameraFrame.rect(width = 1080f, height = 2400f, margin = 60f, reservedBottom = 450f)

        val box = CameraFrame.cropBox(frame, imageWidth = 1080, imageHeight = 2400)

        // 960 wide between the margins, so 1200 tall; centred in the 1890 of room below the top margin.
        assertArrayEquals(intArrayOf(60, 405, 960, 1200), box)
    }

    @Test
    fun `the cut never reaches outside the picture`() {
        val frame = FrameRect(left = -40f, top = 2300f, right = 1500f, bottom = 2600f)

        val (left, top, width, height) = CameraFrame.cropBox(frame, imageWidth = 1080, imageHeight = 2400)

        assertEquals(0, left)
        assertTrue(top in 0 until 2400)
        assertTrue(width >= 1 && left + width <= 1080)
        assertTrue(height >= 1 && top + height <= 2400)
    }

    // --- The mode itself ---

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val screen: String = main.resolve("java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()

    @Test
    fun `in camera mode none of the game's interface is put on screen`() {
        // Camera mode and the interface are the two branches of one choice.
        val camera = screen.substringAfter("if (cameraMode) {\n            CameraOverlay(").substringBefore("        } else {\n")
        val hud = screen.substringAfter("        } else {\n            TopStatusBar(").substringBefore("\n    if (showInventory)")

        for (piece in listOf("TopStatusBar(", "QuickActionBar(", "SideButton(", "ObjectStatusDialog(", "ToolBanner(", "FloatingEffectsHost(")) {
            assertFalse("$piece is shown in camera mode", camera.contains(piece))
        }
        for (piece in listOf("QuickActionBar(", "SideButton(", "ObjectStatusDialog(", "FloatingEffectsHost(", "reward_rain_button")) {
            assertTrue("$piece is missing from the normal screen", hud.contains(piece))
        }
        assertTrue(camera.contains("onClose = { cameraMode = false }"))
    }

    @Test
    fun `in camera mode the garden has no grid, no selection and no aura, and taps do nothing`() {
        assertTrue(screen.contains("showGrid = !cameraMode"))
        assertTrue(screen.contains("selectedTile = if (cameraMode) null else selectedTile"))
        assertTrue(screen.contains("auraCells = if (cameraMode) emptySet() else aura"))
        assertTrue(screen.contains("if (!cameraMode) viewModel.onTileTapped(tile)"))
    }

    @Test
    fun `the photograph is the garden cut to the frame, without the camera's own buttons`() {
        val shutter = screen.substringAfter("onShutter = {").substringBefore("onClose = {")
        assertTrue(shutter.contains("gardenLayer.toImageBitmap()"))
        assertTrue(shutter.contains("cropToFrame(image, frame)"))
        assertTrue(shutter.contains("shareGardenImage("))
        // The overlay is drawn outside the box that is recorded into the picture.
        val recorded = screen.substringAfter("gardenLayer.record").substringBefore("if (cameraMode) {\n            CameraOverlay(")
        assertTrue(recorded.contains("GardenCanvas("))
        assertFalse(recorded.contains("CameraOverlay("))
    }

    @Test
    fun `entering camera mode puts down whatever was in hand`() {
        val viewModel = main.resolve("java/com/studioguu/miniaturegarden/ui/GardenViewModel.kt").readText()
        val enter = viewModel.substringAfter("fun enterCameraMode() {").substringBefore("}")
        for (state in listOf("_selectedTile.value = null", "_placingSlotIndex.value = null", "_movingId.value = null", "_hoeActive.value = false")) {
            assertTrue(state, enter.contains(state))
        }
        assertTrue(screen.contains("viewModel.enterCameraMode()"))
    }
}
