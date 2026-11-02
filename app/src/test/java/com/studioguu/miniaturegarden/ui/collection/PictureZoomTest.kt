package com.studioguu.miniaturegarden.ui.collection

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The magnifying glass on the encyclopedia's pictures. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
class PictureZoomTest {

    @get:Rule
    val compose = createComposeRule()

    private val frame = Size(300f, 600f)

    @Test
    fun `it goes from the size it was to four times that, and no further either way`() {
        assertEquals(4f, PictureZoom.MAX_SCALE, 0f)
        assertEquals(4f, PictureZoom.scaleAfter(3f, 2f), 0f)
        assertEquals(1f, PictureZoom.scaleAfter(1.5f, 0.25f), 0f)
        assertEquals(3f, PictureZoom.scaleAfter(2f, 1.5f), 0f)
    }

    @Test
    fun `it can be moved as far as its edges and no further, so the frame stays full`() {
        assertEquals(Offset.Zero, PictureZoom.clamp(Offset(50f, 50f), 1f, frame))
        assertEquals(Offset(150f, 300f), PictureZoom.clamp(Offset(1_000f, 1_000f), 2f, frame))
        assertEquals(Offset(-150f, -300f), PictureZoom.clamp(Offset(-1_000f, -1_000f), 2f, frame))
        assertEquals(Offset(20f, -30f), PictureZoom.clamp(Offset(20f, -30f), 2f, frame))
    }

    @Test
    fun `what is under the fingers stays under them as it is pinched`() {
        val centroid = Offset(40f, -20f)
        val pan = PictureZoom.panAfter(Offset.Zero, Offset.Zero, centroid, 1f, 2f, frame)
        // The point under the fingers, (40, -20) from the middle at 1x, is at pan + 2 x that at 2x.
        assertEquals(centroid, pan + centroid * 2f)
    }

    @Test
    fun `the glass takes a finger though it looks small, and toggles the picture`() {
        var zoomed by mutableStateOf(false)
        compose.setContent {
            GardenTheme {
                Box(Modifier.size(200.dp)) {
                    ZoomablePicture(zoomed, onUnzoom = { zoomed = false }) { Text("picture") }
                    Magnifier(zoomed, { zoomed = !zoomed }, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        compose.onNodeWithTag(ZOOMED_PICTURE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(MAGNIFIER_TAG).assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp).performClick()
        compose.waitForIdle()
        assertTrue(zoomed)
        compose.onNodeWithTag(ZOOMED_PICTURE_TAG).assertExists()
        val app = RuntimeEnvironment.getApplication()
        compose.onNodeWithContentDescription(app.getString(R.string.collection_zoom_out)).performClick()
        compose.waitForIdle()
        assertFalse(zoomed)
    }

    @Test
    fun `a double tap on the picture puts it back`() {
        var zoomed by mutableStateOf(true)
        compose.setContent { GardenTheme { ZoomablePicture(zoomed, onUnzoom = { zoomed = false }, Modifier.size(200.dp)) { Text("picture") } } }
        compose.onNodeWithTag(ZOOMED_PICTURE_TAG).performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertFalse(zoomed)
    }

    @Test
    fun `the pages stand still while a picture is close, and only a filled-in page has the glass`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val book = File(main, "java/com/studioguu/miniaturegarden/ui/collection/CollectionDialog.kt").readText()
        assertTrue(book.contains("userScrollEnabled = zoomedPage == null"))
        assertTrue(book.contains("LaunchedEffect(pagerState.currentPage) { zoomedPage = null }"))
        assertTrue(book.contains("if (state == PageState.REVEALED) Magnifier("))
    }
}
