package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/ASSET_LEDGER.md is the one place that says which pictures are final, which are stand-ins
 * and which are still drawn in code. These checks hold it to what is actually bundled, so the
 * ledger cannot quietly drift away from the atlas, res/ and the game's own lists.
 */
class AssetLedgerTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private val ledger: String by lazy { File(main, "../../../docs/ASSET_LEDGER.md").readText() }

    private val atlasIds: Set<String> by lazy {
        AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()).assets.map { it.assetId }.toSet()
    }

    /** The visitors' own atlas (`creatures_manifest.json`), empty while none is bundled. */
    private val creatureAtlasIds: Set<String> by lazy {
        val file = File(main, "assets/atlas/creatures_manifest.json")
        if (!file.exists()) emptySet() else AtlasManifest.parse(file.readText()).assets.map { it.assetId }.toSet()
    }

    /** The marks the ledger uses, in the order they are looked for. */
    private val marks = listOf(ADOPTED, OLD_STYLE, STAND_IN, GENERATED, CODE, NONE, STORED)

    private fun markOf(cell: String): String? = marks.firstOrNull { cell.startsWith(it) }

    private fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** One row of the crop table: name, unlock, stage 1..4, withered, icon, notes. */
    private val cropRows: Map<String, List<String>> by lazy {
        val table = ledger.substringAfter("## 作物").substringBefore("\n## ")
        table.lines()
            .filter { it.startsWith("| ") && it.contains("（`plant_") }
            .associate { row ->
                val id = Regex("（`(plant_[a-z]+)`）").find(row)!!.groupValues[1]
                id to cells(row)
            }
    }

    private fun stageCell(row: List<String>, stage: Int) = row[1 + stage]
    private fun witheredCell(row: List<String>) = row[6]
    private fun iconCell(row: List<String>) = row[7]

    @Test
    fun `every crop has exactly one row, and no row names a crop that does not exist`() {
        assertEquals(PlantSpecies.entries.map { it.objectId }.toSet(), cropRows.keys)
        for (row in cropRows.values) assertEquals(row.toString(), 9, row.size)
    }

    @Test
    fun `a crop's row has a mark in every stage it grows through and none past it`() {
        for (species in PlantSpecies.entries) {
            val row = cropRows.getValue(species.objectId)
            for (stage in 1..4) {
                val mark = markOf(stageCell(row, stage))
                assertNotNull("${species.objectId} stage $stage", mark)
                if (stage <= species.growthStages) {
                    assertTrue("${species.objectId} stage $stage needs a picture mark", mark != NONE)
                } else {
                    assertEquals("${species.objectId} has no stage $stage", NONE, mark)
                }
            }
            val withered = markOf(witheredCell(row))
            assertNotNull("${species.objectId} withered", withered)
            assertEquals("${species.objectId}: withered is ➖ only for a crop that never withers", !species.canWither, withered == NONE)
        }
    }

    @Test
    fun `a stage marked as a picture is in the atlas, and a stage drawn in code is not`() {
        for (species in PlantSpecies.entries) {
            val row = cropRows.getValue(species.objectId)
            val slots = (1..species.growthStages).map { "${species.objectId}_stage$it" to stageCell(row, it) } +
                listOfNotNull(if (species.canWither) "${species.objectId}_withered" to witheredCell(row) else null)
            for ((spriteId, cell) in slots) {
                when (markOf(cell)) {
                    ADOPTED, OLD_STYLE, STAND_IN -> assertTrue("$spriteId is marked as a picture but is not in the atlas", spriteId in atlasIds)
                    CODE -> assertTrue("$spriteId is in the atlas but the ledger says it is drawn in code", spriteId !in atlasIds)
                    else -> throw AssertionError("$spriteId has an unexpected mark: $cell")
                }
            }
        }
    }

    @Test
    fun `every crop sprite in the atlas is accounted for by a crop row`() {
        val stageOrWithered = Regex("^(plant_[a-z]+)_(stage([1-9])|withered)(_[a-z]+)?$")
        for (id in atlasIds.filter { it.startsWith("plant_") }) {
            val match = stageOrWithered.find(id)
            assertNotNull("$id does not look like a crop sprite", match)
            val row = cropRows[match!!.groupValues[1]]
            assertNotNull("$id belongs to no crop row", row)
            val cell = match.groupValues[3].toIntOrNull()?.let { stageCell(row!!, it) } ?: witheredCell(row!!)
            assertTrue("$id is in the atlas but its cell is $cell", markOf(cell) in listOf(ADOPTED, OLD_STYLE, STAND_IN))
        }
    }

    @Test
    fun `a crop's icon is marked as a picture exactly when the game shows one`() {
        // UiLabels.itemIconRes maps a seed to its picture; read as text, like the other source checks.
        val labels = File(main, "java/com/studioguu/miniaturegarden/ui/UiLabels.kt").readText()
        for (species in PlantSpecies.entries) {
            val seed = GardenItem.entries.single { it.category == ItemCategory.SEED && it.objectId == species.objectId }
            val hasPicture = labels.contains("GardenItem.${seed.name} -> R.drawable.icon_")
            val mark = markOf(iconCell(cropRows.getValue(species.objectId)))
            assertEquals("${species.objectId} icon", if (hasPicture) ADOPTED else CODE, mark)
            val file = File(main, "res/drawable-nodpi/icon_${species.objectId.removePrefix("plant_")}.png")
            if (hasPicture) assertTrue("${file.name} is missing", file.isFile)
        }
    }

    @Test
    fun `the crop totals match the table`() {
        val rows = cropRows.values
        val slots = PlantSpecies.entries.flatMap { species ->
            val row = cropRows.getValue(species.objectId)
            (1..species.growthStages).map { stageCell(row, it) } + listOfNotNull(if (species.canWither) witheredCell(row) else null)
        }.map { markOf(it) }
        val icons = rows.map { markOf(iconCell(it)) }
        val expectedSlots = "| 段階・枯れの枠（${slots.size}） | ${slots.count { it == ADOPTED }} | ${slots.count { it == OLD_STYLE }} | " +
            "${slots.count { it == STAND_IN }} | ${slots.count { it == CODE }} |"
        val expectedIcons = "| アイコン（${icons.size}） | ${icons.count { it == ADOPTED }} | — | — | ${icons.count { it == CODE }} |"
        assertTrue("expected the line: $expectedSlots", ledger.contains(expectedSlots))
        assertTrue("expected the line: $expectedIcons", ledger.contains(expectedIcons))
        assertTrue(ledger.contains("**集計（作物の絵の枠 ${slots.size}、アイコン ${icons.size}）**"))
    }

    @Test
    fun `every shop item that is not a seed has its own row`() {
        for (item in GardenItem.entries.filter { it.category != ItemCategory.SEED }) {
            assertTrue("${item.itemId} is not in the ledger", ledger.contains("（`${item.itemId}`）"))
        }
    }

    @Test
    fun `every sprite in the atlas that is not a crop is named in the ledger`() {
        for (id in atlasIds.filterNot { it.startsWith("plant_") || it.startsWith("creature_") }) {
            assertTrue("$id is in the atlas but not in the ledger", ledger.contains("`$id`"))
        }
    }

    @Test
    fun `every creature has a row, marked as a picture exactly when the atlas has one`() {
        val section = ledger.substringAfter("## 生き物").substringBefore("\n## ")
        for (creature in Creature.entries) {
            val assetId = "creature_${creature.id}"
            val row = section.lines().singleOrNull { it.startsWith("| ") && it.contains("`$assetId`") }
            assertNotNull("$assetId has no row", row)
            val expected = if (assetId in creatureAtlasIds) ADOPTED else CODE
            assertEquals(assetId, expected, markOf(cells(row!!).last()))
        }
    }

    @Test
    fun `every bundled picture and sound outside the atlas is named in the ledger`() {
        val pictures = listOf("res/drawable", "res/drawable-nodpi")
            .flatMap { File(main, it).listFiles().orEmpty().toList() }
            .filter { it.extension == "png" && !it.name.startsWith("icon_") }
        for (file in pictures) assertTrue("${file.name} is not in the ledger", ledger.contains(file.name))
        val sounds = File(main, "res/raw").listFiles().orEmpty().filter { it.extension == "mp3" }
        assertTrue(sounds.isNotEmpty())
        for (file in sounds) assertTrue("${file.nameWithoutExtension} is not in the ledger", ledger.contains("`${file.nameWithoutExtension}`"))
    }

    private companion object {
        const val ADOPTED = "✅"
        const val OLD_STYLE = "🟡"
        const val STAND_IN = "🔁"
        const val GENERATED = "⚙️"
        const val CODE = "⬜"
        const val NONE = "➖"
        const val STORED = "📦"
    }
}
