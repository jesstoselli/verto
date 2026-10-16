package com.verto.ui.plan

/** The lenses inside Trajeto. Presentation only: nothing about the choice is stored. */
enum class PlanTab(val label: String) {
    Tasks("Tarefas"),
    Routines("Rotinas"),
    Anchors("Âncoras"),
}

/** What is on the path right now, in words: totals, not a list of what is still owed. */
fun trajectorySummary(tasks: Int, routines: Int, anchors: Int): String {
    fun count(n: Int, one: String, many: String) = when (n) {
        0 -> "nenhuma $one"
        1 -> "1 $one"
        else -> "$n $many"
    }
    return "No caminho: ${count(tasks, "tarefa", "tarefas")}, ${count(routines, "rotina", "rotinas")} e ${count(anchors, "âncora", "âncoras")}"
}
