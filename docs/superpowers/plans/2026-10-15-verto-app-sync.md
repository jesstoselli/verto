# Verto App Sync (Plan 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app pushes every local change to the Verto API and pulls changes from it, so the data survives a lost phone and is ready for a second device, without ever blocking the UI.

**Architecture:** Every write already lands in Room and in the `outbox` table in one transaction (Plan 2). This plan adds a Ktor client for `/v1/auth/*` and `/v1/sync/*`, an `AuthSession` that refreshes tokens one at a time, a `SyncEngine` (push the outbox, then pull pages from a cursor kept in a new `sync_state` table) and a `SyncScheduler` that runs it on app start, a few seconds after each change and with backoff while offline; WorkManager runs it periodically in the background. A "Conta e sync" screen, reached from Planejar, holds login, logout and the sync status line. The server does not change.

**Tech Stack:** Kotlin 2.4.21, Room KMP 2.8.5 (auto-migration 1→2), Ktor Client 3.3.0 (core, content-negotiation, kotlinx-json; OkHttp engine on Android; MockEngine in tests), kotlinx-serialization 1.11.0, Koin 4.2.2, WorkManager 2.10.0, Compose Multiplatform 1.12.2.

**Spec:** `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md` (sections "Sync", "Arquitetura", "Testes"). API contract: `~/Repos/Verto/src/Verto.Application/Dtos/Sync/SyncDtos.cs`, `Dtos/Auth/AuthDtos.cs`, `UseCases/Sync/SyncEntityCatalog.cs`, `UseCases/Sync/SyncService.cs`.

## Global Constraints

- Work on branch `feat/app-sync` cut from `main`. When integrating later: **rebase onto `main` and fast-forward, no merge commits**.
- The server is not touched. Every request must satisfy `SyncEntityCatalog.cs` (required payload keys, payload ≤ 64 KB, ≤ 500 changes per push, `clientUpdatedAt` set, `id` a GUID).
- API base URL: `https://verto-api.billfolder.app`. Endpoints: `POST /v1/auth/login`, `POST /v1/auth/refresh`, `POST /v1/auth/logout`, `POST /v1/sync/push`, `GET /v1/sync/pull?since=<long>&limit=<int>`. JSON is camelCase.
- Enum values travel lowercase, exactly as Room stores them (`a`, `c`, `skipped`, `low`, `hard`…). The spec table that says `A | C` is amended in Task 1, not the code: changing the wire would need a data migration on a phone that already holds real data.
- Conflict rule (spec): last write to reach the server wins; a pulled record whose id still has an outbox entry is skipped locally (the pending local version goes up on the next push).
- Sync failures never block the UI and never show outside the "Conta e sync" screen. No counters ("3 mudanças pendentes"), no red, no word "atrasado", no word "erro" in user copy.
- The refresh token is single-use on the server (rotation with family revocation on reuse): **never send two refreshes with the same token**.
- Tokens are never logged, never put in Room, and are excluded from Android backup.
- Existing installs (DB version 1 with real data) must upgrade without losing a row or an outbox entry.
- New dependencies allowed by this plan only: Ktor client (core, content-negotiation, serialization-kotlinx-json, okhttp, mock for tests) and `androidx.work:work-runtime-ktx`. Nothing else.
- Code, identifiers and comments in English; user-facing copy in Brazilian Portuguese; commits in Portuguese (`feat(sync): ...`).

## Review Focus

1. **Upgrading a phone that already has data.** Opening a version-1 database must keep every row and every pending outbox entry, then start syncing from cursor 0. Pinned by `MigrationTest.version1Database_upgradesWithItsRowsAndOutbox` (Task 2).
2. **Editing a record while its push is in flight.** The newer local edit must not be dropped when the older one is acknowledged. Pinned by `LocalStoreTest.markSent_keepsAnEntryRewrittenAfterItWasRead` (Task 2) and `SyncEngineTest.editDuringPush_isSentInTheSameSync` (Task 4).
3. **Two requests hitting 401 at the same time.** Only one refresh may be sent, or the server revokes the whole token family and she is logged out. Pinned by `AuthSessionTest.concurrent401s_shareOneRefresh` (Task 3).
4. **Refresh token expired or revoked (phone off for 3 months).** The app keeps working offline, keeps the outbox, and asks her to log in again without losing anything. Pinned by `SyncEngineTest.refreshRejected_reportsSessionExpired_andKeepsTheOutbox` (Task 4).
5. **A record the server refuses.** It must not block every later push forever. Pinned by `SyncEngineTest.rejectedRecord_leavesTheOutbox_andIsReported` (Task 4).

---

## File Structure

```
VertoApp/
├── gradle/libs.versions.toml                                   (Task 3, Task 7: Ktor, WorkManager)
├── shared/data/
│   ├── build.gradle.kts                                        (Task 3: Ktor deps)
│   ├── schemas/com.verto.data.db.VertoDatabase/2.json          (generated, Task 2)
│   └── src/
│       ├── commonMain/kotlin/com/verto/data/
│       │   ├── db/Entities.kt            (Task 2: SyncStateEntity)
│       │   ├── db/Daos.kt                (Task 2: SyncStateDao, OutboxDao queries)
│       │   ├── db/VertoDatabase.kt       (Task 2: version 2 + auto-migration)
│       │   ├── sync/Payloads.kt          (Task 1: updatedAt in every payload)
│       │   ├── sync/RemoteDecoder.kt     (Task 1: payload → entity)
│       │   ├── sync/LocalStore.kt        (Task 2: applyRemote, cursor, outbox batches, state)
│       │   ├── sync/SyncEngine.kt        (Task 4)
│       │   ├── sync/SyncScheduler.kt     (Task 5)
│       │   └── remote/
│       │       ├── ApiModels.kt          (Task 3)
│       │       ├── VertoApi.kt           (Task 3)
│       │       └── AuthSession.kt        (Task 3: Session, TokenStore, AuthSession, AccountState)
│       └── jvmTest/kotlin/com/verto/data/
│           ├── sync/RemoteDecoderTest.kt (Task 1)
│           ├── sync/LocalStoreTest.kt    (Task 2: new cases)
│           ├── db/MigrationTest.kt       (Task 2)
│           ├── remote/FakeVertoServer.kt (Task 3)
│           ├── remote/AuthSessionTest.kt (Task 3)
│           ├── sync/SyncEngineTest.kt    (Task 4)
│           └── sync/SyncSchedulerTest.kt (Task 5)
├── composeApp/
│   ├── build.gradle.kts                                        (Task 6: ktor-client-mock in tests)
│   └── src/
│       ├── commonMain/kotlin/com/verto/ui/
│       │   ├── account/SyncStatusLine.kt     (Task 6)
│       │   ├── account/AccountViewModel.kt   (Task 6)
│       │   ├── account/AccountScreen.kt      (Task 6)
│       │   ├── navigation/Routes.kt          (Task 6: AccountRoute)
│       │   ├── navigation/VertoNavHost.kt    (Task 6)
│       │   ├── plan/PlanScreen.kt            (Task 6: account button)
│       │   └── di/VertoModule.kt             (Task 6: sync singletons + AccountViewModel)
│       ├── commonTest/.../account/SyncStatusLineTest.kt   (Task 6)
│       └── jvmTest/.../account/AccountViewModelTest.kt    (Task 6)
├── androidApp/
│   ├── build.gradle.kts                                        (Task 7)
│   └── src/main/
│       ├── AndroidManifest.xml               (Task 7: INTERNET, backup rules)
│       ├── res/xml/backup_rules.xml          (Task 7)
│       ├── res/xml/data_extraction_rules.xml (Task 7)
│       └── kotlin/com/verto/android/
│           ├── PrefsTokenStore.kt            (Task 7)
│           ├── SyncWorker.kt                 (Task 7)
│           ├── VertoApplication.kt           (Task 7)
│           └── MainActivity.kt               (Task 7)
├── README.md                                                   (Task 8)
└── ~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md  (Task 1: wire amendments, other repo)
```

---

### Task 1: Wire format both ways

**Files:**
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/sync/Payloads.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/sync/RemoteDecoder.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/sync/RemoteDecoderTest.kt`
- Modify (other repo): `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md`

**Interfaces:**
- Consumes: entity classes from `db/Entities.kt`; `SyncJson` and `toPayload()` from `Payloads.kt` (Plan 2).
- Produces: `internal object RemoteDecoder { fun decode(entityType: String, id: String, payload: String, deletedAt: Long?, receivedAt: Long): Any? }` returning one of the 8 entity classes, or `null` for an unknown type or an unreadable payload. Every payload gains `updatedAt: String?` (ISO instant) so a pulled row keeps its original `updatedAt`.

- [ ] **Step 1: Create the branch**

```bash
cd ~/Repos/VertoApp
git checkout main && git pull --ff-only
git checkout -b feat/app-sync
```

- [ ] **Step 2: Write the failing test**

`shared/data/src/jvmTest/kotlin/com/verto/data/sync/RemoteDecoderTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :shared:data:jvmTest --tests '*RemoteDecoderTest*' --console=plain`
Expected: compilation FAIL, `Unresolved reference 'RemoteDecoder'`.

- [ ] **Step 4: Add `updatedAt` to every payload**

Replace the whole of `shared/data/src/commonMain/kotlin/com/verto/data/sync/Payloads.kt`:

`shared/data/src/commonMain/kotlin/com/verto/data/sync/Payloads.kt`:

```kotlin
package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

