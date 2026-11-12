package com.studioguu.miniaturegarden.data.asset

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #fb-1111 (10): the app's pictures are lossless WebP made from PNG masters kept outside the app
 * (art/png/, tools/webp_art.py, which also checks every pixel against the master).
 */
class WebpArtTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val masters = File(main.canonicalFile.parentFile.parentFile.parentFile, "art/png")

    private fun pictures(root: File, extension: String) = listOf("res", "assets")
        .flatMap { File(root, it).walkTopDown().filter { f -> f.isFile && f.extension == extension }.toList() }
        .map { it.relativeTo(root).invariantSeparatorsPath.substringBeforeLast('.') }
        .toSortedSet()

    @Test
    fun `the app carries no PNG, and every WebP has its PNG master and the other way round`() {
        assertEquals(sortedSetOf<String>(), pictures(main, "png"))
        val webp = pictures(main, "webp")
        assertTrue(webp.size >= 90)
        assertEquals(pictures(masters, "png"), webp)
    }

    @Test
    fun `every WebP is lossless`() {
        for (file in main.walkTopDown().filter { it.isFile && it.extension == "webp" }) {
            val head = file.inputStream().use { it.readNBytes(16) }
            assertEquals(file.name, "RIFF", String(head, 0, 4, Charsets.US_ASCII))
            assertEquals(file.name, "WEBP", String(head, 8, 4, Charsets.US_ASCII))
            // VP8L is the lossless kind; VP8 or VP8X with VP8 inside would be lossy.
            assertEquals(file.name, "VP8L", String(head, 12, 4, Charsets.US_ASCII))
        }
    }

    @Test
    fun `the atlases name their pages as WebP, and the masters' manifests as PNG`() {
        for (name in listOf("assets_manifest.json", "creatures_manifest.json")) {
            val app = AtlasManifest.parse(File(main, "assets/atlas/$name").readText())
            val master = AtlasManifest.parse(File(masters, "assets/atlas/$name").readText())
            assertTrue(app.pages.all { it.file.endsWith(".webp") && File(main, "assets/atlas/${it.file}").isFile })
            assertEquals(master.pages.map { it.file.replace(".png", ".webp") }, app.pages.map { it.file })
            assertEquals(master.assets, app.assets)
        }
    }

    @Test
    fun `the tools write their pictures to the masters`() {
        val tools = File(main.canonicalFile.parentFile.parentFile.parentFile, "tools")
        for (script in listOf("prepare_app_icon.py", "prepare_creature_art.py", "prepare_drawable_art.py", "prepare_greenhouse_art.py", "prepare_water_art.py")) {
            val text = File(tools, script).readText()
            assertTrue(script, text.contains("\"art\" / \"png\"") || text.contains("art/png/assets/atlas"))
            assertTrue(script, !Regex("""= .*"app" / "src" / "main" / "(res|assets)""").containsMatchIn(text))
        }
        assertTrue(File(tools, "webp_art.py").readText().contains("exact=True"))
    }
}
