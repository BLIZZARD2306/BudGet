package com.budgetgoblin.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How often you get paid; your budget and Remaining reset on each payday. */
enum class PayCycle(val label: String, val detail: String) {
    MONTHLY("Monthly", "Resets on the 1st"),
    TWICE_MONTHLY("Twice a month", "Resets on the 1st and 16th"),
    BIWEEKLY("Every 2 weeks", "Resets every other week"),
    WEEKLY("Weekly", "Resets every week"),
}

/** One budget period: [start] inclusive, [end] exclusive, both epoch millis at local midnight. */
data class Period(val start: Long, val end: Long) {
    operator fun contains(millis: Long) = millis in start until end
}

/**
 * When budget periods start. [weekStart] is a Calendar.DAY_OF_WEEK (weekly and every-2-weeks);
 * [anchor] is any past payday for every-2-weeks, so the 14-day rhythm lines up with real paydays.
 */
data class PaySchedule(
    val cycle: PayCycle = PayCycle.MONTHLY,
    val weekStart: Int = Calendar.MONDAY,
    val anchor: Long = 0L,
) {
    fun periodAt(now: Long = System.currentTimeMillis()): Period {
        val c = midnight(now)
        return when (cycle) {
            PayCycle.MONTHLY -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                val start = c.timeInMillis
                c.add(Calendar.MONTH, 1)
                Period(start, c.timeInMillis)
            }
            PayCycle.TWICE_MONTHLY -> {
                val second = c.get(Calendar.DAY_OF_MONTH) >= 16
                c.set(Calendar.DAY_OF_MONTH, if (second) 16 else 1)
                val start = c.timeInMillis
                if (second) {
                    c.set(Calendar.DAY_OF_MONTH, 1)
                    c.add(Calendar.MONTH, 1)
                } else {
                    c.set(Calendar.DAY_OF_MONTH, 16)
                }
                Period(start, c.timeInMillis)
            }
            PayCycle.WEEKLY -> {
                while (c.get(Calendar.DAY_OF_WEEK) != weekStart) c.add(Calendar.DAY_OF_YEAR, -1)
                val start = c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 7)
                Period(start, c.timeInMillis)
            }
            PayCycle.BIWEEKLY -> {
                // Step from the anchor payday in 14-day jumps to the one at or before today. Without an
                // anchor, a fixed reference week keeps the rhythm steady (counting from today would
                // restart the 2 weeks every week).
                val a = midnight(if (anchor > 0) anchor else lastWeekday(REFERENCE_WEEK, weekStart))
                if (a.timeInMillis <= c.timeInMillis) {
                    while (true) {
                        val next = (a.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 14) }
                        if (next.timeInMillis > c.timeInMillis) break
                        a.add(Calendar.DAY_OF_YEAR, 14)
                    }
                } else {
                    while (a.timeInMillis > c.timeInMillis) a.add(Calendar.DAY_OF_YEAR, -14)
                }
                val start = a.timeInMillis
                a.add(Calendar.DAY_OF_YEAR, 14)
                Period(start, a.timeInMillis)
            }
        }
    }

    /** "This week's budget", "November budget", or "Budget until Nov 15". */
    fun bannerLabel(now: Long = System.currentTimeMillis()): String = when (cycle) {
        PayCycle.MONTHLY -> SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(now)) + " budget"
        PayCycle.WEEKLY -> "This week's budget"
        else -> "Budget until " + lastDay(now)
    }

    /** "Resets Monday" / "Resets Nov 16": when Remaining starts over. */
    fun resetLabel(now: Long = System.currentTimeMillis()): String {
        val end = periodAt(now).end
        val days = ((end - midnight(now).timeInMillis) / DAY).toInt()
        return when {
            days <= 1 -> "Resets tomorrow"
            days < 7 -> "Resets " + SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(end))
            else -> "Resets " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(end))
        }
    }

    /** "week", "month" or "pay period", for sentences. */
    val periodName: String
        get() = when (cycle) {
            PayCycle.MONTHLY -> "month"
            PayCycle.WEEKLY -> "week"
            else -> "pay period"
        }

    private fun lastDay(now: Long) =
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(periodAt(now).end - DAY))

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        /** Sunday 7 January 2024: the week every-2-weeks counts from when no payday was picked. */
        private val REFERENCE_WEEK = Calendar.getInstance().apply { clear(); set(2024, Calendar.JANUARY, 7, 12, 0) }.timeInMillis

        fun midnight(millis: Long): Calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        /** The most recent [dayOfWeek] at or before [now], at midnight. */
        fun lastWeekday(now: Long, dayOfWeek: Int): Long {
            val c = midnight(now)
            while (c.get(Calendar.DAY_OF_WEEK) != dayOfWeek) c.add(Calendar.DAY_OF_YEAR, -1)
            return c.timeInMillis
        }
    }
}
