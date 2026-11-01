package com.budgetgoblin.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/**
 * Everything the app shows, kept in Compose state and saved to [store] on every change.
 * While signed in, [cloud] also receives each change, and the apply* functions take in
 * changes made on the user's other phones.
 */
class BudgetState(private val store: BudgetStore) {
    var cloud: CloudSync? = null

    val transactions = mutableStateListOf<Transaction>().apply {
        addAll(store.loadTransactions())
        // Saving straight back pins down dates guessed for older saves, so they don't shift day to day.
        if (store.hasSavedData) store.saveTransactions(this)
    }
    val challenges = mutableStateListOf<Challenge>().apply { addAll(store.loadChallenges()) }
    var monthlyBudget by mutableDoubleStateOf(store.loadBudget())
        private set

    /** The account this phone's data last synced with, so we only offer an upload once. */
    var syncedUid: String?
        get() = store.loadSyncedUid()
        set(value) = store.saveSyncedUid(value)

    /** True once the user has made their own changes here (the starter sample doesn't count). */
    val hasOwnData get() = store.hasSavedData

    val thisMonth: List<Transaction> get() = transactions.filter { isThisMonth(it.date) }
    val activeChallenges get() = challenges.filter { it.status == ChallengeStatus.ACTIVE }

    /** Adds [t] and returns the challenges it broke. */
    fun add(t: Transaction): List<Challenge> {
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
        syncedUid = null
    }

    private fun replace(old: Challenge, new: Challenge) {
        val i = challenges.indexOf(old)
        if (i >= 0) challenges[i] = new
        store.saveChallenges(challenges)
        cloud?.put(new)
    }
}
