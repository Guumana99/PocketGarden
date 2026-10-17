package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
import com.studioguu.miniaturegarden.domain.model.LetterTip
import com.studioguu.miniaturegarden.domain.model.PostOffice
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.UserProfile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The post as an archive: letters kept, read one at a time, and the advice and shop news it brings. */
class PostTest {

    private val t0 = 1_700_000_000_000L
    private val interval = PostOffice.TIP_INTERVAL_MILLIS
    private val welcome = Letter.arrived(Season.SPRING, t0)

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings: String = main.resolve("res/values/strings.xml").readText()
    private fun source(path: String): String = main.resolve("java/com/studioguu/miniaturegarden/$path").readText()
    private fun string(name: String): String =
        Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(strings)!!.groupValues[1]

    // --- Advice ---

    @Test
    fun `the first piece of advice comes a while after the garden's first letter`() {
        assertNull(PostOffice.nextTip(listOf(welcome), Season.SPRING, t0 + interval - 1))
        val tip = PostOffice.nextTip(listOf(welcome), Season.SPRING, t0 + interval)
        assertEquals(Letter.tip(LetterTip.WATERING, Season.SPRING, t0 + interval), tip)
        assertEquals(LetterKind.TIP, tip!!.kind)
        assertEquals(LetterTip.WATERING, tip.tip)
    }

    @Test
    fun `an empty post brings no advice`() {
        assertNull(PostOffice.nextTip(emptyList(), Season.SPRING, t0 + 100 * interval))
    }

    @Test
    fun `advice comes in order, each once, one interval after the last`() {
        val letters = mutableListOf(welcome)
        var now = t0
        val sent = mutableListOf<LetterTip>()
        repeat(LetterTip.entries.size + 2) {
            now += interval
            PostOffice.nextTip(letters, Season.WINTER, now)?.let {
                letters += it
                sent += it.tip!!
            }
        }
        assertEquals(LetterTip.entries, sent)
        // Everything has been said: nothing more comes.
        assertNull(PostOffice.nextTip(letters, Season.WINTER, now + 10 * interval))
        // Not before the interval since the last one.
        val two = listOf(welcome, Letter.tip(LetterTip.WATERING, Season.SPRING, t0 + interval))
        assertNull(PostOffice.nextTip(two, Season.SPRING, t0 + 2 * interval - 1))
        assertEquals(LetterTip.RAIN, PostOffice.nextTip(two, Season.SPRING, t0 + 2 * interval)?.tip)
    }

    @Test
    fun `a long time away brings one letter of advice, not a pile`() = runBlocking {
        val repo = gardenWith(welcome)
        val post = PostLettersUseCase(repo)
        val back = t0 + 30 * interval
        assertNotNull(post.tipIfDue(back))
        assertNull(post.tipIfDue(back))
        assertNull(post.tipIfDue(back + interval - 1))
        assertEquals(LetterTip.RAIN, post.tipIfDue(back + interval)?.tip)
        assertEquals(2, repo.getLetters().count { it.kind == LetterKind.TIP })
    }

    @Test
    fun `the greenhouse letter says crops grow even in winter, and every tip has its words`() {
        assertTrue(string("letter_tip_greenhouse_body").contains("冬でも作物が育ちます"))
        for (tip in LetterTip.entries) {
            val key = tip.name.lowercase()
            assertTrue("missing title for $tip", strings.contains("name=\"letter_tip_${key}_title\""))
            assertTrue("missing body for $tip", strings.contains("name=\"letter_tip_${key}_body\""))
        }
    }

    // --- New stock ---

    @Test
    fun `a level-up that puts something in the shop is told by post`() {
        val letter = PostOffice.newStock(1, 2, Season.SUMMER, t0)!!
        assertEquals(LetterKind.NEW_STOCK, letter.kind)
        assertEquals(1..2, letter.stockLevels)
        assertEquals(Season.SUMMER, letter.season)
        assertTrue(GardenItem.unlockedBetween(1, 2).isNotEmpty())
        // A level that opens nothing, or no level-up at all, sends nothing.
        assertNull(PostOffice.newStock(6, 7, Season.SUMMER, t0))
        assertNull(PostOffice.newStock(3, 3, Season.SUMMER, t0))
    }

