package com.budgetgoblin.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class Category(val label: String, val monthlyLimit: Double) {
    FOOD("Food", 400.0),
    TRANSPORT("Transport", 150.0),
    BILLS("Bills", 600.0),
    FUN("Fun", 200.0),
    OTHER("Other", 250.0),
    SHOPPING("Shopping", 200.0),
    COFFEE("Coffee", 80.0),
}

/**
 * Income has no category; expenses always have one. [date] is epoch millis. Income with [toBudget]
 * is a one-time top-up: it adds to that month's spending budget.
 */
data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: Category?,
    val date: Long = System.currentTimeMillis(),
    val toBudget: Boolean = false,
    /** The wallet card it was paid with or received in ([Account.CASH_ID] if none was chosen). */
    val accountId: Long = Account.CASH_ID,
)

fun isThisMonth(millis: Long, now: Long = System.currentTimeMillis()): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = millis }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}

/** Rounds money to whole cents, so 478.60 + 3.85 equals 482.45 and a 3-way split isn't 33.333… */
fun cents(x: Double): Double = Math.round(x * 100) / 100.0

/** The total of these transactions' amounts, in whole cents. */
fun Iterable<Transaction>.total(): Double = cents(sumOf { it.amount })

/**
 * What an amount field keeps of [typed]: ASCII digits and one decimal point. The phone's own decimal
 * separator counts as the point (a comma in many languages, so "12,50" is 12.50, never 1250); the
 * other of ',' and '.' is a thousands separator and is dropped. With [allowNegative], a leading '-' stays.
 */
fun cleanAmount(
    typed: String,
    allowNegative: Boolean = false,
    decimalSeparator: Char = java.text.DecimalFormatSymbols.getInstance().decimalSeparator,
): String {
    val out = StringBuilder()
    var point = false
    for (c in typed) when {
        c in '0'..'9' -> out.append(c)
        (c == decimalSeparator || c == '.' && decimalSeparator != ',') && !point -> { point = true; out.append('.') }
        c == '-' && allowNegative && out.isEmpty() -> out.append(c)
    }
    return out.toString()
}

