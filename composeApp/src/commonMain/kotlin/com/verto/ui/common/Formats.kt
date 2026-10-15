package com.verto.ui.common

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
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

/** What a masked time field keeps: the digits only, so the colon never has to be typed. */
fun clockDigits(input: String): String = input.filter { it.isDigit() }.take(4)

/**
 * Accepts the masked digits ("1430", or "7"/"12" for a whole hour) as well as "07:30" or "12:5".
 * Three digits are refused: the mask shows "93:0", which is not what "9:30" would mean.
 */
fun parseClock(text: String): LocalTime? {
    val trimmed = text.trim()
    if (':' !in trimmed && trimmed.all { it.isDigit() }) {
        return when (trimmed.length) {
            1, 2 -> parseClock("$trimmed:0")
            4 -> parseClock("${trimmed.take(2)}:${trimmed.drop(2)}")
            else -> null
        }
    }
    val parts = trimmed.split(":")
    if (parts.size > 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

fun Set<DayOfWeek>.label(): String =
    if (size == 7) "todo dia" else weekdayShortNames.filter { it.first in this }.joinToString(" · ") { it.second }

/**
 * How far away a date is, in the words a time-blind brain uses: "amanhã", "sexta", "em 9 dias".
 * Within the coming week the weekday is unambiguous; a passed date is just "passou", never "atrasado".
 */
fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = today.daysUntil(date)
    return when {
        days < 0 -> "passou"
        days == 0 -> "hoje"
        days == 1 -> "amanhã"
        days < 7 -> weekdayNames[date.dayOfWeek.isoDayNumber - 1]
        else -> "em $days dias"
    }
}