    @Test
    fun `word of new stock is posted once`() = runBlocking {
        val repo = gardenWith(welcome)
        val post = PostLettersUseCase(repo)
        assertNotNull(post.newStock(1, 4, t0))
        assertNull(post.newStock(1, 4, t0 + 1))
        assertNull(post.newStock(6, 7, t0 + 2))
        val stock = repo.getLetters().single { it.kind == LetterKind.NEW_STOCK }
        assertEquals(1..4, stock.stockLevels)
        assertFalse(stock.read)
    }

    // --- Read and unread, and the archive ---

    @Test
    fun `opening one letter marks that one read and leaves the rest new`() = runBlocking {
        val tip = Letter.tip(LetterTip.WATERING, Season.SPRING, t0 + interval)
        val repo = gardenWith(welcome, tip)
        ReadLettersUseCase(repo).markRead(tip.letterId)
        val letters = repo.getLetters().associateBy { it.letterId }
        assertTrue(letters.getValue(tip.letterId).read)
        assertFalse(letters.getValue(welcome.letterId).read)
        // Reading again changes nothing; a letter that is not there is left alone.
        ReadLettersUseCase(repo).markRead(tip.letterId)
        ReadLettersUseCase(repo).markRead("tip:NOTHING")
        assertEquals(2, repo.getLetters().size)
    }

    @Test
    fun `letters are kept, newest first, read or not`() = runBlocking {
        val repo = gardenWith(welcome)
        val post = PostLettersUseCase(repo)
        post.tipIfDue(t0 + interval)
        post.newStock(1, 2, t0 + interval + 1)
        ReadLettersUseCase(repo).markRead(welcome.letterId)
        val letters = repo.getLetters()
        assertEquals(3, letters.size)
        assertEquals(letters.sortedByDescending { it.sentAt }, letters)
        assertEquals(welcome.letterId, letters.last().letterId)
        // Reading marks one letter by its id; nothing in the post throws letters away.
        val dao = source("data/local/dao/LetterDao.kt")
        assertTrue(dao.contains("UPDATE letters SET read = 1 WHERE letterId = :letterId"))
        assertFalse(source("ui/post/PostDialog.kt").contains("delete", ignoreCase = true))
    }

    @Test
    fun `a letter's topic goes into the database and comes back`() {
        for (letter in listOf(
            Letter.tip(LetterTip.GREENHOUSE, Season.WINTER, t0).copy(read = true),
            Letter.newStock(4, 5, Season.AUTUMN, t0),
            welcome,
        )) {
            assertEquals(letter, letter.toEntity().toDomain())
        }
        // A tip this version does not know reads as no tip, rather than as a wrong one.
        assertNull(Letter.tip(LetterTip.RAIN, Season.SPRING, t0).copy(topic = "SNOWMAN").tip)
        assertNull(welcome.stockLevels)
    }

    @Test
    fun `the move to schema 12 adds the topic, as Room expects it`() {
        val database = source("data/local/AppDatabase.kt")
        // The version itself has moved on since (see HarvestTypeTest); the step to 12 stays.
        assertTrue(database.contains("MIGRATION_11_12"))
        assertTrue(database.contains("ALTER TABLE letters ADD COLUMN topic TEXT NOT NULL DEFAULT ''"))
        assertTrue(source("di/DatabaseModule.kt").contains("MIGRATION_11_12"))
        val schemas = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val v12 = File(schemas, "com.studioguu.miniaturegarden.data.local.AppDatabase/12.json").readText()
        val createSql = Regex("\"tableName\": \"letters\",\\s*\"createSql\": \"([^\"]+)\"").find(v12)?.groupValues?.get(1)
        assertTrue(createSql, createSql!!.contains("`topic` TEXT NOT NULL DEFAULT ''"))
    }

    // --- The screen ---

    @Test
    fun `a letter is read when its page is opened, and unread ones wear a red NEW stamp`() {
        assertEquals("NEW", string("post_new"))
        val post = source("ui/post/PostDialog.kt")
        assertTrue(post.contains("LaunchedEffect(opened.letterId) { if (!opened.read) onRead(opened.letterId) }"))
        assertTrue(post.contains("if (!letter.read) NewStamp()"))
        assertTrue(post.contains("LazyColumn"))
        assertTrue(source("ui/GardenScreen.kt").contains("onRead = viewModel::markLetterRead"))
    }

    private fun gardenWith(vararg letters: Letter) = FakeGardenRepository().apply {
        profile = UserProfile(coins = 0)
        progress = GardenProgress.NEW_GARDEN
        letters.forEach { this.letters[it.letterId] = it }
    }
}
