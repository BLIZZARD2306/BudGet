package com.budgetgoblin.app

import com.budgetgoblin.app.data.PayCycle
import com.budgetgoblin.app.data.PaySchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PayScheduleTest {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun day(s: String) = fmt.parse(s)!!.time + 13 * 60 * 60 * 1000L // 1pm that day
    private fun range(s: PaySchedule, now: String): String {
        val p = s.periodAt(day(now))
        return fmt.format(p.start) + ".." + fmt.format(p.end)
    }

    @Test fun monthly() {
        assertEquals("2026-11-01..2026-12-01", range(PaySchedule(PayCycle.MONTHLY), "2026-11-04"))
        assertEquals("2026-12-01..2027-01-01", range(PaySchedule(PayCycle.MONTHLY), "2026-12-31"))
    }

    @Test fun twiceAMonth() {
        val s = PaySchedule(PayCycle.TWICE_MONTHLY)
        assertEquals("2026-11-01..2026-11-16", range(s, "2026-11-15"))
        assertEquals("2026-11-16..2026-12-01", range(s, "2026-11-16"))
        assertEquals("2026-12-16..2027-01-01", range(s, "2026-12-31"))
    }

    @Test fun weekly() {
        // 2026-11-04 is a Wednesday.
        assertEquals("2026-11-02..2026-11-09", range(PaySchedule(PayCycle.WEEKLY, Calendar.MONDAY), "2026-11-04"))
        assertEquals("2026-11-04..2026-11-11", range(PaySchedule(PayCycle.WEEKLY, Calendar.WEDNESDAY), "2026-11-04"))
        assertEquals("2026-10-29..2026-11-05", range(PaySchedule(PayCycle.WEEKLY, Calendar.THURSDAY), "2026-11-04"))
    }

    @Test fun everyTwoWeeksWithoutAPaydayKeepsItsRhythm() {
        // No anchor picked: the 2 weeks must not restart each week (they used to count from today).
        val s = PaySchedule(PayCycle.BIWEEKLY, Calendar.MONDAY)
        assertEquals("2026-11-02..2026-11-16", range(s, "2026-11-04"))
        assertEquals("2026-11-02..2026-11-16", range(s, "2026-11-11"))
        assertEquals("2026-11-16..2026-11-30", range(s, "2026-11-18"))
    }

    @Test fun everyTwoWeeks() {
        val s = PaySchedule(PayCycle.BIWEEKLY, anchor = day("2026-10-23"))
        assertEquals("2026-10-23..2026-11-06", range(s, "2026-11-04"))
        assertEquals("2026-11-06..2026-11-20", range(s, "2026-11-06"))
        // An anchor in the future still lines up the rhythm backwards.
        val ahead = PaySchedule(PayCycle.BIWEEKLY, anchor = day("2026-11-20"))
        assertEquals("2026-10-23..2026-11-06", range(ahead, "2026-11-04"))
    }
}
