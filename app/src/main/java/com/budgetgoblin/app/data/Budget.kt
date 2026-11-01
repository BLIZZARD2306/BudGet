package com.budgetgoblin.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class Category(val label: String, val emoji: String, val monthlyLimit: Double) {
    FOOD("Food", "🍜", 400.0),
    TRANSPORT("Transport", "🚌", 150.0),
    BILLS("Bills", "💡", 600.0),
    FUN("Fun", "🎮", 200.0),
    OTHER("Other", "📦", 250.0),
    SHOPPING("Shopping", "🛍️", 200.0),
    COFFEE("Coffee", "☕", 80.0),
}

/** Income has no category; expenses always have one. [date] is epoch millis. */
data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: Category?,
    val date: Long = System.currentTimeMillis(),
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
 * id|isIncome|amount|category|date|title (older saves have no date field).
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

    fun loadChallenges(): List<Challenge> =
        prefs.getString(KEY_CHALLENGES, null)?.lines()?.mapNotNull(Challenge::decode) ?: emptyList()

    fun saveChallenges(challenges: List<Challenge>) {
        prefs.edit { putString(KEY_CHALLENGES, challenges.joinToString("\n") { it.encode() }) }
    }

    private fun encode(t: Transaction) =
        listOf(t.id, t.isIncome, t.amount, t.category?.name.orEmpty(), t.date, t.title.replace('\n', ' '))
            .joinToString("|")

    private fun decode(line: String): Transaction? {
        val parts = line.split("|", limit = 6)
        if (parts.size < 5) return null
        val id = parts[0].toLongOrNull() ?: return null
        // Old 5-field lines have no date: ids from the app are timestamps, sample ids are 1, 2, 3…
        val dated = parts.size == 6 && parts[4].toLongOrNull() != null
        val date = if (dated) parts[4].toLong() else if (id > 1_000_000_000_000) id else startOfDay(id.toInt() % 7) + HOUR * 12
        return Transaction(
            id = id,
            isIncome = parts[1].toBoolean(),
            amount = parts[2].toDoubleOrNull() ?: return null,
            category = Category.entries.firstOrNull { it.name == parts[3] },
            date = date,
            title = if (dated) parts[5] else parts.drop(4).joinToString("|"),
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
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_SYNCED_UID = "synced_uid"
        private const val KEY_BUDGET = "monthly_budget"
        private const val KEY_CHALLENGES = "challenges"
        private val HOUR = TimeUnit.HOURS.toMillis(1)
    }
}