// Unknown keys are ignored so a newer client can add fields without breaking this one.
internal val SyncJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

// Key names are the sync contract with the API (SyncEntityCatalog.cs); rename nothing here without the server.
// updatedAt is not required by the server; it travels so a pulled row keeps its original edit time.

@Serializable
internal data class InboxPayload(
    val text: String,
    val createdAt: String,
    val source: String,
    val state: String,
    val updatedAt: String? = null,
)

@Serializable
internal data class TaskPayload(
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueKind: String?,
    val pinnedToToday: Boolean,
    val doneAt: String?,
    val updatedAt: String? = null,
)

@Serializable
internal data class StepPayload(
    val taskId: String?,
    val routineId: String?,
    val plan: String?,
    val order: Int,
    val title: String,
    val energy: String,
    val doneAt: String?,
    val updatedAt: String? = null,
)

@Serializable
internal data class AnchorPayload(
    val name: String,
    val windowStart: String,
    val windowEnd: String,
    val updatedAt: String? = null,
)

@Serializable
internal data class AnchorEventPayload(
    val anchorId: String,
    val day: String,
    val triggeredAt: String,
    val source: String,
    val updatedAt: String? = null,
)

@Serializable
internal data class RoutinePayload(
    val name: String,
    val anchorId: String,
    val weekdays: List<Int>,
    val updatedAt: String? = null,
)

@Serializable
internal data class RoutineLogPayload(
    val routineId: String,
    val day: String,
    val outcome: String,
    val updatedAt: String? = null,
)

@Serializable
internal data class DayEnergyPayload(
    val day: String,
    val level: String,
    val setAt: String,
    val updatedAt: String? = null,
)

internal fun Long.iso(): String = Instant.fromEpochMilliseconds(this).toString()

internal fun InboxItemEntity.toPayload() = InboxPayload(text, createdAt.iso(), source, state, updatedAt.iso())

internal fun TaskEntity.toPayload() =
    TaskPayload(title, notes, dueDate, dueKind, pinnedToToday, doneAt?.iso(), updatedAt.iso())

internal fun StepEntity.toPayload() =
    StepPayload(taskId, routineId, plan, order, title, energy, doneAt?.iso(), updatedAt.iso())

internal fun AnchorEntity.toPayload() = AnchorPayload(name, windowStart, windowEnd, updatedAt.iso())

internal fun AnchorEventEntity.toPayload() = AnchorEventPayload(anchorId, day, triggeredAt.iso(), source, updatedAt.iso())

internal fun RoutineEntity.toPayload() =
    RoutinePayload(name, anchorId, weekdays.split(',').filter { it.isNotBlank() }.map { it.toInt() }, updatedAt.iso())

internal fun RoutineLogEntity.toPayload() = RoutineLogPayload(routineId, day, outcome, updatedAt.iso())

internal fun DayEnergyEntity.toPayload() = DayEnergyPayload(day, level, setAt.iso(), updatedAt.iso())
```

- [ ] **Step 5: Write the decoder**

`shared/data/src/commonMain/kotlin/com/verto/data/sync/RemoteDecoder.kt`:

```kotlin
package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import kotlinx.serialization.SerializationException
import kotlin.time.Instant

/** Turns a pulled record back into the row it came from. */
internal object RemoteDecoder {

