package com.verto.data.sync

import com.verto.data.DataGraph
import com.verto.data.remote.AuthSession
import com.verto.data.remote.FakeVertoServer
import com.verto.data.remote.InMemoryTokenStore
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.data.repository.TaskDraft
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.InboxSource
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncEngineTest {

    private val server = FakeVertoServer()
    private val phone = DataGraph()
    private val otherPhone = DataGraph()

    @AfterTest
    fun close() {
        phone.db.close()
        otherPhone.db.close()
    }

    private fun DataGraph.engine(signedIn: Boolean = true, pullPage: Int = 500): SyncEngine {
        val tokens = InMemoryTokenStore(if (signedIn) server.signedInSession() else null)
        return SyncEngine(store, server.api, AuthSession(server.api, tokens), clock, pullPage = pullPage)
    }

    private fun taskPayload(title: String) = buildJsonObject {
        put("title", title)
        put("notes", null as String?)
        put("dueDate", null as String?)
        put("dueKind", null as String?)
        put("pinnedToToday", false)
        put("doneAt", null as String?)
        put("updatedAt", "2026-10-15T12:00:00Z")
    }

    @Test
    fun sync_pushesLocalChanges_andEmptiesTheOutbox() = runTest {
        val id = phone.tasks.save(TaskDraft(null, "Fazer IR"))

        val outcome = phone.engine().sync()

        assertIs<SyncOutcome.Synced>(outcome)
        assertEquals("Fazer IR", server.records.getValue("task" to id).payload.jsonObject["title"]?.jsonPrimitive?.content)
        assertTrue(phone.db.outboxDao().all().isEmpty())
        assertEquals(outcome.at, phone.store.lastSyncedAt())
    }

    @Test
    fun editDuringPush_isSentInTheSameSync() = runTest {
        val id = phone.tasks.save(TaskDraft(null, "antes"))
        server.onPush = {
            server.onPush = null
            phone.tasks.save(TaskDraft(id, "depois"))
        }

        phone.engine().sync()

        assertEquals("depois", server.records.getValue("task" to id).payload.jsonObject["title"]?.jsonPrimitive?.content)
        assertTrue(phone.db.outboxDao().all().isEmpty())
        assertEquals("depois", phone.tasks.observeTasks().first().single().title)
    }

    @Test
    fun sync_pullsChangesFromAnotherDevice_withoutSendingThemBack() = runTest {
        server.seed("task", "11111111-1111-1111-1111-111111111111", taskPayload("do tablet"))

        phone.engine().sync()

        assertEquals("do tablet", phone.tasks.observeTasks().first().single().title)
        assertTrue(phone.db.outboxDao().all().isEmpty())
        assertEquals(1, phone.store.cursor())
    }

    @Test
    fun localChange_winsOverTheServerCopy() = runTest {
        val id = phone.tasks.save(TaskDraft(null, "versão do celular"))
        server.seed("task", id, taskPayload("versão antiga do servidor"))

        phone.engine().sync()

        assertEquals("versão do celular", phone.tasks.observeTasks().first().single().title)
        assertEquals(
            "versão do celular",
            server.records.getValue("task" to id).payload.jsonObject["title"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun tombstoneFromTheServer_hidesTheRecord() = runTest {
        val id = phone.tasks.save(TaskDraft(null, "lavar louça"))
        phone.engine().sync()
        server.seed("task", id, taskPayload("lavar louça"), deletedAt = "2026-10-15T13:00:00+00:00")

        phone.engine().sync()

        assertTrue(phone.tasks.observeTasks().first().isEmpty())
    }

    @Test
    fun pull_followsEveryPage() = runTest {
        repeat(5) { server.seed("task", "00000000-0000-0000-0000-00000000000$it", taskPayload("t$it")) }

        phone.engine(pullPage = 2).sync()

        assertEquals(5, phone.tasks.observeTasks().first().size)
        assertEquals(5, phone.store.cursor())
    }

    @Test
    fun rejectedRecord_leavesTheOutbox_andIsReported() = runTest {
        val bad = phone.tasks.save(TaskDraft(null, "o servidor recusa"))
        phone.tasks.save(TaskDraft(null, "o servidor aceita"))
        server.rejectIds += bad

        val outcome = phone.engine().sync()

        assertEquals(true, (outcome as SyncOutcome.Synced).someRejected)
        assertTrue(phone.db.outboxDao().all().isEmpty())
        assertEquals(1, server.records.size)
        assertEquals(2, phone.tasks.observeTasks().first().size)
    }

    @Test
    fun offline_keepsTheOutbox_andReportsFailure() = runTest {
        phone.tasks.save(TaskDraft(null, "sem rede"))
        server.offline = true

        val outcome = phone.engine().sync()

        assertIs<SyncOutcome.Failed>(outcome)
        assertEquals(1, phone.db.outboxDao().all().size)
    }

    @Test
    fun signedOut_touchesNothing() = runTest {
        phone.tasks.save(TaskDraft(null, "sem conta"))

        val outcome = phone.engine(signedIn = false).sync()

        assertEquals(SyncOutcome.SignedOut, outcome)
        assertEquals(1, phone.db.outboxDao().all().size)
        assertEquals(0, server.pushCalls)
    }

    @Test
    fun refreshRejected_reportsSessionExpired_andKeepsTheOutbox() = runTest {
        val engine = phone.engine()
        phone.tasks.save(TaskDraft(null, "três meses sem abrir"))
        server.expireAccessTokens()
        server.revokeRefreshTokens()

        val outcome = engine.sync()

        assertEquals(SyncOutcome.SessionExpired, outcome)
        assertEquals(1, phone.db.outboxDao().all().size)
    }

    @Test
    fun twoSyncsAtOnce_pushOnlyOnce() = runTest {
        phone.tasks.save(TaskDraft(null, "uma vez"))
        val engine = phone.engine()

        List(2) { async { engine.sync() } }.awaitAll()

        assertEquals(1, server.pushCalls)
    }

    @Test
    fun everything_reachesASecondPhone() = runTest {
        phone.inbox.capture("ideia solta", InboxSource.App)
        val taskId = phone.tasks.save(
            TaskDraft(null, "Fazer IR", steps = listOf(StepDraft(null, "abrir site", StepEnergy.Low))),
        )
        val anchorId = phone.routines.saveAnchor(AnchorDraft(null, "Almocei", LocalTime(12, 0), LocalTime(14, 0)))
        val routineId = phone.routines.saveRoutine(
            RoutineDraft(
                null,
                "Caminhada",
                anchorId,
                DayOfWeek.entries.toSet(),
                stepsA = listOf(StepDraft(null, "20 min", StepEnergy.Medium)),
                stepsC = listOf(StepDraft(null, "até a esquina", StepEnergy.Low)),
            ),
        )
        val day = LocalDate(2026, 10, 15)
        phone.routines.tapAnchor(anchorId, day)
        phone.routines.skip(routineId, day)
        phone.days.setEnergy(day, EnergyLevel.Low)

        phone.engine().sync()
        otherPhone.engine().sync()

        val db1 = phone.db
        val db2 = otherPhone.db
        assertEquals(db1.inboxDao().active().toSet(), db2.inboxDao().active().toSet())
        assertEquals(db1.taskDao().get(taskId), db2.taskDao().get(taskId))
        assertEquals(db1.stepDao().observeActive().first().toSet(), db2.stepDao().observeActive().first().toSet())
        assertEquals(db1.anchorDao().activeAnchors().toSet(), db2.anchorDao().activeAnchors().toSet())
        assertEquals(db1.anchorDao().eventsOn(day.toString()).toSet(), db2.anchorDao().eventsOn(day.toString()).toSet())
        assertEquals(db1.routineDao().getRoutine(routineId), db2.routineDao().getRoutine(routineId))
        assertEquals(db1.routineDao().logFor(routineId, day.toString()), db2.routineDao().logFor(routineId, day.toString()))
        assertEquals(db1.dayEnergyDao().latest(day.toString()), db2.dayEnergyDao().latest(day.toString()))
        assertTrue(db2.outboxDao().all().isEmpty())
    }
}
