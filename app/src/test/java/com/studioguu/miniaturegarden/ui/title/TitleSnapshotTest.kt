package com.studioguu.miniaturegarden.ui.title

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1113 10: the title shows last time's garden at once, and the real one fades in over it. */
class TitleSnapshotTest {

    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun read(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")

    @Test
    fun `the kept picture is read first of all, before the artwork`() {
        val app = read("PocketGardenApplication.kt")
        val snapshot = app.indexOf("TitleSnapshot.prefetch(this")
        val art = app.indexOf("assets.prefetch(")
        assertTrue(snapshot in 0 until art)
    }

    @Test
    fun `the title shows the kept picture under the real garden, and keeps a new one once it has been seen`() {
        val screen = read("ui/title/TitleScreen.kt")
        val picture = screen.indexOf("snapshot?.let { picture ->")
        val garden = screen.indexOf("SampleGarden(\n")
        // Drawn first, so the real garden fades in over it.
        assertTrue(picture in 0 until garden)
        assertTrue(screen.contains("finishedListener = { if (it >= 1f) viewModel.snapshotCovered() }"))
        assertTrue(screen.contains("recorded.toImageBitmap()"))
        assertTrue(screen.contains("viewModel::keepSnapshot"))
        // Written whole or not at all.
        val keep = read("ui/title/TitleSnapshot.kt")
        assertTrue(keep.contains("part.renameTo(File(context.filesDir, FILE_NAME))"))
    }
}
