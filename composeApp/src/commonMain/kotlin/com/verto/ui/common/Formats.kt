package com.verto.ui.common

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

private val weekdayNames = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

private val monthNames = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

val weekdayShortNames: List<Pair<DayOfWeek, String>> = listOf(
    DayOfWeek.MONDAY to "seg",
    DayOfWeek.TUESDAY to "ter",
    DayOfWeek.WEDNESDAY to "qua",
    DayOfWeek.THURSDAY to "qui",
    DayOfWeek.FRIDAY to "sex",
    DayOfWeek.SATURDAY to "sáb",
    DayOfWeek.SUNDAY to "dom",
)

private fun Int.twoDigits(): String = toString().padStart(2, '0')

fun LocalDate.longPt(): String = "${weekdayNames[dayOfWeek.isoDayNumber - 1]}, $day de ${monthNames[month.number - 1]}"

fun LocalDate.shortPt(): String = "${day.twoDigits()}/${month.number.twoDigits()}"

/** Neutral on purpose: a missed deadline is information, never an alarm. */
fun deadlineLabel(due: LocalDate, today: LocalDate): String = when {
    due < today -> "prazo era dia ${due.shortPt()}"
    due == today -> "prazo é hoje"
    else -> "prazo dia ${due.shortPt()}"
}

fun softDateLabel(due: LocalDate): String = "seria bom até ${due.shortPt()}"

fun LocalTime.hhmm(): String = "${hour.twoDigits()}:${minute.twoDigits()}"

/** Accepts "7", "07:30" or "12:5" so typing a time never needs exact formatting. */
fun parseClock(text: String): LocalTime? {
    val parts = text.trim().split(":")
    if (parts.size > 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

fun Set<DayOfWeek>.label(): String =
    if (size == 7) "todo dia" else weekdayShortNames.filter { it.first in this }.joinToString(" · ") { it.second }
