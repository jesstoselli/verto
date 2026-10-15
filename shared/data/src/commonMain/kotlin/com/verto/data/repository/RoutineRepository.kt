package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.ZoneProvider
import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.data.toWire
import com.verto.domain.SettingsSource
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.rules.AnchorRules
import com.verto.domain.rules.RoutineRules
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoutineRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val zones: ZoneProvider,
    private val settings: SettingsSource,
) {
    private val anchorDao = store.db.anchorDao()
    private val routineDao = store.db.routineDao()
    private val stepDao = store.db.stepDao()

    fun observeAnchors(): Flow<List<Anchor>> = anchorDao.observeAnchors().map { rows -> rows.map { it.toDomain() } }

    fun observeRoutines(): Flow<List<Routine>> = routineDao.observeRoutines().map { rows -> rows.map { it.toDomain() } }

    fun observeDay(day: LocalDate): Flow<RoutineDay> = combine(
        observeAnchors(),
        observeRoutines(),
        anchorDao.observeEventsOn(day.toString()),
        routineDao.observeLogsOn(day.toString()),
    ) { anchors, routines, events, logs ->
        RoutineDay(anchors, routines, events.map { it.toDomain() }, logs.map { it.toDomain() })
    }

    suspend fun anchorDraft(id: String): AnchorDraft? =
        anchorDao.getAnchor(id)?.takeIf { it.deletedAt == null }?.toDomain()?.let {
            AnchorDraft(it.id, it.name, it.windowStart, it.windowEnd)
        }

    suspend fun saveAnchor(draft: AnchorDraft): String {
        val id = draft.id ?: ids.newId()
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            put(AnchorEntity(id, draft.name.trim(), draft.windowStart.toString(), draft.windowEnd.toString(), nowMs, null))
        }
        return id
    }

    /** Refused while routines still use the anchor: they would silently vanish from Hoje. */
    suspend fun deleteAnchor(id: String): Boolean {
        val nowMs = clock.now().toEpochMilliseconds()
        return store.write {
            if (routineDao.forAnchor(id).isNotEmpty()) return@write false
            anchorDao.getAnchor(id)?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            true
        }
    }

    suspend fun routineDraft(id: String): RoutineDraft? {
        val row = routineDao.getRoutine(id)?.takeIf { it.deletedAt == null } ?: return null
        val steps = stepDao.forRoutine(id)
        fun plan(plan: Plan) = steps.filter { it.plan == plan.wire() }
            .sortedBy { it.order }
            .map { StepDraft(it.id, it.title, it.energy.toEnum()) }
        val routine = row.toDomain()
        return RoutineDraft(routine.id, routine.name, routine.anchorId, routine.weekdays, plan(Plan.A), plan(Plan.C))
    }

    suspend fun saveRoutine(draft: RoutineDraft): String {
        val id = draft.id ?: ids.newId()
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            put(RoutineEntity(id, draft.name.trim(), draft.anchorId, draft.weekdays.toWire(), nowMs, null))
            val current = stepDao.forRoutine(id)
            val kept = (draft.stepsA + draft.stepsC).mapNotNull { it.id }.toSet()
            current.filter { it.id !in kept }.forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            listOf(Plan.A to draft.stepsA, Plan.C to draft.stepsC).forEach { (plan, steps) ->
                steps.forEachIndexed { index, step ->
                    put(
                        StepEntity(
                            id = step.id ?: ids.newId(),
                            taskId = null,
                            routineId = id,
                            plan = plan.wire(),
                            order = index,
                            title = step.title.trim(),
                            energy = step.energy.wire(),
                            doneAt = current.firstOrNull { it.id == step.id }?.doneAt,
                            updatedAt = nowMs,
                            deletedAt = null,
                        ),
                    )
                }
            }
        }
        return id
    }

    suspend fun deleteRoutine(id: String) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            routineDao.getRoutine(id)?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            stepDao.forRoutine(id).forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
        }
    }

    suspend fun tapAnchor(anchorId: String, day: LocalDate) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val anchor = anchorDao.getAnchor(anchorId)?.toDomain() ?: return@write
            val events = anchorDao.eventsOn(day.toString()).map { it.toDomain() }
            if (AnchorRules.canTap(anchor, day, events)) {
                put(AnchorEventEntity(ids.newId(), anchorId, day.toString(), nowMs, AnchorTrigger.Tap.wire(), nowMs, null))
            }
        }
    }

    suspend fun recordOpenedWindows(day: LocalDate) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        store.write {
            val anchors = anchorDao.activeAnchors().map { it.toDomain() }
            val events = anchorDao.eventsOn(day.toString()).map { it.toDomain() }
            AnchorRules.windowsToRecord(anchors, day, events, now, zones.current(), settings.current().dayStartsAt).forEach {
                put(AnchorEventEntity(ids.newId(), it.id, day.toString(), nowMs, AnchorTrigger.Window.wire(), nowMs, null))
            }
        }
    }

    /** Checks or unchecks a routine step for [day] and keeps that day's log in step with what is complete. */
    suspend fun toggleStep(stepId: String, day: LocalDate) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        val zone = zones.current()
        store.write {
            val row = stepDao.get(stepId) ?: return@write
            val routineId = row.routineId ?: return@write
            val doneToday = RoutineRules.isDoneOn(row.toDomain(), day, zone, settings.current().dayStartsAt)
            put(row.copy(doneAt = if (doneToday) null else nowMs, updatedAt = nowMs))

            val steps = stepDao.forRoutine(routineId).map { it.toDomain() }
            val outcome = RoutineRules.completedOutcome(routineId, steps, day, zone, settings.current().dayStartsAt)
            val log = routineDao.logFor(routineId, day.toString())
            when {
                outcome != null && log == null ->
                    put(RoutineLogEntity(ids.newId(), routineId, day.toString(), outcome.wire(), nowMs, null))
                outcome != null && log != null && log.outcome != outcome.wire() ->
                    put(log.copy(outcome = outcome.wire(), updatedAt = nowMs))
                outcome == null && log != null && log.outcome != RoutineOutcome.Skipped.wire() ->
                    put(log.copy(deletedAt = nowMs, updatedAt = nowMs))
            }
        }
    }

    suspend fun skip(routineId: String, day: LocalDate) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val log = routineDao.logFor(routineId, day.toString())
            val skipped = RoutineOutcome.Skipped.wire()
            put(log?.copy(outcome = skipped, updatedAt = nowMs) ?: RoutineLogEntity(ids.newId(), routineId, day.toString(), skipped, nowMs, null))
        }
    }

    suspend fun clearLog(routineId: String, day: LocalDate) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            routineDao.logFor(routineId, day.toString())?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
        }
    }
}
