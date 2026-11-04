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

    fun loadTransactions(): List<Transaction> {
        val raw = prefs.getString(KEY_TRANSACTIONS, null) ?: return sampleData()
        return raw.lines().mapNotNull(::decode)
    }

    fun saveTransactions(transactions: List<Transaction>) {
        prefs.edit { putString(KEY_TRANSACTIONS, transactions.joinToString("\n", transform = ::encode)) }
    }

    /** Whether anything has been saved yet; a fresh install only has the starter sample. */
    val hasSavedData get() = prefs.contains(KEY_TRANSACTIONS) || prefs.contains(KEY_CHALLENGES)

    fun loadSyncedUid(): String? = prefs.getString(KEY_SYNCED_UID, null)

    fun saveSyncedUid(uid: String?) = prefs.edit { putString(KEY_SYNCED_UID, uid) }

    fun loadBudget(): Double = prefs.getFloat(KEY_BUDGET, DEFAULT_BUDGET).toDouble()

    fun saveBudget(amount: Double) = prefs.edit { putFloat(KEY_BUDGET, amount.toFloat()) }

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

    private fun sampleData(): List<Transaction> {
        var id = 0L
        fun t(title: String, amount: Double, category: Category?, daysAgo: Int) =
            Transaction(++id, title, amount, isIncome = category == null, category = category,
                date = startOfDay(daysAgo) + HOUR * 13)
        return listOf(
            t("Coffee with Sam", 6.50, Category.COFFEE, 0),
            t("Groceries", 86.40, Category.FOOD, 0),
            t("Sneakers", 120.0, Category.SHOPPING, 1),
            t("Ramen night", 24.0, Category.FOOD, 2),
            t("Bus pass", 55.0, Category.TRANSPORT, 3),
            t("Cinema", 18.0, Category.FUN, 4),
            t("Electricity", 62.15, Category.BILLS, 5),
            t("Latte", 4.80, Category.COFFEE, 5),
            t("Gift", 35.0, Category.OTHER, 6),
            t("Rent", 450.0, Category.BILLS, 6),
            t("Salary", 2800.0, null, 6),
        ).filter { isThisMonth(it.date) }
    }

    companion object {
        const val DEFAULT_BUDGET = 2000f
        private const val TOP_UP = "+BUDGET"
        private const val KEY_PAY_CYCLE = "pay_cycle"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_PAY_WEEK_START = "pay_week_start"
        private const val KEY_PAY_ANCHOR = "pay_anchor"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_SYNCED_UID = "synced_uid"
        private const val KEY_BUDGET = "monthly_budget"
        private const val KEY_CHALLENGES = "challenges"
        private val HOUR = TimeUnit.HOURS.toMillis(1)
    }
}
