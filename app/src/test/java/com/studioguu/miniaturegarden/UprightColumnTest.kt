package com.studioguu.miniaturegarden

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android 16 ignores the portrait lock on wide windows: the game then keeps a phone-shaped column. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UprightColumnTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a landscape tablet window gets a phone-shaped column in the middle`() {
        compose.setContent { UprightColumn { Box(Modifier.fillMaxSize().testTag("c")) } }
        val b = compose.onNodeWithTag("c").getUnclippedBoundsInRoot()
        assertEquals(800.dp.value, b.height.value, 1f)
        assertTrue(b.width.value < 400f)
        assertEquals((1280f - b.width.value) / 2f, b.left.value, 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a phone window is filled as it is`() {
        compose.setContent { UprightColumn { Box(Modifier.fillMaxSize().testTag("c")) } }
        val b = compose.onNodeWithTag("c").getUnclippedBoundsInRoot()
        assertEquals(411f, b.width.value, 1f)
    }
}
