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

    // --- The encyclopedia's wording ---

    @Test
    fun `the encyclopedia's descriptions read as ordinary Japanese with kanji, not spaced-out kana`() {
        val names = Regex("<string name=\"((?:flavor|creature_flavor|hint|combo)_[a-z_]+)\"").findAll(strings).map { it.groupValues[1] }.toList()
            .filter { !it.endsWith("_effect") }
        assertTrue(names.size > 50)
        val spacedKana = Regex("[ぁ-ゖ] [ぁ-ゖァ-ヺ]")
        for (name in names + listOf("collection_hint_harvest", "collection_hint_placement", "nature_none_yet", "collection_ready_hint")) {
            val text = string(name)
            assertFalse("$name is still spaced-out kana: $text", spacedKana.containsMatchIn(text))
            assertTrue("$name has no kanji: $text", text.any { it in '一'..'鿿' } || name.startsWith("combo_"))
        }
    }

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
    fun `even a short message stays up long enough to be read, and a building's news longest`() {
        // #49: 1.6 s was too quick to read.
        assertTrue(toastMillis("🔨 建築スタート") >= 2_800L)
        assertTrue(toastMillis(string("msg_work_ready")) in 3_000L..4_500L)
        val built = com.studioguu.miniaturegarden.ui.effects.effectTiming(
            com.studioguu.miniaturegarden.ui.effects.GardenEffect.WorkDone(1, "struct_hut", com.studioguu.miniaturegarden.domain.model.WorkKind.BUILDING),
        )
        val harvest = com.studioguu.miniaturegarden.ui.effects.effectTiming(com.studioguu.miniaturegarden.ui.effects.GardenEffect.Harvest(2, coins = 10))
        // "🎉 … が完成！" is fully readable for close to three seconds, and drifts up only a little.
        assertTrue(built.durationMillis * built.visibleShare >= 2_800f)
        assertTrue(built.durationMillis in 3_000..4_000)
        assertTrue(built.riseShare < harvest.riseShare)
        assertTrue(harvest.durationMillis < built.durationMillis)
    }

    @Test
    fun `edit mode has no banner of its own - the pencil turns it off`() {
        val screen = source("ui/GardenScreen.kt")
        assertFalse(screen.contains("R.string.edit_banner"))
        assertFalse(strings.contains("name=\"edit_done\""))
        assertFalse(strings.contains("name=\"edit_banner\""))
        assertTrue(screen.contains("onClick = viewModel::toggleEditMode"))
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

    // --- UI polish (#33) ---

    @Test
    fun `the bag's cells swap in place when the tab or the order changes`() {
        val sheet = source("ui/inventory/InventoryBottomSheet.kt")
        assertFalse(sheet.contains("animateItem"))
        assertFalse(sheet.contains("animateItemPlacement"))
    }

    @Test
    fun `the book's bookmarks are short, and shrink rather than clip`() {
        assertEquals("ちけい", string("collection_tab_terrain"))
        assertEquals("たてもの", string("collection_tab_structures"))
        val book = source("ui/collection/CollectionDialog.kt")
        assertTrue(book.contains("hasVisualOverflow"))
        assertEquals(2, Regex("""TabLabel\(stringResource""").findAll(book).count())
    }

    @Test
    fun `a level-up is a board that stays until OK`() {
        assertEquals("🎉 レベルが上がりました！", string("level_up_title"))
        assertEquals("新しいアイテムが解放されました", string("level_up_unlocked"))
        assertEquals("OK", string("level_up_ok"))
        val board = source("ui/hud/LevelUpDialog.kt")
        assertTrue(board.contains("dismissOnBackPress = false"))
        assertTrue(board.contains("dismissOnClickOutside = false"))
        assertTrue(board.contains("WoodBoard("))
        // No longer a floating text or a toast that can pass by unseen.
        val viewModel = source("ui/GardenViewModel.kt")
        assertFalse(viewModel.contains("GardenEffect.LevelUp"))
        assertFalse(viewModel.contains("msg_new_items_unlocked"))
        assertTrue(source("ui/GardenScreen.kt").contains("LevelUpDialog("))
    }

    @Test
    fun `turning notifications on without the permission explains why before the system asks`() {
        assertEquals("お便りや季節のお知らせを受け取るには、通知の許可が必要です", string("notification_permission_text"))
        assertEquals("OK", string("notification_permission_ok"))
        val row = source("ui/notifications/NotificationUi.kt")
        assertTrue(row.contains("NotificationPermissionDialog("))
        // The system's question comes from the board's OK, not from the switch itself.
        val ok = row.substringAfter("onOk = {").substringBefore("onDismiss")
        assertTrue(ok.contains("permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)"))
    }
}
