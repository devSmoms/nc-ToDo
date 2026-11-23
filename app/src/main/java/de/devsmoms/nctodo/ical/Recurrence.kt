package de.devsmoms.nctodo.ical

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Subset of RFC 5545 RRULE used for repeating tasks: DAILY/WEEKLY/MONTHLY/YEARLY with
 * INTERVAL, BYDAY (weekly), COUNT and UNTIL. Unknown parts are kept in [extra] so a rule
 * written by another app survives when only COUNT is updated.
 */
data class Repeat(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<DayOfWeek> = emptyList(),
    val count: Int? = null,
    val until: LocalDate? = null,
    val extra: List<String> = emptyList(),
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }
}

object Recurrence {
    private val DAY_CODES = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )
    private val WEEKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val UNTIL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    // ---------------------------------------------------------------- presets for the picker

    fun daily() = Repeat(Repeat.Freq.DAILY)
    fun weekdays() = Repeat(Repeat.Freq.WEEKLY, byDay = WEEKDAYS)
    fun weekly(on: DayOfWeek) = Repeat(Repeat.Freq.WEEKLY, byDay = listOf(on))
    fun monthly() = Repeat(Repeat.Freq.MONTHLY)
    fun yearly() = Repeat(Repeat.Freq.YEARLY)

    // ---------------------------------------------------------------- parse / format

    fun parse(rrule: String?): Repeat? {
        if (rrule.isNullOrBlank()) return null
        var freq: Repeat.Freq? = null
        var interval = 1
        var byDay = emptyList<DayOfWeek>()
        var count: Int? = null
        var until: LocalDate? = null
        val extra = mutableListOf<String>()
        for (part in rrule.trim().split(';').filter { it.isNotBlank() }) {
            val key = part.substringBefore('=').uppercase()
            val value = part.substringAfter('=', "")
            when (key) {
                "FREQ" -> freq = runCatching { Repeat.Freq.valueOf(value.uppercase()) }.getOrNull()
                "INTERVAL" -> interval = value.toIntOrNull()?.coerceIn(1, 999) ?: 1
                "BYDAY" -> {
                    val days = value.split(',').mapNotNull { DAY_CODES[it.trim().takeLast(2).uppercase()] }
                    // "1MO"-style positions only make sense for monthly rules; keep them untouched there.
                    if (value.any { it.isDigit() }) extra += part else byDay = days
                }
                "COUNT" -> count = value.toIntOrNull()?.takeIf { it > 0 }
                "UNTIL" -> until = runCatching { LocalDate.parse(value.take(8), UNTIL_DATE) }.getOrNull()
                else -> extra += part
            }
        }
        return freq?.let { Repeat(it, interval, byDay, count, until, extra) }
    }

    fun format(r: Repeat): String = buildList {
        add("FREQ=${r.freq.name}")
        if (r.interval > 1) add("INTERVAL=${r.interval}")
        if (r.byDay.isNotEmpty()) add("BYDAY=" + r.byDay.sortedBy { it.value }.joinToString(",") { d -> DAY_CODES.entries.first { it.value == d }.key })
        if (r.count != null) add("COUNT=${r.count}")
        if (r.until != null) add("UNTIL=" + UNTIL_DATE.format(r.until))
        addAll(r.extra)
    }.joinToString(";")

    // ---------------------------------------------------------------- next occurrence

    /** First occurrence strictly after [from], or null if the series has ended (UNTIL / COUNT). */
    fun next(r: Repeat, from: LocalDate): LocalDate? {
        if (r.count != null && r.count <= 1) return null
        val next = when (r.freq) {
            Repeat.Freq.DAILY -> from.plusDays(r.interval.toLong())
            Repeat.Freq.WEEKLY -> nextWeekly(r, from)
            Repeat.Freq.MONTHLY -> from.plusMonths(r.interval.toLong())
            Repeat.Freq.YEARLY -> from.plusYears(r.interval.toLong())
        }
        return next.takeIf { r.until == null || !it.isAfter(r.until) }
    }

    private fun nextWeekly(r: Repeat, from: LocalDate): LocalDate {
        if (r.byDay.isEmpty()) return from.plusWeeks(r.interval.toLong())
        val weekStart = from.with(DayOfWeek.MONDAY)
        var d = from.plusDays(1)
        while (true) {
            val weeks = ChronoUnit.WEEKS.between(weekStart, d.with(DayOfWeek.MONDAY))
            if (weeks % r.interval == 0L && d.dayOfWeek in r.byDay) return d
            d = d.plusDays(1)
        }
    }

    /** Rule for the next occurrence (COUNT reduced by one). */
    fun afterOneOccurrence(r: Repeat): Repeat = if (r.count != null) r.copy(count = r.count - 1) else r

    // ---------------------------------------------------------------- display

    fun describe(r: Repeat): String {
        val n = r.interval
        val base = when (r.freq) {
            Repeat.Freq.DAILY -> if (n == 1) "Täglich" else "Alle $n Tage"
            Repeat.Freq.WEEKLY -> when {
                r.byDay == WEEKDAYS && n == 1 -> "Werktags"
                r.byDay.isEmpty() -> if (n == 1) "Wöchentlich" else "Alle $n Wochen"
                else -> (if (n == 1) "Wöchentlich" else "Alle $n Wochen") + " (" +
                    r.byDay.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.GERMAN).trimEnd('.') } + ")"
            }
            Repeat.Freq.MONTHLY -> if (n == 1) "Monatlich" else "Alle $n Monate"
            Repeat.Freq.YEARLY -> if (n == 1) "Jährlich" else "Alle $n Jahre"
        }
        val end = when {
            r.until != null -> ", bis " + DateTimeFormatter.ofPattern("d.M.yyyy").format(r.until)
            r.count != null -> ", noch ${r.count}×"
            else -> ""
        }
        return base + end
    }
}
