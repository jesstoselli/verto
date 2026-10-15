package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteDecoderTest {

    private fun <T> roundTrip(type: String, id: String, payload: String, deletedAt: Long?): T {
        @Suppress("UNCHECKED_CAST")
        return RemoteDecoder.decode(type, id, payload, deletedAt, receivedAt = 9_999) as T
    }

    @Test
    fun everyEntity_survivesTheTripThroughTheServer() {
        val inbox = InboxItemEntity("i", "texto", 1_000, "app", "raw", 2_000, null)
        val task = TaskEntity("t", "IR", "notas", "2026-10-20", "hard", true, 3_000, 4_000, null)
        val taskStep = StepEntity("s1", "t", null, null, 2, "abrir site", "medium", 5_000, 6_000, null)
        val routineStep = StepEntity("s2", null, "r", "c", 0, "alongar", "low", null, 6_000, 7_000)
        val anchor = AnchorEntity("a", "Almocei", "12:00", "14:00", 1_000, null)
        val event = AnchorEventEntity("e", "a", "2026-10-15", 1_500, "window", 1_600, null)
        val routine = RoutineEntity("r", "Exercício", "a", "1,3,5", 1_000, null)
        val log = RoutineLogEntity("l", "r", "2026-10-15", "skipped", 1_000, null)
        val energy = DayEnergyEntity("d", "2026-10-15", "high", 1_200, 1_300, null)

        assertEquals(inbox, roundTrip("inbox_item", "i", SyncJson.encodeToString(inbox.toPayload()), null))
        assertEquals(task, roundTrip("task", "t", SyncJson.encodeToString(task.toPayload()), null))
        assertEquals(taskStep, roundTrip("step", "s1", SyncJson.encodeToString(taskStep.toPayload()), null))
        assertEquals(routineStep, roundTrip("step", "s2", SyncJson.encodeToString(routineStep.toPayload()), 7_000))
        assertEquals(anchor, roundTrip("anchor", "a", SyncJson.encodeToString(anchor.toPayload()), null))
        assertEquals(event, roundTrip("anchor_event", "e", SyncJson.encodeToString(event.toPayload()), null))
        assertEquals(routine, roundTrip("routine", "r", SyncJson.encodeToString(routine.toPayload()), null))
        assertEquals(log, roundTrip("routine_log", "l", SyncJson.encodeToString(log.toPayload()), null))
        assertEquals(energy, roundTrip("day_energy", "d", SyncJson.encodeToString(energy.toPayload()), null))
    }

    @Test
    fun missingUpdatedAt_fallsBackToWhenItArrived() {
        val anchor = roundTrip<AnchorEntity>("anchor", "a", """{"name":"Acordei","windowStart":"07:00","windowEnd":"09:00"}""", null)

        assertEquals(9_999, anchor.updatedAt)
    }

    @Test
    fun unknownKeys_areIgnored() {
        val anchor = roundTrip<AnchorEntity>(
            "anchor",
            "a",
            """{"name":"Acordei","windowStart":"07:00","windowEnd":"09:00","color":"lilac"}""",
            null,
        )

        assertEquals("Acordei", anchor.name)
    }

    @Test
    fun unknownTypeOrBrokenPayload_isSkipped() {
        assertNull(RemoteDecoder.decode("habit", "h", "{}", null, 0))
        assertNull(RemoteDecoder.decode("task", "t", """{"title":42}""", null, 0))
    }
}
