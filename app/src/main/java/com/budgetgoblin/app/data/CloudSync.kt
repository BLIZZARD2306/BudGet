package com.budgetgoblin.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

/**
 * Mirrors [BudgetState] to Firestore under users/{uid}: one document per transaction and per
 * challenge (so two phones never overwrite each other's whole list) plus the budget on the user
 * document. Firestore keeps an offline copy, so writes made without internet upload later.
 */
class CloudSync(private val state: BudgetState) {
    private val db get() = FirebaseFirestore.getInstance()
    private var listeners = emptyList<ListenerRegistration>()
    private var uid: String? = null

    private fun user(uid: String) = db.collection("users").document(uid)
    private fun transactions(uid: String) = user(uid).collection("transactions")
    private fun challenges(uid: String) = user(uid).collection("challenges")

    /**
     * Starts live updates from the account; local changes go up from here on. Until the server
     * has answered once (metadata changes are included so that moment always arrives), snapshots
     * that come only from the offline cache are ignored: an empty
     * cache must never overwrite what's on the phone.
     */
    fun start(uid: String) {
        stop()
        this.uid = uid
        state.cloud = this
        var transactionsLive = false
        var challengesLive = false
        listeners = listOf(
            transactions(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                transactionsLive = transactionsLive || !snap.metadata.isFromCache
                if (transactionsLive) state.applyTransactions(snap.documents.mapNotNull(::toTransaction))
            },
            challenges(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                challengesLive = challengesLive || !snap.metadata.isFromCache
                if (challengesLive) state.applyChallenges(snap.documents.mapNotNull(::toChallenge))
            },
            user(uid).addSnapshotListener { snap, _ ->
                snap?.getDouble(FIELD_BUDGET)?.let(state::applyBudget)
            },
        )
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners = emptyList()
        uid = null
        state.cloud = null
    }

    /** Asks the server (never the offline cache, which may just be empty); throws if it can't be reached. */
    suspend fun hasData(uid: String) = !transactions(uid).limit(1).get(Source.SERVER).await().isEmpty

    /** Copies everything on this phone into the account, in one go. */
    suspend fun upload(uid: String) {
        val batch = db.batch()
        state.transactions.forEach { batch.set(transactions(uid).document(it.id.toString()), fromTransaction(it)) }
        state.challenges.forEach { batch.set(challenges(uid).document(it.startedAt.toString()), fromChallenge(it)) }
        batch.set(user(uid), mapOf(FIELD_BUDGET to state.monthlyBudget), SetOptions.merge())
        batch.commit().await()
    }

    /** Removes all of the account's data (used before deleting the account). */
    suspend fun deleteAll(uid: String) {
        val batch = db.batch()
        transactions(uid).get().await().documents.forEach { batch.delete(it.reference) }
        challenges(uid).get().await().documents.forEach { batch.delete(it.reference) }
        batch.delete(user(uid))
        batch.commit().await()
    }

    fun put(t: Transaction) { uid?.let { transactions(it).document(t.id.toString()).set(fromTransaction(t)) } }
    fun remove(t: Transaction) { uid?.let { transactions(it).document(t.id.toString()).delete() } }
    fun put(c: Challenge) { uid?.let { challenges(it).document(c.startedAt.toString()).set(fromChallenge(c)) } }
    fun putBudget(amount: Double) { uid?.let { user(it).set(mapOf(FIELD_BUDGET to amount), SetOptions.merge()) } }

    private fun fromTransaction(t: Transaction) = mapOf(
        "title" to t.title,
        "amount" to t.amount,
        "isIncome" to t.isIncome,
        "category" to t.category?.name,
        "date" to t.date,
    )

    private fun toTransaction(d: DocumentSnapshot): Transaction? = Transaction(
        id = d.id.toLongOrNull() ?: return null,
        title = d.getString("title") ?: "",
        amount = d.getDouble("amount") ?: return null,
        isIncome = d.getBoolean("isIncome") ?: false,
        category = d.getString("category")?.let { name -> Category.entries.firstOrNull { it.name == name } },
        date = d.getLong("date") ?: return null,
    )

    private fun fromChallenge(c: Challenge) = mapOf(
        "type" to c.type.name,
        "startedAt" to c.startedAt,
        "status" to c.status.name,
        "endedAt" to c.endedAt,
    )

    private fun toChallenge(d: DocumentSnapshot): Challenge? = Challenge(
        type = ChallengeType.entries.firstOrNull { it.name == d.getString("type") } ?: return null,
        startedAt = d.getLong("startedAt") ?: return null,
        status = ChallengeStatus.entries.firstOrNull { it.name == d.getString("status") } ?: return null,
        endedAt = d.getLong("endedAt"),
    )

    private companion object {
        const val FIELD_BUDGET = "monthlyBudget"
    }
}
