package com.studioguu.miniaturegarden.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every migration, run for real, turns the exported schema of one version into the next.
 *
 * There is no SQLite on the JVM here, so each migration runs against a stand-in database that
 * only writes down the SQL it is given; that SQL is then applied to the tables of the older
 * exported schema (app/schemas) and the result compared with the newer one, the way Room checks
 * a migrated database when the app opens it. A migration that forgets a column, gets its type or
 * default wrong, or a schema that was changed without a migration, fails here instead of on a
 * player's phone.
 */
class MigrationSchemaTest {

    private val schemas: File = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        .resolve("com.studioguu.miniaturegarden.data.local.AppDatabase")

    private val migrations: List<Migration> = with(AppDatabase.Companion) {
        listOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
        )
    }

    /** A column as Room compares it: type affinity, NOT NULL, and the default when there is one. */
    private data class Column(val affinity: String, val notNull: Boolean, val default: String?)

    private data class Table(val columns: Map<String, Column>, val primaryKey: List<String>, val indices: Set<String>)

    private fun schema(version: Int): Map<String, Table> {
        val database = Json.parseToJsonElement(File(schemas, "$version.json").readText()).jsonObject.getValue("database").jsonObject
        return database.getValue("entities").jsonArray.associate { element ->
            val entity = element.jsonObject
            val columns = entity.getValue("fields").jsonArray.associate {
                val field = it.jsonObject
                field.string("columnName") to Column(field.string("affinity"), field["notNull"]?.jsonPrimitive?.boolean ?: false, field["defaultValue"]?.jsonPrimitive?.content)
            }
            val primaryKey = entity.getValue("primaryKey").jsonObject.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content }
            val indices = entity["indices"]?.jsonArray?.map { it.jsonObject.string("name") }?.toSet() ?: emptySet()
            entity.string("tableName") to Table(columns, primaryKey, indices)
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    /** The SQL [migration] runs, written down by a database that does nothing else. */
    private fun sqlOf(migration: Migration): List<String> {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            when (method.name) {
                "execSQL" -> statements += args!![0] as String
                else -> fail("${migration.startVersion}->${migration.endVersion} calls ${method.name}, which this test cannot follow")
            }
            null
        } as SupportSQLiteDatabase
        migration.migrate(database)
        return statements
    }

    private val createTable = Regex("""CREATE TABLE IF NOT EXISTS `(\w+)` \((.*)\)""", RegexOption.DOT_MATCHES_ALL)
    private val columnDefinition = Regex("""^`(\w+)` (\w+)( NOT NULL)?(?: DEFAULT (.+))?$""")
    private val primaryKey = Regex("""^PRIMARY KEY\((.*)\)$""")
    private val addColumn = Regex("""ALTER TABLE `?(\w+)`? ADD COLUMN `?(\w+)`? (\w+)( NOT NULL)?(?: DEFAULT (.+))?""")
    private val createIndex = Regex("""CREATE (?:UNIQUE )?INDEX IF NOT EXISTS `(\w+)` ON `(\w+)`""")
    private val dropTable = Regex("""DROP TABLE `?(\w+)`?""")
    private val renameTable = Regex("""ALTER TABLE `?(\w+)`? RENAME TO `?(\w+)`?""")

    /** [tables] after [sql]; only the kinds of statement the migrations use are understood. */
    private fun applied(tables: Map<String, Table>, sql: List<String>, step: String): Map<String, Table> {
        val result = tables.toMutableMap()
        for (statement in sql.map { it.trim() }) {
            createTable.matchEntire(statement)?.let { match ->
                val columns = linkedMapOf<String, Column>()
                var key = emptyList<String>()
                for (part in match.groupValues[2].split(Regex(""",\s*(?=`|PRIMARY KEY)""")).map { it.trim() }) {
                    val pk = primaryKey.matchEntire(part)
                    if (pk != null) {
                        key = pk.groupValues[1].split(",").map { it.trim().trim('`') }
                        continue
                    }
                    val column = columnDefinition.matchEntire(part) ?: error("$step: cannot read column \"$part\"")
                    columns[column.groupValues[1]] = Column(column.groupValues[2], column.groupValues[3].isNotEmpty(), column.groupValues[4].ifEmpty { null })
                }
                result[match.groupValues[1]] = Table(columns, key, emptySet())
                continue
            }
            addColumn.matchEntire(statement)?.let { match ->
                val name = match.groupValues[1]
                val table = result[name] ?: error("$step: adds a column to $name, which does not exist")
                val column = Column(match.groupValues[3], match.groupValues[4].isNotEmpty(), match.groupValues[5].ifEmpty { null })
                result[name] = table.copy(columns = table.columns + (match.groupValues[2] to column))
                continue
            }
            createIndex.find(statement)?.let { match ->
                val name = match.groupValues[2]
                val table = result.getValue(name)
                result[name] = table.copy(indices = table.indices + match.groupValues[1])
                continue
            }
            dropTable.matchEntire(statement)?.let { match ->
                result.remove(match.groupValues[1]) ?: error("$step: drops ${match.groupValues[1]}, which does not exist")
                continue
            }
            renameTable.matchEntire(statement)?.let { match ->
                result[match.groupValues[2]] = result.remove(match.groupValues[1]) ?: error("$step: renames ${match.groupValues[1]}, which does not exist")
                continue
            }
            // Data only (filling in new columns): the tables stay as they are.
            if (statement.startsWith("UPDATE") || statement.startsWith("INSERT")) continue
            fail("$step: does not understand \"$statement\"")
        }
        return result
    }

    @Test
    fun `an older save's soil starts full and plain, and its beds stay what they were`() {
        val sql = sqlOf(AppDatabase.MIGRATION_16_17)
        assertTrue(sql.contains("UPDATE grid_objects SET soilQuality = 0, nutritionLevel = 1"))
        assertTrue(sql.contains("UPDATE grid_objects SET status = 'HEALTHY' WHERE status = 'UNNOURISHED'"))
        // Nothing changes what stands in the garden: a bed keeps its id, which now reads as ridges.
        assertTrue(sql.none { it.contains("SET objectId") })
        assertTrue(sql.none { it.contains("DELETE") })
    }

    @Test
    fun `there is a migration from every version to the next, up to the current one`() {
        val current = schemas.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        assertEquals((1 until current).map { it to it + 1 }, migrations.map { it.startVersion to it.endVersion })
        val module = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
            .resolve("java/com/studioguu/miniaturegarden/di/DatabaseModule.kt").readText()
        for (migration in migrations) {
            val name = "MIGRATION_${migration.startVersion}_${migration.endVersion}"
            assertTrue("$name is not given to Room", module.contains("AppDatabase.$name"))
        }
    }

    @Test
    fun `each migration turns one version's tables into the next's`() {
        for (migration in migrations) {
            val step = "v${migration.startVersion}->v${migration.endVersion}"
            val migrated = applied(schema(migration.startVersion), sqlOf(migration), step)
            val expected = schema(migration.endVersion)
            assertEquals("$step: tables", expected.keys, migrated.keys)
            for ((name, table) in expected) {
                val actual = migrated.getValue(name)
                assertEquals("$step: columns of $name", table.columns.keys, actual.columns.keys)
                assertEquals("$step: primary key of $name", table.primaryKey, actual.primaryKey)
                assertEquals("$step: indices of $name", table.indices, actual.indices)
                for ((column, want) in table.columns) {
                    val got = actual.columns.getValue(column)
                    assertEquals("$step: type of $name.$column", want.affinity, got.affinity)
                    assertEquals("$step: NOT NULL of $name.$column", want.notNull, got.notNull)
                    // Like Room, a default is only checked where the entity declares one.
                    if (want.default != null) assertEquals("$step: default of $name.$column", want.default, got.default)
                }
            }
        }
    }

    @Test
    fun `v13 only adds the harvest count, at zero, so no garden changes`() {
        val sql = sqlOf(AppDatabase.MIGRATION_12_13)
        assertEquals(listOf("ALTER TABLE grid_objects ADD COLUMN harvestCount INTEGER NOT NULL DEFAULT 0"), sql)
        val before = schema(12)
        val after = schema(13)
        for ((name, table) in before) {
            val columns = if (name == "grid_objects") after.getValue(name).columns - "harvestCount" else after.getValue(name).columns
            assertEquals(name, table.columns, columns)
        }
    }

    @Test
    fun `v14 adds the tools' skill, the beds' richness and the sprinkler's switch, with nothing changed`() {
        val added = mapOf(
            "grid_objects" to setOf("soilQuality", "isOn"),
            "garden_progress" to setOf("hoeXp", "wateringCanXp"),
        )
        val before = schema(13)
        val after = schema(14)
        for ((name, table) in before) {
            assertEquals(name, table.columns, after.getValue(name).columns - added[name].orEmpty())
        }
        // Old beds are plain, old tools new, and everything that can run is running.
        assertEquals("0", after.getValue("grid_objects").columns.getValue("soilQuality").default)
        assertEquals("1", after.getValue("grid_objects").columns.getValue("isOn").default)
        assertEquals("0", after.getValue("garden_progress").columns.getValue("hoeXp").default)
    }
}
