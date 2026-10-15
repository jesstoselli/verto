package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.StepEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.wire
import com.verto.domain.model.StepEnergy
import kotlin.time.Clock

class StepRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val stepDao = store.db.stepDao()

    /**
     * "Tá grande demais": the step is replaced, in place, by smaller ones. They start light,
     * since the point is a step small enough to begin. Returns the new ids in order.
     */
    suspend fun split(stepId: String, titles: List<String>): List<String> {
        val pieces = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return emptyList()
        val nowMs = clock.now().toEpochMilliseconds()
        return store.write {
            val original = stepDao.get(stepId)?.takeIf { it.deletedAt == null } ?: return@write emptyList()
            val siblings = when {
                original.taskId != null -> stepDao.forTask(original.taskId)
                else -> stepDao.forRoutine(checkNotNull(original.routineId)).filter { it.plan == original.plan }
            }
            val created = pieces.map { title ->
                StepEntity(
                    id = ids.newId(),
                    taskId = original.taskId,
                    routineId = original.routineId,
                    plan = original.plan,
                    order = 0,
                    title = title,
                    energy = StepEnergy.Low.wire(),
                    doneAt = null,
                    updatedAt = nowMs,
                    deletedAt = null,
                )
            }
            val newIds = created.map { it.id }.toSet()
            siblings.flatMap { if (it.id == stepId) created else listOf(it) }.forEachIndexed { index, step ->
                if (step.id in newIds || step.order != index) put(step.copy(order = index, updatedAt = nowMs))
            }
            put(original.copy(deletedAt = nowMs, updatedAt = nowMs))
            created.map { it.id }
        }
    }
}