    /**
     * Returns the entity, or null when the type is unknown (a newer client's entity) or the payload
     * cannot be read: one bad record is skipped instead of stopping the whole pull.
     */
    fun decode(entityType: String, id: String, payload: String, deletedAt: Long?, receivedAt: Long): Any? = try {
        when (entityType) {
            "inbox_item" -> SyncJson.decodeFromString(InboxPayload.serializer(), payload).run {
                InboxItemEntity(id, text, createdAt.ms(), source, state, updatedAt.msOr(receivedAt), deletedAt)
            }
            "task" -> SyncJson.decodeFromString(TaskPayload.serializer(), payload).run {
                TaskEntity(id, title, notes, dueDate, dueKind, pinnedToToday, doneAt?.ms(), updatedAt.msOr(receivedAt), deletedAt)
            }
            "step" -> SyncJson.decodeFromString(StepPayload.serializer(), payload).run {
                StepEntity(id, taskId, routineId, plan, order, title, energy, doneAt?.ms(), updatedAt.msOr(receivedAt), deletedAt)
            }
            "anchor" -> SyncJson.decodeFromString(AnchorPayload.serializer(), payload).run {
                AnchorEntity(id, name, windowStart, windowEnd, updatedAt.msOr(receivedAt), deletedAt)
            }
            "anchor_event" -> SyncJson.decodeFromString(AnchorEventPayload.serializer(), payload).run {
                AnchorEventEntity(id, anchorId, day, triggeredAt.ms(), source, updatedAt.msOr(receivedAt), deletedAt)
            }
            "routine" -> SyncJson.decodeFromString(RoutinePayload.serializer(), payload).run {
                RoutineEntity(id, name, anchorId, weekdays.joinToString(","), updatedAt.msOr(receivedAt), deletedAt)
            }
            "routine_log" -> SyncJson.decodeFromString(RoutineLogPayload.serializer(), payload).run {
                RoutineLogEntity(id, routineId, day, outcome, updatedAt.msOr(receivedAt), deletedAt)
            }
            "day_energy" -> SyncJson.decodeFromString(DayEnergyPayload.serializer(), payload).run {
                DayEnergyEntity(id, day, level, setAt.ms(), updatedAt.msOr(receivedAt), deletedAt)
            }
            else -> null
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun String.ms(): Long = Instant.parse(this).toEpochMilliseconds()

    private fun String?.msOr(fallback: Long): Long = this?.ms() ?: fallback
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest --console=plain`
Expected: PASS, including the existing `PayloadContractTest` (required keys unchanged; `updatedAt` is an extra key the server accepts).

- [ ] **Step 7: Amend the spec to match the wire**

In `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md`:

Replace `| **Step** | ... | Micro-passo. Rotinas usam \`plan\` = \`A\` \| \`C\`. |` so that its last cell reads:

```
Micro-passo. Rotinas usam `plan` = `a` \| `c`. |
```

Replace in the **RoutineLog** row `` `outcome` (`A` \| `C` \| `skipped`) `` with:

```
`outcome` (`a` \| `c` \| `skipped`)
```

Append to the paragraph that starts `Todas as entidades têm` (same paragraph, after its last sentence):

```
No fio, valores de enum vão em minúsculas, como o banco local guarda, e todo payload leva também `updatedAt` (ISO), que o servidor guarda sem validar.
```

- [ ] **Step 8: Commit (two repos)**

```bash
cd ~/Repos/VertoApp
git add shared/data
git commit -m "feat(sync): payloads levam updatedAt e voltam do servidor como linhas do Room"
cd ~/Repos/Verto
git add docs/superpowers/specs/2026-10-15-verto-design.md
git commit -m "docs(spec): enums em minúsculas no fio e updatedAt no payload"
cd ~/Repos/VertoApp
```

---

### Task 2: Sync state, outbox batches and applying pulled changes

**Files:**
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/db/Entities.kt` (append `SyncStateEntity`)
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/db/Daos.kt` (`OutboxDao` queries, new `SyncStateDao`)
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/db/VertoDatabase.kt`
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt`
- Create (generated by the build): `shared/data/schemas/com.verto.data.db.VertoDatabase/2.json`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/db/MigrationTest.kt`, new cases in `shared/data/src/jvmTest/kotlin/com/verto/data/sync/LocalStoreTest.kt`

**Interfaces:**
- Consumes: `RemoteDecoder.decode` (Task 1).
- Produces, on `LocalStore`:
  - `data class RemoteChange(val entityType: String, val id: String, val payload: String, val deletedAt: Long?)` (top level, package `com.verto.data.sync`)
  - `suspend fun pendingBatch(limit: Int): List<OutboxEntity>`
  - `suspend fun markSent(seqs: List<Long>)`
  - `fun observePendingCount(): Flow<Int>`
  - `suspend fun applyRemote(changes: List<RemoteChange>, cursor: Long, receivedAt: Long)`
  - `suspend fun cursor(): Long`
  - `suspend fun lastSyncedAt(): Instant?` / `suspend fun setLastSyncedAt(at: Instant)`

- [ ] **Step 1: Write the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/db/MigrationTest.kt`:

```kotlin
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
```

Append these cases to `shared/data/src/jvmTest/kotlin/com/verto/data/sync/LocalStoreTest.kt` (inside the class, before its closing brace; add the imports `com.verto.data.db.TaskEntity`, `kotlinx.coroutines.flow.first`, `kotlin.time.Instant`):

```kotlin
    private fun remoteInbox(id: String, text: String, deletedAt: Long? = null) = RemoteChange(
        entityType = "inbox_item",
        id = id,
        payload = """{"text":"$text","createdAt":"2026-10-15T12:00:00Z","source":"app","state":"raw","updatedAt":"2026-10-15T12:00:00Z"}""",
        deletedAt = deletedAt,
    )

    @Test
    fun markSent_keepsAnEntryRewrittenAfterItWasRead() = runTest {
        store.write { put(inbox("i1", "antes", 1_000)) }
        val sent = store.pendingBatch(10)

        store.write { put(inbox("i1", "depois", 2_000)) }
        store.markSent(sent.map { it.seq })

        val left = db.outboxDao().all().single()
        assertTrue("depois" in left.payload)
    }

    @Test
    fun applyRemote_writesRowsWithoutQueueingThem() = runTest {
        store.applyRemote(listOf(remoteInbox("r1", "do outro celular")), cursor = 7, receivedAt = 0)

        assertEquals("do outro celular", db.inboxDao().get("r1")?.text)
        assertTrue(db.outboxDao().all().isEmpty())
        assertEquals(7, store.cursor())
    }

    @Test
    fun applyRemote_skipsARecordWithALocalChangePending() = runTest {
        store.write { put(inbox("i1", "versão daqui", 1_000)) }

        store.applyRemote(listOf(remoteInbox("i1", "versão do servidor")), cursor = 3, receivedAt = 0)

        assertEquals("versão daqui", db.inboxDao().get("i1")?.text)
        assertEquals(3, store.cursor())
    }

    @Test
    fun applyRemote_appliesTombstones() = runTest {
        store.applyRemote(listOf(remoteInbox("r1", "vai sumir")), cursor = 1, receivedAt = 0)

        store.applyRemote(listOf(remoteInbox("r1", "vai sumir", deletedAt = 5_000)), cursor = 2, receivedAt = 0)

        assertEquals(5_000, db.inboxDao().get("r1")?.deletedAt)
        assertTrue(db.inboxDao().active().isEmpty())
    }

    @Test
    fun applyRemote_skipsWhatItCannotRead_andStillMovesTheCursor() = runTest {
        store.applyRemote(listOf(RemoteChange("habit", "h1", "{}", null)), cursor = 9, receivedAt = 0)

        assertEquals(9, store.cursor())
    }

    @Test
    fun lastSyncedAt_isKeptAcrossReads() = runTest {
        assertEquals(null, store.lastSyncedAt())

        store.setLastSyncedAt(Instant.fromEpochMilliseconds(1_700_000_000_000))

        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000), store.lastSyncedAt())
    }

    @Test
    fun observePendingCount_followsTheOutbox() = runTest {
        assertEquals(0, store.observePendingCount().first())

        store.write { put(TaskEntity("t1", "IR", null, null, null, false, null, 1_000, null)) }

        assertEquals(1, store.observePendingCount().first())
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:data:jvmTest --console=plain`
Expected: compilation FAIL (`Unresolved reference 'cursor'`, `'RemoteChange'`, `'pendingBatch'`…).

- [ ] **Step 3: Add the table, the DAO queries and the migration**

Append to `shared/data/src/commonMain/kotlin/com/verto/data/db/Entities.kt`:

```kotlin

/** Small named values the sync engine keeps next to the data they describe (pull cursor, last sync). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val name: String,
    val value: String,
)
```

In `shared/data/src/commonMain/kotlin/com/verto/data/db/Daos.kt`, replace the whole `OutboxDao` interface (from `@Dao` above `interface OutboxDao` to its closing brace) with:

```kotlin
@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq")
    suspend fun all(): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY seq LIMIT :limit")
    suspend fun oldest(limit: Int): List<OutboxEntity>

    // REPLACE gives a rewritten record a new seq, so deleting by seq never drops an edit made after the read.
    @Query("DELETE FROM outbox WHERE seq IN (:seqs)")
    suspend fun deleteSent(seqs: List<Long>)

    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE entityType = :entityType AND entityId = :entityId)")
    suspend fun hasPending(entityType: String, entityId: String): Boolean

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeSize(): Flow<Int>
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE name = :name")
    suspend fun get(name: String): String?

    @Upsert
    suspend fun put(row: SyncStateEntity)
}
```

Replace the whole of `shared/data/src/commonMain/kotlin/com/verto/data/db/VertoDatabase.kt`:

`shared/data/src/commonMain/kotlin/com/verto/data/db/VertoDatabase.kt`:

```kotlin
package com.verto.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        InboxItemEntity::class,
        TaskEntity::class,
        StepEntity::class,
        AnchorEntity::class,
        AnchorEventEntity::class,
        RoutineEntity::class,
        RoutineLogEntity::class,
        DayEnergyEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    // Phones already hold real data in version 1: only additive auto-migrations, never a destructive fallback.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(VertoDatabaseConstructor::class)
abstract class VertoDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
    abstract fun taskDao(): TaskDao
    abstract fun stepDao(): StepDao
    abstract fun anchorDao(): AnchorDao
    abstract fun routineDao(): RoutineDao
    abstract fun dayEnergyDao(): DayEnergyDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao
}

// Room's KSP processor generates the actual object for every target.
@Suppress("KotlinNoActualForExpect")
expect object VertoDatabaseConstructor : RoomDatabaseConstructor<VertoDatabase> {
    override fun initialize(): VertoDatabase
}
```

- [ ] **Step 4: Teach `LocalStore` to batch the outbox and apply pulls**

Replace the whole of `shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt`:

`shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt`:

```kotlin
package com.verto.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.OutboxEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.SyncStateEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.VertoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlin.time.Instant

/** A change pulled from the server, payload still as JSON text. */
data class RemoteChange(
    val entityType: String,
    val id: String,
    val payload: String,
    val deletedAt: Long?,
)

class LocalStore(val db: VertoDatabase) {

    /** Row and outbox entry commit together, so the sync engine can never miss a local change. */
    suspend fun <T> write(block: suspend WriteScope.() -> T): T =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction { WriteScope(db).block() }
        }

    suspend fun pendingBatch(limit: Int): List<OutboxEntity> = db.outboxDao().oldest(limit)

    suspend fun markSent(seqs: List<Long>) {
        if (seqs.isNotEmpty()) db.outboxDao().deleteSent(seqs)
    }

    fun observePendingCount(): Flow<Int> = db.outboxDao().observeSize()

    /**
     * Writes pulled records straight to their tables (they must not go back out through the outbox)
     * and moves the cursor in the same transaction, so a crash never skips or half-applies a page.
     */
    suspend fun applyRemote(changes: List<RemoteChange>, cursor: Long, receivedAt: Long) {
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                for (change in changes) {
                    // A local edit still waiting in the outbox wins: it reaches the server on the next push.
                    if (db.outboxDao().hasPending(change.entityType, change.id)) continue
                    val row = RemoteDecoder.decode(change.entityType, change.id, change.payload, change.deletedAt, receivedAt)
                    upsertQuietly(row)
                }
                db.syncStateDao().put(SyncStateEntity(CURSOR, cursor.toString()))
            }
        }
    }

    suspend fun cursor(): Long = db.syncStateDao().get(CURSOR)?.toLongOrNull() ?: 0L

    suspend fun lastSyncedAt(): Instant? = db.syncStateDao().get(LAST_SYNCED_AT)?.let { Instant.parse(it) }

    suspend fun setLastSyncedAt(at: Instant) = db.syncStateDao().put(SyncStateEntity(LAST_SYNCED_AT, at.toString()))

    private suspend fun upsertQuietly(row: Any?) {
        when (row) {
            is InboxItemEntity -> db.inboxDao().upsert(row)
            is TaskEntity -> db.taskDao().upsert(row)
            is StepEntity -> db.stepDao().upsert(row)
            is AnchorEntity -> db.anchorDao().upsertAnchor(row)
            is AnchorEventEntity -> db.anchorDao().upsertEvent(row)
            is RoutineEntity -> db.routineDao().upsertRoutine(row)
            is RoutineLogEntity -> db.routineDao().upsertLog(row)
            is DayEnergyEntity -> db.dayEnergyDao().upsert(row)
            else -> Unit
        }
    }

    private companion object {
        const val CURSOR = "pull_cursor"
        const val LAST_SYNCED_AT = "last_synced_at"
    }
}

class WriteScope internal constructor(private val db: VertoDatabase) {

    suspend fun put(entity: InboxItemEntity) {
        db.inboxDao().upsert(entity)
        enqueue("inbox_item", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: TaskEntity) {
        db.taskDao().upsert(entity)
        enqueue("task", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: StepEntity) {
        db.stepDao().upsert(entity)
        enqueue("step", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: AnchorEntity) {
        db.anchorDao().upsertAnchor(entity)
        enqueue("anchor", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: AnchorEventEntity) {
        db.anchorDao().upsertEvent(entity)
        enqueue("anchor_event", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: RoutineEntity) {
        db.routineDao().upsertRoutine(entity)
        enqueue("routine", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: RoutineLogEntity) {
        db.routineDao().upsertLog(entity)
        enqueue("routine_log", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: DayEnergyEntity) {
        db.dayEnergyDao().upsert(entity)
        enqueue("day_energy", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    private suspend fun enqueue(type: String, id: String, payload: String, deletedAt: Long?, updatedAt: Long) =
        db.outboxDao().enqueue(
            OutboxEntity(entityType = type, entityId = id, payload = payload, deletedAt = deletedAt, clientUpdatedAt = updatedAt),
        )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest :composeApp:jvmTest --console=plain`
Expected: PASS. The build writes `shared/data/schemas/com.verto.data.db.VertoDatabase/2.json`; check it exists.

- [ ] **Step 6: Commit**

```bash
git add shared/data
git commit -m "feat(sync): tabela sync_state, lotes do outbox e aplicação do pull (banco v2)"
```

---

### Task 3: API client and the session that refreshes one at a time

**Files:**
- Modify: `gradle/libs.versions.toml`, `shared/data/build.gradle.kts`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/remote/ApiModels.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/remote/VertoApi.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/remote/AuthSession.kt`
- Create: `shared/data/src/jvmTest/kotlin/com/verto/data/remote/FakeVertoServer.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/remote/AuthSessionTest.kt`

**Interfaces:**
- Produces (package `com.verto.data.remote`):
  - `fun vertoHttpClient(engine: HttpClientEngine): HttpClient`
  - `class VertoApi(http: HttpClient, baseUrl: String = VertoApi.DEFAULT_BASE_URL)` with internal `login`, `refresh`, `logout`, `push(access, changes: List<ChangeIn>): PushResult`, `pull(access, since: Long, limit: Int): PullResult`
  - `class UnauthorizedException(val code: String?)`, `class ServerException(val status: Int, val code: String?)`
  - `@Serializable data class Session(val accessToken: String, val refreshToken: String, val email: String)`
  - `interface TokenStore { fun load(): Session?; fun save(session: Session?) }`, `class InMemoryTokenStore`
  - `sealed interface AccountState { SignedOut; SignedIn(email); Expired(email) }`
  - `class AuthSession(api: VertoApi, tokens: TokenStore)` with `val state: StateFlow<AccountState>`, `suspend fun login(email, password)`, `suspend fun logout()`, `suspend fun <T> authorized(call: suspend (String) -> T): T`
  - `class SignedOutException`, `class SessionExpiredException`
  - Test fake: `FakeVertoServer` (auth + sync, used again in Task 4)

- [ ] **Step 1: Add Ktor**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
ktor = "3.3.0"
```

and under `[libraries]`:

```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

If Gradle cannot resolve 3.3.0, use the newest 3.x release on Maven Central and ledger the ruling.

In `shared/data/build.gradle.kts`, in `commonMain.dependencies { ... }` add:

```kotlin
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
```

and in `commonTest.dependencies { ... }` add:

```kotlin
            implementation(libs.ktor.client.mock)
```

- [ ] **Step 2: Write the fake server (test code)**

`shared/data/src/jvmTest/kotlin/com/verto/data/remote/FakeVertoServer.kt`:

```kotlin
package com.verto.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * In-memory stand-in for the Verto API with the same rules that matter to the client:
 * refresh tokens rotate and a reused one is refused, push upserts by (type, id) with a growing
 * version, pull pages by version.
 */
class FakeVertoServer {

    data class Stored(val entityType: String, val id: String, val payload: JsonElement, val deletedAt: String?, val version: Long)

    val email = "jess@verto.app"
    val password = "senha-certa"

    val validAccess = mutableSetOf<String>()
    private val validRefresh = mutableSetOf<String>()
    private var tokenSeq = 0
    private var version = 0L

    val records = linkedMapOf<Pair<String, String>, Stored>()
    var refreshCalls = 0
    var pushCalls = 0
    var offline = false
    val rejectIds = mutableSetOf<String>()

    /** Runs inside a push, before it is stored: lets a test edit the phone while the push is in flight. */
    var onPush: (suspend () -> Unit)? = null

    /** When set, refresh waits for it: lets a test line up two 401s before the first refresh answers. */
    var refreshGate: CompletableDeferred<Unit>? = null

    val engine = MockEngine { request -> handle(request) }
    val api = VertoApi(vertoHttpClient(engine), "https://fake.verto")

    /** A session the server already accepts, as if she had logged in earlier. */
    fun signedInSession(): Session {
        val auth = issue()
        return Session(auth.accessToken, auth.refreshToken, email)
    }

    fun expireAccessTokens() = validAccess.clear()

    fun revokeRefreshTokens() = validRefresh.clear()

    /** A change made on another device. */
    fun seed(entityType: String, id: String, payload: JsonObject, deletedAt: String? = null) {
        records[entityType to id] = Stored(entityType, id, payload, deletedAt, ++version)
    }

    private fun issue(): AuthBody {
        tokenSeq++
        val access = "access-$tokenSeq"
        val refresh = "refresh-$tokenSeq"
        validAccess += access
        validRefresh += refresh
        return AuthBody(access, refresh, UserBody("00000000-0000-0000-0000-000000000001", email, "Jess"))
    }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        if (offline) throw IOException("sem rede")
        val path = request.url.encodedPath
        val body = if (request.method == HttpMethod.Post) request.body.toByteArray().decodeToString() else ""
        return when (path) {
            "/v1/auth/login" -> {
                val login = ApiJson.decodeFromString(LoginBody.serializer(), body)
                if (login.email == email && login.password == password) {
                    ok(ApiJson.encodeToString(AuthBody.serializer(), issue()))
                } else {
                    failure(HttpStatusCode.Unauthorized, "invalid_credentials")
                }
            }
            "/v1/auth/refresh" -> {
                refreshCalls++
                refreshGate?.await()
                val refresh = ApiJson.decodeFromString(RefreshBody.serializer(), body).refreshToken
                if (validRefresh.remove(refresh)) {
                    ok(ApiJson.encodeToString(AuthBody.serializer(), issue()))
                } else {
                    // Reuse of a rotated token: the real server revokes the family.
                    validRefresh.clear()
                    failure(HttpStatusCode.Unauthorized, "invalid_refresh_token")
                }
            }
            "/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
            "/v1/sync/push" -> authorized(request) {
                pushCalls++
                onPush?.invoke()
                val push = ApiJson.decodeFromString(PushBody.serializer(), body)
                val accepted = mutableListOf<Accepted>()
                val rejected = mutableListOf<Rejected>()
                push.changes.forEach { change ->
                    if (change.id in rejectIds) {
                        rejected += Rejected(change.entityType, change.id, "missing_field:title")
                    } else {
                        version++
                        records[change.entityType to change.id] =
                            Stored(change.entityType, change.id, change.payload, change.deletedAt, version)
                        accepted += Accepted(change.entityType, change.id, version)
                    }
                }
                ok(ApiJson.encodeToString(PushResult.serializer(), PushResult(accepted, rejected)))
            }
            "/v1/sync/pull" -> authorized(request) {
                val since = request.url.parameters["since"]!!.toLong()
                val limit = request.url.parameters["limit"]!!.toInt()
                val rows = records.values.filter { it.version > since }.sortedBy { it.version }.take(limit + 1)
                val page = rows.take(limit)
                val result = PullResult(
                    changes = page.map { ChangeOut(it.entityType, it.id, it.payload, it.deletedAt, it.version) },
                    cursor = page.lastOrNull()?.version ?: since,
                    hasMore = rows.size > limit,
                )
                ok(ApiJson.encodeToString(PullResult.serializer(), result))
            }
            else -> failure(HttpStatusCode.NotFound, "not_found")
        }
    }

    private suspend fun MockRequestHandleScope.authorized(
        request: HttpRequestData,
        block: suspend MockRequestHandleScope.() -> HttpResponseData,
    ): HttpResponseData {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        return if (token != null && token in validAccess) block() else failure(HttpStatusCode.Unauthorized, null)
    }

    private fun MockRequestHandleScope.ok(json: String) =
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.failure(status: HttpStatusCode, code: String?) = respond(
        if (code == null) "" else """{"error":"$code","message":"..."}""",
        status,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
```

- [ ] **Step 3: Write the failing session tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/remote/AuthSessionTest.kt`:

```kotlin
package com.verto.data.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthSessionTest {

    private val server = FakeVertoServer()

    @Test
    fun login_storesTheSession_andSignsIn() = runTest {
        val tokens = InMemoryTokenStore()
        val session = AuthSession(server.api, tokens)

        session.login("  jess@verto.app ", server.password)

        assertEquals(AccountState.SignedIn(server.email), session.state.value)
        assertEquals(server.email, tokens.load()?.email)
    }

    @Test
    fun login_wrongPassword_staysSignedOut() = runTest {
        val session = AuthSession(server.api, InMemoryTokenStore())

        val error = assertFailsWith<UnauthorizedException> { session.login(server.email, "errada") }

        assertEquals("invalid_credentials", error.code)
        assertEquals(AccountState.SignedOut, session.state.value)
    }

    @Test
    fun authorized_withoutASession_isSignedOut() = runTest {
        val session = AuthSession(server.api, InMemoryTokenStore())

        assertFailsWith<SignedOutException> { session.authorized { server.api.pull(it, 0, 10) } }
    }

    @Test
    fun authorized_on401_refreshesOnce_andRetries() = runTest {
        val tokens = InMemoryTokenStore(server.signedInSession())
        val session = AuthSession(server.api, tokens)
        server.expireAccessTokens()

        val page = session.authorized { server.api.pull(it, 0, 10) }

        assertEquals(0, page.changes.size)
        assertEquals(1, server.refreshCalls)
    }

    @Test
    fun concurrent401s_shareOneRefresh() = runTest {
        val session = AuthSession(server.api, InMemoryTokenStore(server.signedInSession()))
        server.expireAccessTokens()
        val gate = CompletableDeferred<Unit>()
        server.refreshGate = gate

        val calls = List(2) { async { session.authorized { server.api.pull(it, 0, 10) } } }
        repeat(5) { yield() }
        gate.complete(Unit)
        calls.awaitAll()

        assertEquals(1, server.refreshCalls)
        assertEquals(AccountState.SignedIn(server.email), session.state.value)
    }

    @Test
    fun refreshRefused_expiresTheSession_andForgetsTheTokens() = runTest {
        val tokens = InMemoryTokenStore(server.signedInSession())
        val session = AuthSession(server.api, tokens)
        server.expireAccessTokens()
        server.revokeRefreshTokens()

        assertFailsWith<SessionExpiredException> { session.authorized { server.api.pull(it, 0, 10) } }

        assertEquals(AccountState.Expired(server.email), session.state.value)
        assertNull(tokens.load())
    }

    @Test
    fun logout_forgetsTheTokens_evenOffline() = runTest {
        val tokens = InMemoryTokenStore(server.signedInSession())
        val session = AuthSession(server.api, tokens)
        server.offline = true

        session.logout()

        assertNull(tokens.load())
        assertEquals(AccountState.SignedOut, session.state.value)
    }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run: `./gradlew :shared:data:jvmTest --tests '*AuthSessionTest*' --console=plain`
Expected: compilation FAIL (`Unresolved reference 'VertoApi'`, `'AuthSession'`…).

- [ ] **Step 5: Write the API models**

`shared/data/src/commonMain/kotlin/com/verto/data/remote/ApiModels.kt`:

```kotlin
package com.verto.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// Mirrors Verto.Application.Dtos (camelCase JSON). Only the fields the app reads are declared.

internal val ApiJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class LoginBody(val email: String, val password: String)

@Serializable
internal data class RefreshBody(val refreshToken: String)

@Serializable
internal data class UserBody(val id: String, val email: String, val displayName: String)

@Serializable
internal data class AuthBody(val accessToken: String, val refreshToken: String, val user: UserBody)

@Serializable
internal data class ErrorBody(val error: String? = null)

@Serializable
internal data class ChangeIn(
    val entityType: String,
    val id: String,
    val payload: JsonElement,
    val deletedAt: String?,
    val clientUpdatedAt: String,
)

@Serializable
internal data class PushBody(val changes: List<ChangeIn>)

@Serializable
internal data class Accepted(val entityType: String, val id: String, val serverVersion: Long)

@Serializable
internal data class Rejected(val entityType: String, val id: String, val error: String)

@Serializable
internal data class PushResult(val accepted: List<Accepted>, val rejected: List<Rejected>)

@Serializable
internal data class ChangeOut(
    val entityType: String,
    val id: String,
    val payload: JsonElement,
    val deletedAt: String? = null,
    val serverVersion: Long,
)

@Serializable
internal data class PullResult(val changes: List<ChangeOut>, val cursor: Long, val hasMore: Boolean)
```

- [ ] **Step 6: Write the API client**

`shared/data/src/commonMain/kotlin/com/verto/data/remote/VertoApi.kt`:

```kotlin
package com.verto.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/** The server said the credentials or tokens are not, or no longer, good. */
class UnauthorizedException(val code: String?) : Exception(code ?: "unauthorized")

/** Any other answer outside 2xx. */
class ServerException(val status: Int, val code: String?) : Exception("HTTP $status ${code.orEmpty()}")

fun vertoHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(ApiJson) }
    expectSuccess = false
}

class VertoApi(private val http: HttpClient, private val baseUrl: String = DEFAULT_BASE_URL) {

    internal suspend fun login(email: String, password: String): AuthBody =
        http.post("$baseUrl/v1/auth/login") { json(LoginBody(email, password)) }.checked().body()

    internal suspend fun refresh(refreshToken: String): AuthBody =
        http.post("$baseUrl/v1/auth/refresh") { json(RefreshBody(refreshToken)) }.checked().body()

    internal suspend fun logout(refreshToken: String) {
        http.post("$baseUrl/v1/auth/logout") { json(RefreshBody(refreshToken)) }.checked()
    }

    internal suspend fun push(accessToken: String, changes: List<ChangeIn>): PushResult =
        http.post("$baseUrl/v1/sync/push") {
            bearerAuth(accessToken)
            json(PushBody(changes))
        }.checked().body()

    internal suspend fun pull(accessToken: String, since: Long, limit: Int): PullResult =
        http.get("$baseUrl/v1/sync/pull") {
            bearerAuth(accessToken)
            parameter("since", since)
            parameter("limit", limit)
        }.checked().body()

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.checked(): HttpResponse {
        if (status.isSuccess()) return this
        val code = try {
            body<ErrorBody>().error
        } catch (_: Exception) {
            null
        }
        if (status == HttpStatusCode.Unauthorized) throw UnauthorizedException(code)
        throw ServerException(status.value, code)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://verto-api.billfolder.app"
    }
}
```

- [ ] **Step 7: Write the session**

`shared/data/src/commonMain/kotlin/com/verto/data/remote/AuthSession.kt`:

```kotlin
package com.verto.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val email: String)

/** Where the tokens live: private, never in Room, never in a backup (see androidApp's PrefsTokenStore). */
interface TokenStore {
    fun load(): Session?
    fun save(session: Session?)
}

class InMemoryTokenStore(private var session: Session? = null) : TokenStore {
    override fun load(): Session? = session
    override fun save(session: Session?) {
        this.session = session
    }
}

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val email: String) : AccountState

    /** The server stopped accepting the refresh token; local data is untouched. */
    data class Expired(val email: String) : AccountState
}

