package com.mochi.ascend.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * A goal is "checked in" against the period key of its horizon:
 *   DAY   -> 2026-10-17
 *   WEEK  -> 2026-W42   (ISO week)
 *   MONTH -> 2026-10
 *   YEAR  -> 2026
 */
object Dates {
    private val iso: WeekFields = WeekFields.ISO

    fun today(): LocalDate = LocalDate.now()

    fun dayKey(d: LocalDate): String = d.toString()

    fun weekKey(d: LocalDate): String =
        "%d-W%02d".format(d.get(iso.weekBasedYear()), d.get(iso.weekOfWeekBasedYear()))

    fun monthKey(d: LocalDate): String = "%d-%02d".format(d.year, d.monthValue)

    fun yearKey(d: LocalDate): String = d.year.toString()

    fun key(h: Horizon, d: LocalDate): String = when (h) {
        Horizon.DAY -> dayKey(d)
        Horizon.WEEK -> weekKey(d)
        Horizon.MONTH -> monthKey(d)
        Horizon.YEAR -> yearKey(d)
    }

    fun startOf(h: Horizon, d: LocalDate): LocalDate = when (h) {
        Horizon.DAY -> d
        Horizon.WEEK -> d.with(DayOfWeek.MONDAY)
        Horizon.MONTH -> d.withDayOfMonth(1)
        Horizon.YEAR -> d.withDayOfYear(1)
    }

    fun endOf(h: Horizon, d: LocalDate): LocalDate = when (h) {
        Horizon.DAY -> d
        Horizon.WEEK -> startOf(Horizon.WEEK, d).plusDays(6)
        Horizon.MONTH -> startOf(Horizon.MONTH, d).plusMonths(1).minusDays(1)
        Horizon.YEAR -> startOf(Horizon.YEAR, d).plusYears(1).minusDays(1)
    }

    fun plus(h: Horizon, d: LocalDate, n: Long): LocalDate = when (h) {
        Horizon.DAY -> d.plusDays(n)
        Horizon.WEEK -> d.plusWeeks(n)
        Horizon.MONTH -> d.plusMonths(n)
        Horizon.YEAR -> d.plusYears(n)
    }

    fun minus(h: Horizon, d: LocalDate, n: Long): LocalDate = plus(h, d, -n)

    fun parse(s: String?): LocalDate? =
        try {
            if (s.isNullOrBlank()) null else LocalDate.parse(s)
        } catch (_: Throwable) {
            null
        }

    /** Short human label, e.g. "17 Oct", "W42", "Oct 2026", "2026". */
    fun label(h: Horizon, d: LocalDate): String = when (h) {
        Horizon.DAY -> "${d.dayOfMonth} ${monthShort(d)}"
        Horizon.WEEK -> "W${d.get(iso.weekOfWeekBasedYear())}"
        Horizon.MONTH -> "${monthShort(d)} ${d.year}"
        Horizon.YEAR -> d.year.toString()
    }

    fun monthShort(d: LocalDate): String =
        d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    fun weekdayShort(d: LocalDate): String =
        d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    /** Minutes-to-hours helper for display. */
    fun prettyMinutes(m: Int): String = when {
        m <= 0 -> ""
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }
}
