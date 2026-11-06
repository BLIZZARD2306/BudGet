package com.budgetgoblin.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.TimeUnit

/**
 * Everything the app shows, kept in Compose state and saved to [store] on every change.
 * While signed in, [cloud] also receives each change, and the apply* functions take in
 * changes made on the user's other phones.
 */
class BudgetState(private val store: BudgetStore) {
    var cloud: CloudSync? = null

    val transactions = mutableStateListOf<Transaction>().apply {
        addAll(store.loadTransactions())
        // Saving straight back pins down dates guessed for older saves (and drops old starter samples).
        if (store.hasSavedData) store.saveTransactions(this)
    }
    val challenges = mutableStateListOf<Challenge>().apply { addAll(store.loadChallenges()) }

    /** The wallet's cards; Cash ([Account.CASH_ID]) is always there and can't be removed. */
    val accounts = mutableStateListOf<Account>().apply { addAll(store.loadAccounts()) }

    fun account(id: Long): Account = accounts.firstOrNull { it.id == id } ?: accounts.first { it.id == Account.CASH_ID }

    /** Everything paid with or received in [account], newest first (unknown cards count as Cash). */
    fun historyOf(account: Account): List<Transaction> {
        val known = accounts.map { it.id }.toSet()
        return transactions
            .filter { t -> t.accountId == account.id || (account.id == Account.CASH_ID && t.accountId !in known) }
            .sortedByDescending { it.date }
    }

    /** Starting amount, plus money in, minus money out. */
    fun balanceOf(account: Account): Double =
        cents(account.startBalance + historyOf(account).sumOf { if (it.isIncome) it.amount else -it.amount })

    val totalBalance: Double get() = accounts.sumOf { balanceOf(it) }

    fun saveAccount(a: Account) {
        val i = accounts.indexOfFirst { it.id == a.id }
        if (i >= 0) accounts[i] = a else accounts.add(a)
        store.saveAccounts(accounts)
        cloud?.put(a)
    }

    /** Removes a card; its history moves to Cash so no money disappears from the totals. */
    fun removeAccount(a: Account) {
        if (a.id == Account.CASH_ID) return
        transactions.toList().forEachIndexed { i, t ->
            if (t.accountId == a.id) {
                val moved = t.copy(accountId = Account.CASH_ID)
                transactions[i] = moved
                cloud?.put(moved)
            }
        }
        store.saveTransactions(transactions)
        accounts.removeAll { it.id == a.id }
        store.saveAccounts(accounts)
        cloud?.remove(a)
    }

    fun applyAccounts(list: List<Account>) {
        accounts.clear()
        accounts.addAll(if (list.none { it.id == Account.CASH_ID }) listOf(Account.cash()) + list else list.sortedBy { it.id })
        store.saveAccounts(accounts)
    }
    /** The budget for one pay period (a month, a week, ... per [schedule]). Stored under its old name. */
    var monthlyBudget by mutableDoubleStateOf(store.loadBudget())
        private set

    /** When you get paid; the budget and Remaining reset at the start of each period. */
    var schedule by mutableStateOf(store.loadSchedule())
        private set

    /** The account this phone's data last synced with, so we only offer an upload once. */
    var syncedUid: String?
        get() = store.loadSyncedUid()
        set(value) = store.saveSyncedUid(value)

    /** True once the user has made their own changes here. */
    val hasOwnData get() = store.hasSavedData

    /** Whether this phone holds anything worth copying into an account: entries, challenges or cards. */
    val hasLocalEntries get() = hasOwnData && (transactions.isNotEmpty() || challenges.isNotEmpty() || accounts.any { it.id != Account.CASH_ID })

    val thisMonth: List<Transaction> get() = transactions.filter { isThisMonth(it.date) }

    /** The current pay period, and what happened in it. */
    val period: Period get() = schedule.periodAt()
    val thisPeriod: List<Transaction> get() = period.let { p -> transactions.filter { it.date in p } }

    /** One-time top-ups added to this period's budget. */
    val topUps: Double get() = thisPeriod.filter { it.isIncome && it.toBudget }.total()