/** Midnight at the start of the day [daysAgo] days before [now]. */
fun startOfDay(daysAgo: Int, now: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * Saves everything to SharedPreferences. Transactions are one per line:
 * id|isIncome|amount|category|date|title (older saves have no date field). A budget top-up stores
 * [TOP_UP] in the category column; the card it was paid with rides on the date as date@cardId.
 */
class BudgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("budget", Context.MODE_PRIVATE)

    /** A fresh install starts empty. Starter samples saved by older versions (ids 1, 2, 3…) are dropped. */
    fun loadTransactions(): List<Transaction> {
        val raw = prefs.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        return raw.lines().mapNotNull(::decode).filterNot { isSample(it.id) }
    }

    fun saveTransactions(transactions: List<Transaction>) {
        prefs.edit { putString(KEY_TRANSACTIONS, transactions.joinToString("\n", transform = ::encode)) }
    }

    /** Whether the user has changed anything on this phone yet (entries, challenges, cards or budget). */
    val hasSavedData get() = listOf(KEY_TRANSACTIONS, KEY_CHALLENGES, KEY_ACCOUNTS, KEY_BUDGET_EXACT, KEY_BUDGET).any(prefs::contains)

    fun loadSyncedUid(): String? = prefs.getString(KEY_SYNCED_UID, null)

    fun saveSyncedUid(uid: String?) = prefs.edit { putString(KEY_SYNCED_UID, uid) }

    /** Saved as text so it comes back exact (older versions saved a Float, which rounds big budgets). */
    fun loadBudget(): Double =
        prefs.getString(KEY_BUDGET_EXACT, null)?.toDoubleOrNull() ?: prefs.getFloat(KEY_BUDGET, DEFAULT_BUDGET).toDouble()

    fun saveBudget(amount: Double) = prefs.edit { putString(KEY_BUDGET_EXACT, amount.toString()) }

    /** The wallet's cards; Cash is always first, even on a fresh install. */
    fun loadAccounts(): List<Account> {
        val saved = prefs.getString(KEY_ACCOUNTS, null)?.lines()?.mapNotNull(Account::decode).orEmpty()
        return if (saved.none { it.id == Account.CASH_ID }) listOf(Account.cash()) + saved else saved
    }

    fun saveAccounts(accounts: List<Account>) {
        prefs.edit { putString(KEY_ACCOUNTS, accounts.joinToString("\n") { it.encode() }) }
    }

    fun loadSchedule() = PaySchedule(
        cycle = PayCycle.entries.firstOrNull { it.name == prefs.getString(KEY_PAY_CYCLE, null) } ?: PayCycle.MONTHLY,
        weekStart = prefs.getInt(KEY_PAY_WEEK_START, java.util.Calendar.MONDAY),
        anchor = prefs.getLong(KEY_PAY_ANCHOR, 0L),
    )

    fun saveSchedule(s: PaySchedule) = prefs.edit {
        putString(KEY_PAY_CYCLE, s.cycle.name)
        putInt(KEY_PAY_WEEK_START, s.weekStart)
        putLong(KEY_PAY_ANCHOR, s.anchor)
    }

    fun loadChallenges(): List<Challenge> =
        prefs.getString(KEY_CHALLENGES, null)?.lines()?.mapNotNull(Challenge::decode) ?: emptyList()

    fun saveChallenges(challenges: List<Challenge>) {
        prefs.edit { putString(KEY_CHALLENGES, challenges.joinToString("\n") { it.encode() }) }
    }

    private fun encode(t: Transaction) =
        listOf(t.id, t.isIncome, t.amount, if (t.toBudget) TOP_UP else t.category?.name.orEmpty(), if (t.accountId == Account.CASH_ID) t.date else "${t.date}@${t.accountId}", t.title.replace('\n', ' '))
            .joinToString("|")

    private fun decode(line: String): Transaction? {
        val parts = line.split("|", limit = 6)
        if (parts.size < 5) return null
        val id = parts[0].toLongOrNull() ?: return null
        // Old 5-field lines have no date: ids from the app are timestamps, sample ids are 1, 2, 3…
        val dateField = parts.getOrNull(4)?.split("@")
        val dated = parts.size == 6 && dateField?.get(0)?.toLongOrNull() != null
        val date = if (dated) dateField!![0].toLong() else if (id > 1_000_000_000_000) id else startOfDay(id.toInt() % 7) + HOUR * 12
        return Transaction(
            id = id,
            isIncome = parts[1].toBoolean(),
            amount = parts[2].toDoubleOrNull() ?: return null,
            category = Category.entries.firstOrNull { it.name == parts[3] },
            date = date,
            title = if (dated) parts[5] else parts.drop(4).joinToString("|"),
            toBudget = parts[3] == TOP_UP,
            accountId = if (dated) dateField!!.getOrNull(1)?.toLongOrNull() ?: Account.CASH_ID else Account.CASH_ID,
        )
    }

    companion object {
        const val DEFAULT_BUDGET = 2000f

        /** The old starter samples had ids 1, 2, 3…; real entries use the time they were made. */
        fun isSample(id: Long) = id < 1_000_000_000_000
        private const val TOP_UP = "+BUDGET"
        private const val KEY_PAY_CYCLE = "pay_cycle"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_PAY_WEEK_START = "pay_week_start"
        private const val KEY_PAY_ANCHOR = "pay_anchor"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_SYNCED_UID = "synced_uid"
        private const val KEY_BUDGET = "monthly_budget" // older Float copy, read only
        private const val KEY_BUDGET_EXACT = "monthly_budget_exact"
        private const val KEY_CHALLENGES = "challenges"
        private val HOUR = TimeUnit.HOURS.toMillis(1)
    }
}
