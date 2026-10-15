package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.data.toEntity
import com.verto.domain.model.DueKind
import com.verto.domain.model.Step
import com.verto.domain.model.Task
import com.verto.domain.rules.TaskRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

class TaskRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val taskDao = store.db.taskDao()
    private val stepDao = store.db.stepDao()

    fun observeTasks(): Flow<List<Task>> = taskDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    /** Task and routine steps alike; callers filter by owner. */
    fun observeAllSteps(): Flow<List<Step>> = stepDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun draftFor(id: String): TaskDraft? {
        val row = taskDao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        return TaskDraft(
            id = row.id,
            title = row.title,
            notes = row.notes,
            dueDate = row.dueDate?.let { LocalDate.parse(it) },
            dueKind = row.dueKind?.toEnum<DueKind>(),
            pinnedToToday = row.pinnedToToday,
            steps = stepDao.forTask(id).map { StepDraft(it.id, it.title, it.energy.toEnum()) },
            done = row.doneAt != null,
        )
    }

    suspend fun save(draft: TaskDraft): String {
        require(draft.title.isNotBlank()) { "A task needs a title" }
        val id = draft.id ?: ids.newId()
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        store.write {
            val existing = taskDao.get(id)
            val current = stepDao.forTask(id)
            val kept = draft.steps.mapNotNull { it.id }.toSet()
            current.filter { it.id !in kept }.forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }

            val steps = draft.steps.mapIndexed { index, step ->
                StepEntity(
                    id = step.id ?: ids.newId(),
                    taskId = id,
                    routineId = null,
                    plan = null,
                    order = index,
                    title = step.title.trim(),
                    energy = step.energy.wire(),
                    doneAt = current.firstOrNull { it.id == step.id }?.doneAt,
                    updatedAt = nowMs,
                    deletedAt = null,
                )
            }
            steps.forEach { put(it) }

            val task = Task(
                id = id,
                title = draft.title.trim(),
                notes = draft.notes?.trim()?.ifBlank { null },
                dueDate = draft.dueDate,
                dueKind = draft.dueKind?.takeIf { draft.dueDate != null },
                pinnedToToday = draft.pinnedToToday,
                doneAt = existing?.doneAt?.let { Instant.fromEpochMilliseconds(it) },
            )
            // Only a change to the step list re-derives doneAt; otherwise a "Concluir" or "Reabrir" tapped
            // in the editor would be undone by the "Salvar" that follows it.
            val stepsChanged = draft.steps.any { it.id == null } || current.any { it.id !in kept }
            val doneAt = if (stepsChanged) TaskRules.doneAtAfterStepChange(task, steps.map { it.toDomain() }, now) else task.doneAt
            put(task.copy(doneAt = doneAt).toEntity(nowMs))
        }
        return id
    }

    suspend fun setPinned(id: String, pinned: Boolean) = update(id) { it.copy(pinnedToToday = pinned) }

    suspend fun setDone(id: String, done: Boolean) {
        val nowMs = clock.now().toEpochMilliseconds()
        update(id) { it.copy(doneAt = if (done) it.doneAt ?: nowMs else null) }
    }

    suspend fun toggleStep(stepId: String) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        store.write {
            val step = stepDao.get(stepId) ?: return@write
            val taskId = step.taskId ?: return@write
            put(step.copy(doneAt = if (step.doneAt == null) nowMs else null, updatedAt = nowMs))
            val task = taskDao.get(taskId) ?: return@write
            val doneAt = TaskRules.doneAtAfterStepChange(task.toDomain(), stepDao.forTask(taskId).map { it.toDomain() }, now)
            if (doneAt?.toEpochMilliseconds() != task.doneAt) {
                put(task.copy(doneAt = doneAt?.toEpochMilliseconds(), updatedAt = nowMs))
            }
        }
    }

    suspend fun delete(id: String) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            taskDao.get(id)?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            stepDao.forTask(id).forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
        }
    }

    private suspend fun update(id: String, change: (TaskEntity) -> TaskEntity) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val row = taskDao.get(id) ?: return@write
            put(change(row).copy(updatedAt = nowMs))
        }
    }
}