class SignedOutException : Exception("no session")

class SessionExpiredException : Exception("session expired")

class AuthSession(private val api: VertoApi, private val tokens: TokenStore) {

    private val refreshLock = Mutex()
    private val _state = MutableStateFlow(tokens.load()?.let { AccountState.SignedIn(it.email) } ?: AccountState.SignedOut)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    suspend fun login(email: String, password: String) {
        val auth = api.login(email.trim(), password)
        tokens.save(Session(auth.accessToken, auth.refreshToken, auth.user.email))
        _state.value = AccountState.SignedIn(auth.user.email)
    }

    /** Forgets the tokens first: being offline must never keep her signed in. Telling the server is best-effort. */
    suspend fun logout() {
        val session = tokens.load() ?: return
        tokens.save(null)
        _state.value = AccountState.SignedOut
        try {
            api.logout(session.refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The token simply expires on its own.
        }
    }

    suspend fun <T> authorized(call: suspend (accessToken: String) -> T): T {
        val session = tokens.load() ?: throw SignedOutException()
        return try {
            call(session.accessToken)
        } catch (_: UnauthorizedException) {
            call(refreshedAfter(session.accessToken))
        }
    }

    /**
     * One refresh at a time: the server rotates refresh tokens and treats a reused one as stolen,
     * revoking the whole family. A caller that waited finds the new token already there.
     */
    private suspend fun refreshedAfter(staleAccessToken: String): String = refreshLock.withLock {
        val current = tokens.load() ?: throw SignedOutException()
        if (current.accessToken != staleAccessToken) return current.accessToken
        val auth = try {
            api.refresh(current.refreshToken)
        } catch (_: UnauthorizedException) {
            tokens.save(null)
            _state.value = AccountState.Expired(current.email)
            throw SessionExpiredException()
        }
        tokens.save(current.copy(accessToken = auth.accessToken, refreshToken = auth.refreshToken))
        auth.accessToken
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest --console=plain`
Expected: PASS (all earlier tests too).

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml shared/data
git commit -m "feat(sync): cliente da API com sessão que renova o token uma vez só"
```

---

### Task 4: Sync engine

**Files:**
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/sync/SyncEngine.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/sync/SyncEngineTest.kt`

**Interfaces:**
- Consumes: `LocalStore.pendingBatch / markSent / applyRemote / cursor / setLastSyncedAt` (Task 2); `VertoApi.push / pull`, `AuthSession.authorized`, `SignedOutException`, `SessionExpiredException` (Task 3); `iso()` from `Payloads.kt` (Task 1).
- Produces:
  - `sealed interface SyncOutcome { data class Synced(val at: Instant, val someRejected: Boolean); data object SignedOut; data object SessionExpired; data class Failed(val cause: Throwable) }`
  - `class SyncEngine(store: LocalStore, api: VertoApi, session: AuthSession, clock: Clock, pushBatch: Int = 200, pullPage: Int = 500)` with `suspend fun sync(): SyncOutcome`

- [ ] **Step 1: Write the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/sync/SyncEngineTest.kt`:

```kotlin
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
```

The `DataGraph` used here already exists in `shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt` and exposes `clock`, `store`, `db`, `inbox`, `tasks`, `routines`, `days`. `DataGraph` uses `SequentialIds` ("id-1"…), which are not GUIDs; the fake server does not check the format, the real one does (covered by `UuidIdGenerator` in production). If any repository method named above differs in this codebase (`tapAnchor`, `skip`, `setEnergy`, `capture`), use the existing one and ledger it.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:data:jvmTest --tests '*SyncEngineTest*' --console=plain`
Expected: compilation FAIL, `Unresolved reference 'SyncEngine'`.

- [ ] **Step 3: Write the engine**

`shared/data/src/commonMain/kotlin/com/verto/data/sync/SyncEngine.kt`:

```kotlin
package com.verto.data.sync

import com.verto.data.db.OutboxEntity
import com.verto.data.remote.AuthSession
import com.verto.data.remote.ChangeIn
import com.verto.data.remote.ChangeOut
import com.verto.data.remote.SessionExpiredException
import com.verto.data.remote.SignedOutException
import com.verto.data.remote.VertoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface SyncOutcome {
    data class Synced(val at: Instant, val someRejected: Boolean) : SyncOutcome
    data object SignedOut : SyncOutcome
    data object SessionExpired : SyncOutcome
    data class Failed(val cause: Throwable) : SyncOutcome
}

/**
 * Push first, then pull: a local change is on the server before the pull runs, so the
 * "last write to reach the server wins" rule never undoes something done on this phone.
 */
class SyncEngine(
    private val store: LocalStore,
    private val api: VertoApi,
    private val session: AuthSession,
    private val clock: Clock,
    private val pushBatch: Int = 200,
    private val pullPage: Int = 500,
) {
    private val running = Mutex()

    suspend fun sync(): SyncOutcome = running.withLock {
        try {
            val someRejected = push()
            pull()
            val now = clock.now()
            store.setLastSyncedAt(now)
            SyncOutcome.Synced(now, someRejected)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SignedOutException) {
            SyncOutcome.SignedOut
        } catch (_: SessionExpiredException) {
            SyncOutcome.SessionExpired
        } catch (e: Exception) {
            SyncOutcome.Failed(e)
        }
    }

    private suspend fun push(): Boolean {
        var someRejected = false
        while (true) {
            val batch = store.pendingBatch(pushBatch)
            if (batch.isEmpty()) return someRejected
            val result = session.authorized { token -> api.push(token, batch.map { it.toChangeIn() }) }
            someRejected = someRejected || result.rejected.isNotEmpty()
            // Refused entries leave too: the server would refuse them the same way forever and block
            // the rest. The record itself stays on this phone untouched.
            store.markSent(batch.map { it.seq })
        }
    }

    private suspend fun pull() {
        var since = store.cursor()
        do {
            val page = session.authorized { token -> api.pull(token, since, pullPage) }
            store.applyRemote(page.changes.map { it.toRemoteChange() }, page.cursor, clock.now().toEpochMilliseconds())
            since = page.cursor
        } while (page.hasMore)
    }

    private fun OutboxEntity.toChangeIn() = ChangeIn(
        entityType = entityType,
        id = entityId,
        payload = Json.parseToJsonElement(payload),
        deletedAt = deletedAt?.iso(),
        clientUpdatedAt = clientUpdatedAt.iso(),
    )

    private fun ChangeOut.toRemoteChange() = RemoteChange(
        entityType = entityType,
        id = id,
        payload = payload.toString(),
        deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilliseconds() },
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/data
git commit -m "feat(sync): motor de sync (push do outbox, pull paginado, a mudança local vence)"
```

---

### Task 5: Scheduler and status

**Files:**
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/sync/SyncScheduler.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/sync/SyncSchedulerTest.kt`

**Interfaces:**
- Consumes: `SyncOutcome` (Task 4), `LocalStore.observePendingCount / lastSyncedAt` (Task 2).
- Produces:
  - `enum class SyncPhase { Idle, Running, Offline, SignedOut, SessionExpired }`
  - `data class SyncStatus(val phase: SyncPhase, val lastSyncedAt: Instant?, val someRejected: Boolean = false)`
  - `class SyncScheduler(sync: suspend () -> SyncOutcome, pendingChanges: Flow<Int>, lastSyncedAt: suspend () -> Instant?, scope: CoroutineScope, debounce: Duration = 3.seconds, backoff: List<Duration> = listOf(10.seconds, 30.seconds, 2.minutes, 10.minutes))` with `val status: StateFlow<SyncStatus>`, `fun start()`, `fun syncNow()` (starts the scheduler if it was not started)
  - `fun SyncScheduler.Companion.of(engine: SyncEngine, store: LocalStore, scope: CoroutineScope): SyncScheduler`

- [ ] **Step 1: Write the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/sync/SyncSchedulerTest.kt`:

```kotlin
package com.verto.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncSchedulerTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val pending = MutableStateFlow(0)
    private val outcomes = ArrayDeque<SyncOutcome>()
    private var calls = 0

    private suspend fun fakeSync(): SyncOutcome {
        calls++
        return outcomes.removeFirstOrNull() ?: SyncOutcome.Synced(at, someRejected = false)
    }

    private fun kotlinx.coroutines.test.TestScope.scheduler() =
        SyncScheduler(::fakeSync, pending, { null }, backgroundScope)

    @Test
    fun start_syncsRightAway() = runTest {
        val scheduler = scheduler()

        scheduler.start()
        runCurrent()

        assertEquals(1, calls)
        assertEquals(SyncStatus(SyncPhase.Idle, at), scheduler.status.value)
    }

    @Test
    fun aLocalChange_syncsAfterTheQuietPeriod() = runTest {
        scheduler().start()
        runCurrent()

        pending.value = 1
        advanceTimeBy(2.9.seconds)
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(0.2.seconds)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun failures_retryWithBackoff_untilItWorks() = runTest {
        outcomes += SyncOutcome.Failed(IOException("sem rede"))
        outcomes += SyncOutcome.Failed(IOException("sem rede"))
        val scheduler = scheduler()

        scheduler.start()
        runCurrent()
        assertEquals(SyncPhase.Offline, scheduler.status.value.phase)

        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(2, calls)

        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(3, calls)
        assertEquals(SyncPhase.Idle, scheduler.status.value.phase)
    }

    @Test
    fun signedOut_waitsForHerInsteadOfRetrying() = runTest {
        outcomes += SyncOutcome.SignedOut
        val scheduler = scheduler()

        scheduler.start()
        advanceTimeBy(20.seconds)
        runCurrent()

        assertEquals(1, calls)
        assertEquals(SyncPhase.SignedOut, scheduler.status.value.phase)
    }

    @Test
    fun syncNow_beforeStart_startsIt() = runTest {
        scheduler().syncNow()
        runCurrent()

        assertEquals(1, calls)
    }

    @Test
    fun syncNow_runsAnotherRound() = runTest {
        val scheduler = scheduler()
        scheduler.start()
        runCurrent()

        scheduler.syncNow()
        runCurrent()

        assertEquals(2, calls)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:data:jvmTest --tests '*SyncSchedulerTest*' --console=plain`
Expected: compilation FAIL, `Unresolved reference 'SyncScheduler'`.

- [ ] **Step 3: Write the scheduler**

`shared/data/src/commonMain/kotlin/com/verto/data/sync/SyncScheduler.kt`:

```kotlin
package com.verto.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class SyncPhase { Idle, Running, Offline, SignedOut, SessionExpired }

data class SyncStatus(val phase: SyncPhase, val lastSyncedAt: Instant?, val someRejected: Boolean = false)

/**
 * Decides when to sync: right away, a few quiet seconds after local changes, and again with
 * backoff while offline. Runs one sync at a time; requests made meanwhile collapse into one.
 */
class SyncScheduler(
    private val sync: suspend () -> SyncOutcome,
    private val pendingChanges: Flow<Int>,
    private val lastSyncedAt: suspend () -> Instant?,
    private val scope: CoroutineScope,
    private val debounce: Duration = 3.seconds,
    private val backoff: List<Duration> = listOf(10.seconds, 30.seconds, 2.minutes, 10.minutes),
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val _status = MutableStateFlow(SyncStatus(SyncPhase.Idle, null))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var started = false
    private var retry: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val saved = lastSyncedAt()
            _status.update { if (it.lastSyncedAt == null) it.copy(lastSyncedAt = saved) else it }
        }
        scope.launch {
            // drop(1): the first value is what is already pending, covered by the sync at start.
            pendingChanges.drop(1).filter { it > 0 }.debounce(debounce).collect { requests.trySend(Unit) }
        }
        scope.launch {
            var failures = 0
            requests.trySend(Unit)
            for (request in requests) {
                retry?.cancel()
                _status.update { it.copy(phase = SyncPhase.Running) }
                when (val outcome = sync()) {
                    is SyncOutcome.Synced -> {
                        failures = 0
                        _status.value = SyncStatus(SyncPhase.Idle, outcome.at, outcome.someRejected)
                    }
                    SyncOutcome.SignedOut -> _status.update { it.copy(phase = SyncPhase.SignedOut) }
                    SyncOutcome.SessionExpired -> _status.update { it.copy(phase = SyncPhase.SessionExpired) }
                    is SyncOutcome.Failed -> {
                        _status.update { it.copy(phase = SyncPhase.Offline) }
                        val wait = backoff[minOf(failures, backoff.lastIndex)]
                        failures++
                        retry = scope.launch {
                            delay(wait)
                            requests.trySend(Unit)
                        }
                    }
                }
            }
        }
    }

    /** Asks for a sync; starts the scheduler if nothing has yet (e.g. right after login). */
    fun syncNow() {
        if (!started) start() else requests.trySend(Unit)
    }

    companion object {
        fun of(engine: SyncEngine, store: LocalStore, scope: CoroutineScope) =
            SyncScheduler({ engine.sync() }, store.observePendingCount(), { store.lastSyncedAt() }, scope)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/data
git commit -m "feat(sync): agendador (na abertura, depois de cada mudança e com espera crescente sem rede)"
```

---

### Task 6: "Conta e sync" screen

**Files:**
- Modify: `composeApp/build.gradle.kts` (test dependency)
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/account/SyncStatusLine.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/account/AccountViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/account/AccountScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/Routes.kt`, `VertoNavHost.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`
- Test: `composeApp/src/commonTest/kotlin/com/verto/ui/account/SyncStatusLineTest.kt`, `composeApp/src/jvmTest/kotlin/com/verto/ui/account/AccountViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthSession`, `AccountState`, `UnauthorizedException`, `VertoApi`, `vertoHttpClient`, `InMemoryTokenStore` (Task 3); `SyncScheduler`, `SyncStatus`, `SyncPhase`, `SyncEngine` (Tasks 4–5).
- Produces: `fun syncStatusLine(account: AccountState, status: SyncStatus, now: Instant, zone: TimeZone): String`; `AccountViewModel(session: AuthSession, scheduler: SyncScheduler, clock: Clock, zones: ZoneProvider)`; `AccountRoute`; `PlanScreen(onOpenAccount = ...)`.
- Koin (common module) gains: `VertoApi`, `AuthSession`, `SyncEngine`, `SyncScheduler` singletons; the platform module must provide `TokenStore` and `HttpClient` (Task 7).

- [ ] **Step 1: Write the failing tests**

`composeApp/src/commonTest/kotlin/com/verto/ui/account/SyncStatusLineTest.kt`:

```kotlin
package com.verto.ui.account

import com.verto.data.remote.AccountState
import com.verto.data.sync.SyncPhase
import com.verto.data.sync.SyncStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusLineTest {

    private val zone = TimeZone.of("America/Sao_Paulo")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 10, day, hour, minute).toInstant(zone)
    private val signedIn = AccountState.SignedIn("jess@verto.app")

    @Test
    fun signedOutOrExpired_comesFirst() {
        val status = SyncStatus(SyncPhase.Idle, at(15, 9))
        assertEquals("Entre pra guardar seus dados no servidor.", syncStatusLine(AccountState.SignedOut, status, at(15, 10), zone))
        assertEquals(
            "Sua sessão expirou. Entra de novo: seus dados continuam aqui.",
            syncStatusLine(AccountState.Expired("jess@verto.app"), status, at(15, 10), zone),
        )
    }

    @Test
    fun phases_readCalmly() {
        assertEquals("Sincronizando…", syncStatusLine(signedIn, SyncStatus(SyncPhase.Running, null), at(15, 10), zone))
        assertEquals(
            "Sem conexão agora. Tento de novo sozinho.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Offline, at(15, 9)), at(15, 10), zone),
        )
        assertEquals("Ainda não sincronizou.", syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, null), at(15, 10), zone))
    }

    @Test
    fun lastSync_saysWhen() {
        assertEquals(
            "Tudo guardado. Última vez hoje às 09:05.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, at(15, 9, 5)), at(15, 10), zone),
        )
        assertEquals(
            "Tudo guardado. Última vez dia 12/10 às 22:40.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, at(12, 22, 40)), at(15, 10), zone),
        )
    }

    @Test
    fun refusedRecords_getOneQuietSentence() {
        assertEquals(
            "Tudo guardado. Última vez hoje às 09:05. Algumas mudanças não foram aceitas pelo servidor.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, at(15, 9, 5), someRejected = true), at(15, 10), zone),
        )
    }
}
```

`composeApp/src/jvmTest/kotlin/com/verto/ui/account/AccountViewModelTest.kt`:

```kotlin
package com.verto.ui.account

import com.verto.data.ZoneProvider
import com.verto.data.remote.AccountState
import com.verto.data.remote.AuthSession
import com.verto.data.remote.InMemoryTokenStore
import com.verto.data.remote.VertoApi
import com.verto.data.remote.vertoHttpClient
import com.verto.data.sync.SyncOutcome
import com.verto.data.sync.SyncScheduler
import com.verto.ui.TestClock
import com.verto.ui.TestZone
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountViewModelTest : ViewModelTest() {

    private var answer: String = "ok"
    private var syncs = 0

    private val engine = MockEngine { _ ->
        when (answer) {
            "ok" -> respond(
                """{"accessToken":"a","refreshToken":"r","user":{"id":"1","email":"jess@verto.app","displayName":"Jess"}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
            "wrong" -> respond(
                """{"error":"invalid_credentials"}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> throw IOException("sem rede")
        }
    }

    private fun viewModel(): AccountViewModel {
        val session = AuthSession(VertoApi(vertoHttpClient(engine), "https://fake.verto"), InMemoryTokenStore())
        val scheduler = SyncScheduler(
            sync = {
                syncs++
                SyncOutcome.SignedOut
            },
            pendingChanges = flowOf(0),
            lastSyncedAt = { null },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        return AccountViewModel(session, scheduler, TestClock(uiAt(15, 10)), ZoneProvider { TestZone })
    }

    @Test
    fun login_signsIn_clearsThePassword_andSyncs() = runTest {
        val vm = viewModel()
        vm.setEmail("jess@verto.app")
        vm.setPassword("segredo")

        vm.login()

        val state = vm.state.first { it.account is AccountState.SignedIn }
        assertEquals("", state.password)
        assertEquals(1, syncs)
    }

    @Test
    fun wrongPassword_explainsWithoutDrama() = runTest {
        answer = "wrong"
        val vm = viewModel()
        vm.setEmail("jess@verto.app")
        vm.setPassword("errada")

        vm.login()

        assertEquals("E-mail ou senha não conferem.", vm.state.first { it.message != null }.message)
    }

    @Test
    fun offline_saysSoAndKeepsWhatSheTyped() = runTest {
        answer = "offline"
        val vm = viewModel()
        vm.setEmail("jess@verto.app")
        vm.setPassword("segredo")

        vm.login()

        val state = vm.state.first { it.message != null }
        assertEquals("Sem conexão agora. Tenta de novo daqui a pouco.", state.message)
        assertEquals("jess@verto.app", state.email)
    }

    @Test
    fun blankFields_askForThem() = runTest {
        val vm = viewModel()

        vm.login()

        assertEquals("Preenche e-mail e senha.", vm.state.value.message)
    }
}
```

In `composeApp/build.gradle.kts`, inside `commonTest.dependencies { ... }`, add:

```kotlin
            implementation(libs.ktor.client.mock)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :composeApp:jvmTest --console=plain`
Expected: compilation FAIL (`Unresolved reference 'syncStatusLine'`, `'AccountViewModel'`).

- [ ] **Step 3: Write the status line**

`composeApp/src/commonMain/kotlin/com/verto/ui/account/SyncStatusLine.kt`:

```kotlin
package com.verto.ui.account

import com.verto.data.remote.AccountState
import com.verto.data.sync.SyncPhase
import com.verto.data.sync.SyncStatus
import com.verto.ui.common.hhmm
import com.verto.ui.common.shortPt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The one place sync speaks: calm, no counts, no "erro". */
fun syncStatusLine(account: AccountState, status: SyncStatus, now: Instant, zone: TimeZone): String {
    when (account) {
        AccountState.SignedOut -> return "Entre pra guardar seus dados no servidor."
        is AccountState.Expired -> return "Sua sessão expirou. Entra de novo: seus dados continuam aqui."
        is AccountState.SignedIn -> Unit
    }
    val line = when (status.phase) {
        SyncPhase.Running -> "Sincronizando…"
        SyncPhase.Offline -> "Sem conexão agora. Tento de novo sozinho."
        SyncPhase.SignedOut -> "Entre pra guardar seus dados no servidor."
        SyncPhase.SessionExpired -> "Sua sessão expirou. Entra de novo: seus dados continuam aqui."
        SyncPhase.Idle -> status.lastSyncedAt?.let { last ->
            val local = last.toLocalDateTime(zone)
            val today = now.toLocalDateTime(zone).date
            val day = if (local.date == today) "hoje" else "dia ${local.date.shortPt()}"
            "Tudo guardado. Última vez $day às ${local.time.hhmm()}."
        } ?: "Ainda não sincronizou."
    }
    return if (status.someRejected && status.phase == SyncPhase.Idle) {
        "$line Algumas mudanças não foram aceitas pelo servidor."
    } else {
        line
    }
}
```

`shortPt()` (dd/MM) and `hhmm()` already exist in `com.verto.ui.common` (`Formats.kt`). If `shortPt` is private there, make it `internal`/public and ledger it.

- [ ] **Step 4: Write the view model**

`composeApp/src/commonMain/kotlin/com/verto/ui/account/AccountViewModel.kt`:

```kotlin
package com.verto.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.ZoneProvider
import com.verto.data.remote.AccountState
import com.verto.data.remote.AuthSession
import com.verto.data.remote.UnauthorizedException
import com.verto.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class AccountForm(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

data class AccountUiState(
    val account: AccountState = AccountState.SignedOut,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val syncLine: String = "",
)

class AccountViewModel(
    private val session: AuthSession,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {

    private val form = MutableStateFlow(AccountForm())

    val state: StateFlow<AccountUiState> = combine(session.state, scheduler.status, form) { account, status, f ->
        AccountUiState(
            account = account,
            email = f.email,
            password = f.password,
            busy = f.busy,
            message = f.message,
            syncLine = syncStatusLine(account, status, clock.now(), zones.current()),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState(account = session.state.value))

    fun setEmail(value: String) = form.update { it.copy(email = value, message = null) }

    fun setPassword(value: String) = form.update { it.copy(password = value, message = null) }

    fun login() {
        val current = form.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isBlank()) {
            form.update { it.copy(message = "Preenche e-mail e senha.") }
            return
        }
        form.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                session.login(current.email, current.password)
                scheduler.syncNow()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: UnauthorizedException) {
                "E-mail ou senha não conferem."
            } catch (_: Exception) {
                "Sem conexão agora. Tenta de novo daqui a pouco."
            }
            form.update {
                if (message == null) AccountForm() else it.copy(busy = false, message = message)
            }
        }
    }

    fun syncNow() = scheduler.syncNow()

    fun logout() {
        viewModelScope.launch { session.logout() }
    }
}
```

- [ ] **Step 5: Write the screen**

`composeApp/src/commonMain/kotlin/com/verto/ui/account/AccountScreen.kt`:

```kotlin
package com.verto.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.data.remote.AccountState
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountScreen(onClose: () -> Unit, viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EditorScaffold(title = "Conta e sync", onBack = onClose) {
        Text(state.syncLine, style = MaterialTheme.typography.bodyLarge)
        when (val account = state.account) {
            is AccountState.SignedIn -> {
                Text(account.email, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar agora") }
                TextButton(onClick = viewModel::logout) { Text("Sair desta conta") }
                Hint("Sair não apaga nada deste celular.")
            }
            else -> {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::setEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(onClick = viewModel::login, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Entrar") }
                state.message?.let { Hint(it) }
            }
        }
    }
}
```

- [ ] **Step 6: Wire route, Planejar button and Koin**

Append to `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/Routes.kt`:

```kotlin

@Serializable
data object AccountRoute
```

In `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt`, replace the `PlanScreen(` call's argument list so it also passes `onOpenAccount = { nav.navigate(AccountRoute) },`, and add after the `composable<AnchorEditorRoute> { ... }` block:

```kotlin
            composable<AccountRoute> { AccountScreen(onClose = { nav.popBackStack() }) }
```

(import `com.verto.ui.account.AccountScreen`).

In `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt`:
- add the parameter `onOpenAccount: () -> Unit,` after `onOpenAnchor`;
- add as the first item of the `LazyColumn`, before `item { SectionHeader("Tarefas", ...`:

```kotlin
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onOpenAccount) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "Conta e sync")
                }
            }
        }
```

(imports: `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.AccountCircle`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton` — skip any already present).

In `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`, change the doc comment to `/** Expects a VertoDatabase, a TokenStore and an HttpClient from the platform module (androidApp). */` and add inside `module { ... }`, after `singleOf(::DayRepository)`:

```kotlin
    single { VertoApi(get()) }
    single { AuthSession(get(), get()) }
    single { SyncEngine(get(), get(), get(), get()) }
    // Lives as long as the process: syncing must not stop when a screen goes away.
    single { SyncScheduler.of(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    viewModelOf(::AccountViewModel)
```

(imports: `com.verto.data.remote.AuthSession`, `com.verto.data.remote.VertoApi`, `com.verto.data.sync.SyncEngine`, `com.verto.data.sync.SyncScheduler`, `com.verto.ui.account.AccountViewModel`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.SupervisorJob`).

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest :composeApp:jvmTest --console=plain`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add composeApp shared/data
git commit -m "feat(sync): tela Conta e sync com login, status e sair"
```

---

### Task 7: Android wiring

**Files:**
- Modify: `gradle/libs.versions.toml`, `androidApp/build.gradle.kts`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/res/xml/backup_rules.xml`, `androidApp/src/main/res/xml/data_extraction_rules.xml`
- Create: `androidApp/src/main/kotlin/com/verto/android/PrefsTokenStore.kt`, `androidApp/src/main/kotlin/com/verto/android/SyncWorker.kt`
- Modify: `androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`, `MainActivity.kt`

**Interfaces:**
- Consumes: `TokenStore`, `Session`, `vertoHttpClient` (Task 3); `SyncEngine`, `SyncOutcome`, `SyncScheduler` (Tasks 4–5); Koin `vertoModule` (Task 6).
- Produces: an installable app that syncs against `https://verto-api.billfolder.app`.

This task has no unit test: it is platform glue checked by building and by Task 8.

- [ ] **Step 1: Dependencies**

In `gradle/libs.versions.toml` add under `[versions]`:

```toml
work = "2.10.0"
```

and under `[libraries]`:

```toml
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
```

In `androidApp/build.gradle.kts`, inside `dependencies { ... }` add:

```kotlin
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.work.runtime)
```

- [ ] **Step 2: Token store outside backups**

`androidApp/src/main/kotlin/com/verto/android/PrefsTokenStore.kt`:

```kotlin
package com.verto.android

import android.content.Context
import com.verto.data.remote.Session
import com.verto.data.remote.TokenStore
import kotlinx.serialization.json.Json

/**
 * App-private and excluded from backup (res/xml/*_rules.xml): a restored token on a new phone
 * would be a reused refresh token, which makes the server revoke the whole session family.
 */
class PrefsTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): Session? = prefs.getString(KEY, null)?.let {
        try {
            Json.decodeFromString(Session.serializer(), it)
        } catch (_: Exception) {
            null
        }
    }

    override fun save(session: Session?) {
        val editor = prefs.edit()
        if (session == null) editor.remove(KEY) else editor.putString(KEY, Json.encodeToString(Session.serializer(), session))
        editor.apply()
    }

    companion object {
        const val FILE = "verto_session"
        private const val KEY = "session"
    }
}
```

`androidApp/src/main/res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="verto_session.xml" />
</full-backup-content>
```

`androidApp/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="verto_session.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="verto_session.xml" />
    </device-transfer>
</data-extraction-rules>
```

`androidApp/build.gradle.kts` must apply the serialization plugin for `Session.serializer()` only if it fails to resolve; `Session` is compiled in `shared/data`, which already has the plugin, so no change is expected. Add `implementation(libs.kotlinx.serialization.json)` to the androidApp dependencies if `kotlinx.serialization.json.Json` does not resolve.

- [ ] **Step 3: Manifest**

In `androidApp/src/main/AndroidManifest.xml`:
- add, right after the opening `<manifest ...>` tag: `<uses-permission android:name="android.permission.INTERNET" />`
- add to `<application ...>` the attributes `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"`.

- [ ] **Step 4: Background worker**

`androidApp/src/main/kotlin/com/verto/android/SyncWorker.kt`:

```kotlin
package com.verto.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.verto.data.sync.SyncEngine
import com.verto.data.sync.SyncOutcome
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** Catches changes from other devices while the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result = when (engine.sync()) {
        is SyncOutcome.Failed -> Result.retry()
        else -> Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("verto-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **Step 5: Start everything**

Replace the whole of `androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`:

`androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`:

```kotlin
package com.verto.android

import android.app.Application
import com.verto.data.db.vertoDatabase
import com.verto.data.remote.TokenStore
import com.verto.data.remote.vertoHttpClient
import com.verto.data.sync.SyncScheduler
import com.verto.ui.di.vertoModule
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class VertoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@VertoApplication)
            modules(
                module {
                    single { vertoDatabase(androidContext()) }
                    single<TokenStore> { PrefsTokenStore(androidContext()) }
                    single { vertoHttpClient(OkHttp.create()) }
                },
                vertoModule,
            )
        }.koin
        koin.get<SyncScheduler>().start()
        SyncWorker.schedule(this)
    }
}
```

Replace the whole of `androidApp/src/main/kotlin/com/verto/android/MainActivity.kt`:

`androidApp/src/main/kotlin/com/verto/android/MainActivity.kt`:

```kotlin
package com.verto.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.verto.data.sync.SyncScheduler
import com.verto.ui.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val scheduler: SyncScheduler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    // Opening the app (or coming back to it) is when fresh data matters most.
    override fun onStart() {
        super.onStart()
        scheduler.syncNow()
    }
}
```

- [ ] **Step 6: Build and run every test**

Run: `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest :androidApp:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml androidApp
git commit -m "feat(sync): Android com OkHttp, tokens fora do backup e WorkManager de hora em hora"
```

---

### Task 8: Run it against the real API

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: the whole branch.
- Produces: a README section on sync; a verified install.

The account password is hers: whoever executes this task must not type it. She logs in on the device herself.

- [ ] **Step 1: README section**

Append to `README.md`:

````markdown

## Sync

O app sincroniza com a API em `https://verto-api.billfolder.app` (repo `verto-api`). Login em Planejar → ícone de conta → "Conta e sync".

- Toda mudança vai pro Room e pro `outbox` na mesma transação; o sync manda o outbox e depois puxa o que mudou desde o último cursor.
- Roda ao abrir o app, uns segundos depois de cada mudança, com espera crescente sem rede e de hora em hora pelo WorkManager.
- Se um registro tem mudança local pendente, a versão do servidor é ignorada até o próximo push.
- Os tokens ficam em SharedPreferences privadas, fora do backup do Android.

```bash
./gradlew :shared:data:jvmTest --tests '*Sync*'
```
````

- [ ] **Step 2: Install on the emulator and check the signed-out path**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:installDebug
```

Expected: Planejar shows the account icon; "Conta e sync" shows "Entre pra guardar seus dados no servidor."; the rest of the app works as before (data from version 1 still there).

- [ ] **Step 3: Hand the device checks to her**

Ask her to, on her phone after installing (`./gradlew :androidApp:installDebug` with the phone selected):
1. Planejar → conta → log in with the owner account. Expected: within a few seconds "Tudo guardado. Última vez hoje às HH:MM."
2. Create a task, wait ~5 s, reopen "Conta e sync". Expected: the time moved.
3. Airplane mode, create a task, reopen "Conta e sync". Expected: "Sem conexão agora. Tento de novo sozinho." and the app works normally. Turn the network back on. Expected: back to "Tudo guardado" within a minute or two.
4. Restore check on the emulator (her typing the password there): log in on the emulator. Expected: her phone's tasks, routines and anchors appear on the emulator.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: README com o sync"
```
