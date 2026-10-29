package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.UserProfile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreObjectTest {

    private val t0 = 1_700_000_000_000L

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 100)
        repeat(4) { putSlot(InventorySlot(it)) }
    }
    private val store = StoreObjectUseCase(repo)
    private val remove = RemoveObjectUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))

    private fun obj(id: String, objectId: String, x: Int = 0, y: Int = 0) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    // --- Back into the bag ---

    @Test
    fun `a thing taken off the garden goes back into the bag as its item`() = runBlocking {
        repo.put(obj("rock", "deco_rock"))

        assertEquals(ActionResult.SUCCESS, store("rock"))

        assertTrue(repo.objects.isEmpty())
        assertEquals(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1), repo.slots.getValue(0).contents())
        assertEquals(100L, repo.profile?.coins) // nothing paid, nothing refunded
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `it joins a stack of the same item before taking an empty slot`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 3))
        repo.putSlot(InventorySlot(2, GardenItem.DECO_ROCK.itemId, 5))
        repo.put(obj("rock", "deco_rock"))
        repo.put(obj("stone", GridObject.COBBLESTONE_OBJECT_ID, x = 1))

        store("rock")
        store("stone")

        assertEquals(6, repo.slots.getValue(2).amount)
        assertEquals(InventorySlot(1, GardenItem.FLOOR_COBBLESTONE.itemId, 1), repo.slots.getValue(1).contents())
        assertEquals(3, repo.slots.getValue(0).amount)
    }

    @Test
    fun `everything that is placed from an item can go back as that item`() = runBlocking {
        val placeable = GardenItem.entries.filter { it.category != ItemCategory.SEED }
        assertEquals(18, placeable.size) // with the sprinkler, the pond and the weathervane
        for (item in placeable) {
            assertEquals(item.name, item, StoreObjectUseCase.itemFor(obj("x", item.objectId)))
        }
    }

    @Test
    fun `a fence goes back as a fence whichever part it was showing`() = runBlocking {
        FencePart.entries.forEachIndexed { index, part -> repo.put(obj(part.name, part.objectId, x = index)) }

        FencePart.entries.forEach { assertEquals(ActionResult.SUCCESS, store(it.name)) }

        assertEquals(InventorySlot(0, GardenItem.STRUCT_FENCE.itemId, 3), repo.slots.getValue(0).contents())
    }

    @Test
    fun `with no room in the bag nothing happens`() = runBlocking {
        repeat(4) { repo.putSlot(InventorySlot(it, GardenItem.SEED_TULIP.itemId, InventorySlot.MAX_STACK)) }
        repo.profile = UserProfile(coins = 100, inventoryCapacity = 4)
        repo.put(obj("rock", "deco_rock"))

        assertEquals(ActionResult.INVENTORY_FULL, store("rock"))

        assertEquals(1, repo.objects.size)
    }

    @Test
    fun `a full stack of the same item does not count as room, an empty slot does`() = runBlocking {
        repo.profile = UserProfile(coins = 100, inventoryCapacity = 2)
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, InventorySlot.MAX_STACK))
        repo.put(obj("rock", "deco_rock"))

        assertEquals(ActionResult.SUCCESS, store("rock"))

        assertEquals(InventorySlot.MAX_STACK, repo.slots.getValue(0).amount)
        assertEquals(InventorySlot(1, GardenItem.DECO_ROCK.itemId, 1), repo.slots.getValue(1).contents())
    }

    @Test
    fun `plants and tilled soil cannot go back into the bag`() = runBlocking {
        repo.till(0, 0)
        repo.put(obj("tulip", PlantSpecies.TULIP.objectId))
        repo.put(obj("dead", PlantSpecies.ROSE.objectId, x = 1).copy(status = PlantStatus.WITHERED))

        assertEquals(ActionResult.NOT_RETURNABLE, store("tulip"))
        assertEquals(ActionResult.NOT_RETURNABLE, store("dead"))
        assertEquals(ActionResult.NOT_RETURNABLE, store("soil_0_0"))
        assertEquals(ActionResult.NOT_FOUND, store("nothing"))

        assertEquals(3, repo.objects.size)
        assertTrue(repo.slots.values.all { it.isEmpty })
        assertNull(StoreObjectUseCase.itemFor(obj("x", PlantSpecies.SUNFLOWER.objectId)))
    }

    // --- No experience to be had from picking things up and putting them down ---


    /** Finishes and unveils the building just placed: when it earns, since #50. */
    private suspend fun finishAndUnveil(objectId: String, at: Long = t0) {
        val site = repo.objects.values.first { it.objectId == objectId && it.isWorking }
        repo.put(site.copy(workKind = com.studioguu.miniaturegarden.domain.model.WorkKind.READY))
        UnveilBuildingUseCase(repo, RecordCollectionUseCase(repo))(site.instanceId, at)
    }

    @Test
    fun `the experience for building something is taken back when it is put away`() = runBlocking {
        repo.profile = UserProfile(coins = 0, currentXp = 80)
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1))

        place(0, 2, 2, t0)
        finishAndUnveil(GardenItem.STRUCT_WELL.objectId) // it earns when it is unveiled (#50)
        assertEquals(110L, repo.profile?.currentXp)
        assertEquals(2, repo.profile?.playerLevel)

        store(repo.objects.values.single().instanceId)
        assertEquals(80L, repo.profile?.currentXp)
        assertEquals(2, repo.profile?.playerLevel) // a level, once reached, is kept

        // Round and round changes nothing, whether it is unveiled each time or called off.
        repeat(3) {
            place(0, 2, 2, t0)
            finishAndUnveil(GardenItem.STRUCT_WELL.objectId)
            store(repo.objects.values.single().instanceId)
            place(0, 2, 2, t0)
            store(repo.objects.values.single().instanceId)
        }
        assertEquals(80L, repo.profile?.currentXp)
        assertEquals(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1), repo.slots.getValue(0).contents())
    }

    @Test
    fun `experience never goes below nothing`() {
        assertEquals(0L, UserProfile(currentXp = 20).loseXp(60).currentXp)
        assertEquals(40L, UserProfile(currentXp = 100, playerLevel = 2).loseXp(60).currentXp)
        assertEquals(2, UserProfile(currentXp = 100, playerLevel = 2).loseXp(60).playerLevel)
    }

    @Test
    fun `a thing put away can be placed again, and the page it opened stays open`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        place(0, 1, 1, t0)
        assertEquals(1, repo.collection["deco_rock"]?.count)

        store(repo.objects.values.single().instanceId)
        assertEquals(ActionResult.SUCCESS, place(0, 5, 5, t0))

        val rock = repo.objects.values.single()
        assertEquals(5 to 5, rock.gridX to rock.gridY)
        assertTrue(repo.slots.getValue(0).isEmpty)
        assertEquals(2, repo.collection["deco_rock"]?.count)
    }

    // --- Throwing away is still what it was ---

    @Test
    fun `discarding gives nothing back`() = runBlocking {
        repo.put(obj("rock", "deco_rock"))

        assertEquals(ActionResult.SUCCESS, remove("rock"))

        assertTrue(repo.objects.isEmpty())
        assertTrue(repo.slots.values.all { it.isEmpty })
    }

    // --- Wording ---

    private val strings: String =
        listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }.resolve("res/values/strings.xml").readText()

    private fun string(name: String): String =
        Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(strings)!!.groupValues[1]

    @Test
    fun `the rain reward goes by its new name everywhere`() {
        assertFalse(strings.contains("恵みの雨"))
        for (name in listOf("reward_rain_button", "reward_rain_confirm", "msg_blessed_rain_done")) {
            assertTrue(name, string(name).contains("慈雨"))
        }
        // The button is just its name: no count of how many are left, no numbers at all.
        val button = string("reward_rain_button")
        assertFalse(button.contains("%"))
        assertFalse(button.contains("本日") || button.contains("あと") || button.contains("回"))
        assertFalse(button.any { it.isDigit() })
        // And it still never says "video".
        val shownTexts = strings.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(shownTexts.contains("動画"))
    }

    @Test
    fun `the button that fills in a page names no number of coins`() {
        val label = string("collection_reveal_button")
        assertTrue(label.contains("登録"))
        assertFalse(label.contains("%"))
        assertFalse(label.contains("🪙"))
        assertFalse(label.any { it.isDigit() })
    }

    @Test
    fun `the volume settings show no percentages`() {
        assertFalse(string("settings_reset_volume").contains("%"))
        val dialog = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
            .resolve("java/com/studioguu/miniaturegarden/ui/settings/SettingsDialog.kt").readText()
        assertFalse(dialog.contains("label_percent"))
    }

    @Test
    fun `removing asks first and offers the bag or taking it away, in one word for each kind of thing`() {
        for (name in listOf("remove_title", "remove_store", "remove_discard", "remove_text_returnable")) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        assertTrue(string("remove_store").contains("持ち物に戻す"))
        // Things are taken away, crops pulled up, beds turned back (device feedback, after #75).
        assertEquals("撤去する", string("remove_discard"))
        assertEquals("抜く", string("remove_pull"))
        assertEquals("戻す", string("remove_soil"))
        assertEquals("やめる", string("remove_cancel"))
        assertEquals("枯れた%1\$sを抜きますか？", string("remove_title_withered"))
        assertEquals("抜いた株はたい肥になって、土の栄養になります", string("remove_text_withered"))
        assertEquals("1 つ前の地面に戻します。", string("remove_text_soil"))
        assertTrue(string("hint_withered").contains("抜くとたい肥になって、土の栄養になります"))
        // The word for throwing away is used nowhere in the app.
        assertFalse(strings.contains("破棄"))
    }
}
