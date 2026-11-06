package com.budgetgoblin.app

import com.budgetgoblin.app.data.Transaction
import com.budgetgoblin.app.data.cents
import com.budgetgoblin.app.data.cleanAmount
import com.budgetgoblin.app.data.total
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun commaIsTheDecimalPointWhereThePhoneUsesOne() {
        assertEquals("12.50", cleanAmount("12,50", decimalSeparator = ','))
        assertEquals("1250.5", cleanAmount("1.250,5", decimalSeparator = ','))
    }

    @Test fun commaIsAThousandsSeparatorElsewhere() {
        assertEquals("1250.50", cleanAmount("1,250.50", decimalSeparator = '.'))
        assertEquals("12.50", cleanAmount("12.50", decimalSeparator = '.'))
    }

    @Test fun keepsOnePointAndPlainDigitsOnly() {
        assertEquals("1.23", cleanAmount("1.2.3", decimalSeparator = '.'))
        assertEquals("12", cleanAmount("1a2 ₱", decimalSeparator = '.'))
        assertEquals("5", cleanAmount("-5", decimalSeparator = '.'))
        assertEquals("-5", cleanAmount("-5", allowNegative = true, decimalSeparator = '.'))
        assertEquals("5", cleanAmount("5-", allowNegative = true, decimalSeparator = '.'))
    }

    @Test fun totalsAreWholeCents() {
        fun spend(amount: Double) = Transaction(id = 0, title = "", amount = amount, isIncome = false, category = null)
        val spent = listOf(spend(478.60), spend(3.85)).total()
        assertEquals(0.0, cents(482.45 - spent), 0.0) // no "-0.00" left over
        assertEquals(33.33, cents(100.0 / 3), 0.0)
    }
}