    /** This period's spending budget: the per-period budget plus any top-ups. */
    val budgetThisPeriod: Double get() = monthlyBudget + topUps
    val activeChallenges get() = challenges.filter { it.status == ChallengeStatus.ACTIVE }
    val challengesWon get() = challenges.count { it.status == ChallengeStatus.COMPLETED }

    /** Days in a row (ending today, or yesterday if nothing's logged yet today) with at least one entry. */
    val loggingStreak: Int
        get() {
            val dayMillis = TimeUnit.DAYS.toMillis(1)
            val endOfToday = startOfDay(-1)
            val days = transactions.map { ((endOfToday - 1 - it.date) / dayMillis).coerceAtLeast(0).toInt() }.toSet()
            var day = if (0 in days) 0 else 1
            var streak = 0
            while (day in days) {
                streak++
                day++
            }
            return streak
        }

    /**
     * Adds [entry] and returns the challenges it broke. Challenges that already ran their course are
     * completed first, so spending after one ends never breaks it. Ids are kept unique (two entries
     * in the same millisecond would otherwise be one document in the cloud).
     */
    fun add(entry: Transaction): List<Challenge> {
        val t = if (transactions.any { it.id == entry.id }) entry.copy(id = transactions.maxOf { it.id } + 1) else entry
        completeFinished(t.date)
        transactions.add(0, t)
        store.saveTransactions(transactions)
        cloud?.put(t)
        val broken = activeChallenges.filter { it.type.isBrokenBy(t) }
        broken.forEach { replace(it, it.copy(status = ChallengeStatus.FAILED, endedAt = t.date)) }
        return broken
    }

    fun delete(t: Transaction) {
        transactions.remove(t)
        store.saveTransactions(transactions)
        cloud?.remove(t)
    }

    fun setBudget(amount: Double) {
        monthlyBudget = amount
        store.saveBudget(amount)
        cloud?.putBudget(amount)
    }

    fun changeSchedule(s: PaySchedule) {
        schedule = s
        store.saveSchedule(s)
        cloud?.putSchedule(s)
    }

    fun applySchedule(s: PaySchedule) {
        schedule = s
        store.saveSchedule(s)
    }

    fun start(type: ChallengeType, now: Long = System.currentTimeMillis()) {
        val c = Challenge(type, now)
        challenges.add(0, c)
        store.saveChallenges(challenges)
        cloud?.put(c)
    }

    fun giveUp(c: Challenge, now: Long = System.currentTimeMillis()) =
        replace(c, c.copy(status = ChallengeStatus.FAILED, endedAt = now))

    /** Marks challenges that have run their full length as completed; returns the newly completed ones. */
    fun completeFinished(now: Long = System.currentTimeMillis()): List<Challenge> {
        val done = activeChallenges.filter { now >= it.endsAt }
        done.forEach { replace(it, it.copy(status = ChallengeStatus.COMPLETED, endedAt = it.endsAt)) }
        return done
    }

    fun applyTransactions(list: List<Transaction>) {
        transactions.clear()
        transactions.addAll(list.sortedByDescending { it.date })
        store.saveTransactions(transactions)
    }

    fun applyChallenges(list: List<Challenge>) {
        challenges.clear()
        challenges.addAll(list.sortedByDescending { it.startedAt })
        store.saveChallenges(challenges)
    }

    fun applyBudget(amount: Double) {
        monthlyBudget = amount
        store.saveBudget(amount)
    }

    /** Empties this phone after signing out, so the next person doesn't see the account's data. */
    fun clearLocal() {
        applyTransactions(emptyList())
        applyChallenges(emptyList())
        applyBudget(BudgetStore.DEFAULT_BUDGET.toDouble())
        applySchedule(PaySchedule())
        applyAccounts(emptyList())
        syncedUid = null
    }

    private fun replace(old: Challenge, new: Challenge) {
        val i = challenges.indexOf(old)
        if (i >= 0) challenges[i] = new
        store.saveChallenges(challenges)
        cloud?.put(new)
    }
}
