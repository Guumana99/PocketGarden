package com.studioguu.miniaturegarden.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.InventorySort
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs against a real Preferences DataStore writing to a temporary file. */
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val jobs = ArrayList<Job>()

    private val file: File by lazy { File(folder.root, "settings.preferences_pb") }

    /** A repository on [file]. Only one DataStore may be open on a file at a time. */
    private fun openRepository(): Pair<SettingsRepositoryImpl, Job> {
        val job = Job()
        jobs += job
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        return SettingsRepositoryImpl(dataStore) to job
    }

    @After
    fun closeStores() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    // --- Model ---

    @Test
    fun `a new player starts with music at 35 percent, effects at 90 percent and everything on`() {
        val defaults = AppSettings()
        assertEquals(0.35f, defaults.bgmVolume, 0f)
        assertEquals(0.9f, defaults.soundEffectVolume, 0f)
        assertTrue(defaults.bgmEnabled)
        assertTrue(defaults.soundEffectsEnabled)
        assertTrue(defaults.notificationsEnabled)
    }

    @Test
    fun `normalizing clamps volumes and leaves the switches alone`() {
        val odd = AppSettings(bgmEnabled = false, bgmVolume = 1.7f, soundEffectVolume = -0.2f, notificationsEnabled = false)
        assertEquals(odd.copy(bgmVolume = 1f, soundEffectVolume = 0f), odd.normalized())
        assertEquals(
            AppSettings(),
            AppSettings(bgmVolume = Float.NaN, soundEffectVolume = Float.NaN).normalized(),
        )
        val fine = AppSettings(bgmVolume = 0f, soundEffectVolume = 1f)
        assertEquals(fine, fine.normalized())
    }

    // --- Reading and writing ---

    @Test
    fun `nothing saved yet reads as the defaults`() = runBlocking {
        val (repository, _) = openRepository()
        assertEquals(AppSettings(), repository.settings.first())
        assertFalse(file.exists())
    }

    @Test
    fun `every setting can be written and read back`() = runBlocking {
        val (repository, _) = openRepository()
        val chosen = AppSettings(
            bgmEnabled = false,
            bgmVolume = 0.15f,
            soundEffectsEnabled = false,
            soundEffectVolume = 0.65f,
            notificationsEnabled = false,
            notificationInviteShown = true,
        )

        repository.update { chosen }

        assertEquals(chosen, repository.settings.first())
    }

    @Test
    fun `an update changes only what it touches`() = runBlocking {
        val (repository, _) = openRepository()

        repository.update { it.copy(bgmVolume = 0.5f) }
        repository.update { it.copy(soundEffectsEnabled = false) }

        assertEquals(
            AppSettings(bgmVolume = 0.5f, soundEffectsEnabled = false),
            repository.settings.first(),
        )
    }

    @Test
    fun `muting keeps the chosen volume for when sound comes back`() = runBlocking {
        val (repository, _) = openRepository()
        repository.update { it.copy(bgmVolume = 0.8f) }

        repository.update { it.copy(bgmEnabled = false) }
        assertEquals(0.8f, repository.settings.first().bgmVolume, 0f)

        repository.update { it.copy(bgmEnabled = true) }
        assertEquals(AppSettings(bgmVolume = 0.8f), repository.settings.first())
    }

    @Test
    fun `volumes are clamped before they are stored`() = runBlocking {
        val (repository, _) = openRepository()

        repository.update { it.copy(bgmVolume = 5f, soundEffectVolume = -1f) }

        val saved = repository.settings.first()
        assertEquals(1f, saved.bgmVolume, 0f)
        assertEquals(0f, saved.soundEffectVolume, 0f)
    }

    @Test
    fun `many quick updates all land`() = runBlocking {
        val (repository, _) = openRepository()

        // A slider being dragged: each step builds on the one before.
        repeat(20) { repository.update { it.copy(bgmVolume = it.bgmVolume + 0.01f) } }

        assertEquals(AppSettings.DEFAULT_BGM_VOLUME + 0.20f, repository.settings.first().bgmVolume, 1e-4f)
    }

    @Test
    fun `observers are told about each change`() = runBlocking {
        val (repository, _) = openRepository()
        val subscribed = CompletableDeferred<Unit>()
        val seen = mutableListOf<Float>()
        // Watches until the last value arrives. A busy machine may run the watcher late, so it is
        // only asked to have its first value before the changes are made, and given time to end.
        val watcher = async(Dispatchers.IO) {
            withTimeout(WATCH_TIMEOUT_MILLIS) {
                repository.settings
                    .map { it.soundEffectVolume }
                    .onEach { synchronized(seen) { seen += it } }
                    .onEach { subscribed.complete(Unit) }
                    .first { it == 0.1f }
            }
        }
        subscribed.await()

        repository.update { it.copy(soundEffectVolume = 0.4f) }
        repository.update { it.copy(soundEffectVolume = 0.1f) }

        // It may miss the value in between if it is slow, but never the last one, and it never
        // goes back to an older one.
        assertEquals(0.1f, watcher.await(), 0f)
        val values = synchronized(seen) { seen.toList() }
        assertEquals(0.9f, values.first(), 0f)
        val order = listOf(0.9f, 0.4f, 0.1f)
        val places = values.map { order.indexOf(it) }
        assertTrue("seen out of order: $values", places.all { it >= 0 } && places.zipWithNext().all { (a, b) -> a <= b })
    }

    // --- Surviving a restart ---

    @Test
    fun `settings survive closing and reopening the store`() = runBlocking {
        val chosen = AppSettings(bgmEnabled = false, bgmVolume = 0.2f, soundEffectVolume = 0.55f)
        val (first, firstJob) = openRepository()
        first.update { chosen }
        firstJob.cancelAndJoin() // the app process ends

        val (second, _) = openRepository() // ... and starts again
        assertTrue(file.exists())
        assertEquals(chosen, second.settings.first())
    }

    @Test
    fun `the garden's zoom is kept across a restart, and can be forgotten`() = runBlocking {
        val view = GardenView(scale = 0.62f, panX = 31f, panY = -12.5f)
        val (first, firstJob) = openRepository()
        assertEquals(null, first.settings.first().gardenView)
        first.update { it.copy(gardenView = view) }
        firstJob.cancelAndJoin()

        val (second, _) = openRepository()
        assertEquals(view, second.settings.first().gardenView)
        // Other settings are left as they were.
        assertEquals(AppSettings().bgmVolume, second.settings.first().bgmVolume)

        second.update { it.copy(gardenView = null) }
        assertEquals(null, second.settings.first().gardenView)
    }

    @Test
    fun `the bag's sort order is kept across a restart`() = runBlocking {
        val (first, firstJob) = openRepository()
        assertEquals(InventorySort.ACQUIRED, first.settings.first().inventorySort)
        first.update { it.copy(inventorySort = InventorySort.SEASON) }
        firstJob.cancelAndJoin()

        val (second, _) = openRepository()
        assertEquals(InventorySort.SEASON, second.settings.first().inventorySort)
    }

    @Test
    fun `a stored zoom that is not a usable number is not used`() {
        assertFalse(GardenView(0f, 0f, 0f).isUsable)
        assertFalse(GardenView(Float.NaN, 0f, 0f).isUsable)
        assertFalse(GardenView(1f, Float.POSITIVE_INFINITY, 0f).isUsable)
        assertTrue(GardenView(0.8f, -40f, 12f).isUsable)
    }

    @Test
    fun `settings saved by a version that knew fewer keys still load, with defaults for the rest`() = runBlocking {
        // Write a file holding a single key, as an older build might have.
        val oldJob = Job().also { jobs += it }
        val oldStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + oldJob)) { file }
        oldStore.edit { it[floatPreferencesKey("bgm_volume")] = 0.6f }
        oldJob.cancelAndJoin()

        val (repository, _) = openRepository()

        assertEquals(AppSettings(bgmVolume = 0.6f), repository.settings.first())
    }

    private companion object {
        const val WATCH_TIMEOUT_MILLIS = 10_000L
    }
}
