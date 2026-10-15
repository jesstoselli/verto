package com.verto.data.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.verto.data.sync.LocalStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationTest {

    @Test
    fun version1Database_upgradesWithItsRowsAndOutbox() = runTest {
        val file = File.createTempFile("verto-v1", ".db").apply { delete() }
        try {
            createVersion1(file)

            val db = vertoDatabase(file)
            try {
                assertEquals("Fazer IR", db.taskDao().get("t1")?.title)
                assertEquals(listOf("t1"), db.outboxDao().all().map { it.entityId })
                assertEquals(0L, LocalStore(db).cursor())
            } finally {
                db.close()
            }
        } finally {
            file.delete()
        }
    }

    /** Builds the exact version-1 database Plan 2 shipped, from the exported schema, with one task in it. */
    private fun createVersion1(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.verto.data.db.VertoDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                fun sql(raw: String) = raw.replace("\${TABLE_NAME}", table)
                connection.execSQL(sql(entity.jsonObject.getValue("createSql").jsonPrimitive.content))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(sql(index.jsonObject.getValue("createSql").jsonPrimitive.content))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL(
                "INSERT INTO tasks (id, title, notes, dueDate, dueKind, pinnedToToday, doneAt, updatedAt, deletedAt) " +
                    "VALUES ('t1', 'Fazer IR', NULL, NULL, NULL, 1, NULL, 1000, NULL)",
            )
            connection.execSQL(
                "INSERT INTO outbox (entityType, entityId, payload, deletedAt, clientUpdatedAt) " +
                    "VALUES ('task', 't1', '{}', NULL, 1000)",
            )
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }
}
