package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.ui.components.GardenToastState
import com.studioguu.miniaturegarden.ui.components.popScale
import com.studioguu.miniaturegarden.ui.components.toastMillis
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small things of the interface: toasts, wording, and how a building pops up. */
class PolishTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings: String = main.resolve("res/values/strings.xml").readText()
    private fun source(path: String): String = main.resolve("java/com/studioguu/miniaturegarden/$path").readText()

    private fun string(name: String): String =
        Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(strings)!!.groupValues[1]

    // --- Toasts ---

    @Test
    fun `a toast shows the latest message and each message is its own`() {
        val toast = GardenToastState()
        assertNull(toast.current)

        toast.show("おひっこし完了")
        val first = toast.current!!
        assertEquals("おひっこし完了", first.second)

        // The same words again are still a new message, so that they are shown afresh.
        toast.show("おひっこし完了")
        val second = toast.current!!
        assertFalse(first.first == second.first)

        // A newer message replaces the one on show rather than queueing behind it.
        toast.show("かばんにしまいました")
        assertEquals("かばんにしまいました", toast.current!!.second)
    }

    @Test
    fun `taking down an old toast does not take down the one that replaced it`() {
        val toast = GardenToastState()
        toast.show("one")
        val old = toast.current!!.first
        toast.show("two")

        toast.dismiss(old)
        assertEquals("two", toast.current!!.second)

        toast.dismiss(toast.current!!.first)
        assertNull(toast.current)
    }

    @Test
    fun `longer messages stay up longer, within limits`() {
        assertTrue(toastMillis("") >= GardenToastState.BASE_MILLIS)
        assertTrue(toastMillis("短い") < toastMillis("これは もう少し ながい メッセージです"))
        assertEquals(GardenToastState.MAX_MILLIS, toastMillis("あ".repeat(500)))
        assertTrue(toastMillis("🚚 おひっこし完了") in 1_500L..3_000L)
    }

    @Test
    fun `messages are shown by the garden's own toast, not the system's snackbar`() {
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("GardenToastHost("))
        assertFalse(screen.contains("Snackbar"))
    }

    // --- Layout that does not jump ---

    @Test
    fun `the panels above the tool bar swap by fading in place, not by sliding or stacking`() {
        val screen = source("ui/GardenScreen.kt")
        assertFalse(screen.contains("slideInVertically"))
        assertFalse(screen.contains("slideOutVertically"))
        // The tool bar is measured once and the panels are laid over the garden above it.
        assertTrue(screen.contains("barHeightPx"))
        assertTrue(screen.contains("padding(bottom = barHeight"))
    }

    // --- Wording ---

    @Test
    fun `the everyday messages are short and say what happened`() {
        for (name in listOf("msg_stored", "work_started_building", "work_started_demolition", "msg_revived", "msg_composted")) {
            val text = string(name)
            assertTrue("$name is too long: $text", text.length <= 14)
            assertFalse("$name still ends like a log line: $text", text.endsWith("を始めました"))
        }
        assertFalse(strings.contains(">移動しました<"))
        // Moving says nothing at all: the thing is plainly in its new place.
        assertFalse(strings.contains("msg_moved"))
        assertFalse(strings.contains(">持ち物に戻しました<"))
    }

    @Test
    fun `the rain button names the rain and nothing else`() {
        assertEquals("☔ 慈雨を降らせる", string("reward_rain_button"))
        // And the screen does not pass it any counts to show.
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("stringResource(R.string.reward_rain_button)"))
    }

    @Test
    fun `the encyclopedia does not count harvests or placements`() {
        assertFalse(strings.contains("collection_count"))
        assertFalse(strings.contains("しゅうかく　%"))
        assertFalse(strings.contains("せっち　%"))
        val book = source("ui/collection/CollectionDialog.kt")
        assertFalse(book.contains("collection_count"))
        assertFalse(book.contains(".count"))
        // The page still has its name, its text and its picture.
        assertTrue(book.contains("flavorText(entry)"))
        assertTrue(book.contains("ObjectPreview("))
    }

    @Test
    fun `a finished building asks to be tapped`() {
        assertTrue(string("msg_work_ready").contains("タップ"))
        assertTrue(string("work_ready").contains("✨"))
        assertTrue(string("effect_built").contains("完成"))
    }

    // --- Work sites ---

    @Test
    fun `a work site never draws the building inside it`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val site = canvas.substringAfter("private fun DrawScope.drawWorkSite(").substringBefore("\nprivate fun ")
        // Sheeting and scaffold only: the finished thing's own drawing is not even passed in.
        assertFalse(site.substringBefore(") {").contains("draw:"))
        assertFalse(site.contains("clipRect"))
        assertFalse(site.contains("saveLayer"))
        assertTrue(site.contains("SHEET_LEFT") && site.contains("drawScaffoldSide"))
        assertTrue(site.contains("drawReadyBubble"))
    }

    // --- The pop ---

    @Test
    fun `an unveiled building pops up past its size and settles at exactly its size`() {
        assertTrue(popScale(0f) < 0.7f)
        val samples = (0..100).map { popScale(it / 100f) }
        assertTrue(samples.max() > 1.1f)
        assertTrue(samples.max() < 1.3f)
        assertEquals(1f, popScale(1f), 1e-4f)
        assertEquals(1f, popScale(0.6f), 1e-4f)   // settled well before the confetti has fallen
        // Up, then down, with no second bounce and no jumps.
        val peak = samples.indexOf(samples.max())
        assertEquals(samples.subList(0, peak + 1).sorted(), samples.subList(0, peak + 1))
        assertEquals(samples.subList(peak, samples.size).sortedDescending(), samples.subList(peak, samples.size))
        assertTrue(samples.zipWithNext { a, b -> kotlin.math.abs(a - b) }.max() < 0.06f)
    }
}
