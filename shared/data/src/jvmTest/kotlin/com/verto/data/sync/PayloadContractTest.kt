package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.inMemoryVertoDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Mirrors SyncEntityCatalog.cs in the Verto API: a missing key there means a rejected push. */
class PayloadContractTest {

    private val required = mapOf(
        "inbox_item" to listOf("text", "createdAt", "source", "state"),
        "task" to listOf("title", "pinnedToToday"),
        "step" to listOf("order", "title", "energy"),
        "anchor" to listOf("name", "windowStart", "windowEnd"),
        "anchor_event" to listOf("anchorId", "day", "triggeredAt", "source"),
        "routine" to listOf("name", "anchorId", "weekdays"),
        "routine_log" to listOf("routineId", "day", "outcome"),
        "day_energy" to listOf("day", "level", "setAt"),
    )

    private val db = inMemoryVertoDatabase()
    private val store = LocalStore(db)

    @AfterTest
    fun close() = db.close()

    private suspend fun writeOneOfEach() = store.write {
        put(InboxItemEntity("i", "texto", 1_000, "app", "raw", 1_000, null))
        put(TaskEntity("t", "IR", null, "2026-10-20", "hard", true, null, 1_000, null))
        put(StepEntity("s", null, "r", "c", 0, "alongar", "low", null, 1_000, null))
        put(AnchorEntity("a", "Almocei", "12:00", "14:00", 1_000, null))
        put(AnchorEventEntity("e", "a", "2026-10-15", 1_000, "tap", 1_000, null))
        put(RoutineEntity("r", "Exercício", "a", "1,3,5", 1_000, null))
        put(RoutineLogEntity("l", "r", "2026-10-15", "c", 1_000, null))
        put(DayEnergyEntity("d", "2026-10-15", "low", 1_000, 1_000, null))
    }

    @Test
    fun everyPayload_hasTheKeysTheServerRequires() = runTest {
        writeOneOfEach()

        val entries = db.outboxDao().all()
        assertEquals(required.keys, entries.map { it.entityType }.toSet())
        entries.forEach { entry ->
            val payload = Json.parseToJsonElement(entry.payload).jsonObject
            required.getValue(entry.entityType).forEach { key ->
                assertTrue(key in payload, "${entry.entityType} payload is missing $key")
            }
        }
    }

    @Test
    fun routineStep_carriesRoutineIdAndPlan() = runTest {
        writeOneOfEach()

        val step = Json.parseToJsonElement(db.outboxDao().all().single { it.entityType == "step" }.payload).jsonObject
        assertNotEquals(JsonNull, step["routineId"])
        assertEquals("c", step["plan"]?.jsonPrimitive?.content)
    }

    @Test
    fun routineWeekdays_travelAsIsoNumbers() = runTest {
        writeOneOfEach()

        val routine = Json.parseToJsonElement(db.outboxDao().all().single { it.entityType == "routine" }.payload).jsonObject
        assertEquals(listOf("1", "3", "5"), routine.getValue("weekdays").jsonArray.map { it.jsonPrimitive.content })
    }
}
