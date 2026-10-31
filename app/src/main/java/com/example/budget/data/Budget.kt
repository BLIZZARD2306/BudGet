package com.example.budget.data

import android.content.Context
import androidx.core.content.edit

enum class Category(val label: String, val emoji: String, val monthlyLimit: Double) {
    FOOD("Food", "🍜", 400.0),
    TRANSPORT("Transport", "🚌", 150.0),
    BILLS("Bills", "💡", 600.0),
    FUN("Fun", "🎮", 200.0),
    OTHER("Other", "📦", 250.0),
}

/** Income has no category; expenses always have one. */
data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: Category?,
)

/** Saves transactions to SharedPreferences, one per line: id|isIncome|amount|category|title. */
class BudgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("budget", Context.MODE_PRIVATE)

    fun load(): List<Transaction> {
        val raw = prefs.getString(KEY, null) ?: return sampleData()
        return raw.lines().mapNotNull(::decode)
    }

    fun save(transactions: List<Transaction>) {
        prefs.edit { putString(KEY, transactions.joinToString("\n", transform = ::encode)) }
    }

    private fun encode(t: Transaction) =
        listOf(t.id, t.isIncome, t.amount, t.category?.name.orEmpty(), t.title.replace('\n', ' '))
            .joinToString("|")

    private fun decode(line: String): Transaction? {
        val parts = line.split("|", limit = 5)
        if (parts.size != 5) return null
        return Transaction(
            id = parts[0].toLongOrNull() ?: return null,
            isIncome = parts[1].toBoolean(),
            amount = parts[2].toDoubleOrNull() ?: return null,
            category = Category.entries.firstOrNull { it.name == parts[3] },
            title = parts[4],
        )
    }

    private fun sampleData(): List<Transaction> {
        var id = 0L
        fun t(title: String, amount: Double, category: Category?) =
            Transaction(++id, title, amount, isIncome = category == null, category = category)
        return listOf(
            t("Salary", 2800.0, null),
            t("Groceries", 86.40, Category.FOOD),
            t("Ramen night", 24.0, Category.FOOD),
            t("Bus pass", 55.0, Category.TRANSPORT),
            t("Rent", 450.0, Category.BILLS),
            t("Electricity", 62.15, Category.BILLS),
            t("Cinema", 18.0, Category.FUN),
            t("Gift", 35.0, Category.OTHER),
        )
    }

    private companion object {
        const val KEY = "transactions"
    }
}
