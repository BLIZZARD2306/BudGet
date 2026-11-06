package com.budgetgoblin.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch

/** SYNCED: the account answered. BLOCKED: it refused this phone (e.g. App Check). OFFLINE: unreachable. */
enum class SyncStatus { CONNECTING, SYNCED, BLOCKED, OFFLINE }

/**
 * Mirrors [BudgetState] to Firestore under users/{uid}: one document per transaction and per
 * challenge (so two phones never overwrite each other's whole list) plus the budget on the user
 * document. Firestore keeps an offline copy, so writes made without internet upload later.
 */
class CloudSync(private val state: BudgetState) {
    private val db get() = FirebaseFirestore.getInstance()
    private var listeners = emptyList<ListenerRegistration>()
    private var uid: String? = null

    /** What's really happening with sync, for the account screen and the avatar dot. */
    var status by mutableStateOf(SyncStatus.CONNECTING)
        private set

    /** Records why the account couldn't be reached (a refused request means this phone isn't allowed). */
    fun markFailed(e: Exception) {
        status = if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            SyncStatus.BLOCKED
        } else {
            SyncStatus.OFFLINE
        }
    }

    private fun user(uid: String) = db.collection("users").document(uid)
    private fun transactions(uid: String) = user(uid).collection("transactions")
    private fun challenges(uid: String) = user(uid).collection("challenges")
    private fun accounts(uid: String) = user(uid).collection("accounts")

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
        var accountsLive = false
        status = SyncStatus.CONNECTING
        listeners = listOf(
            transactions(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                if (e != null) return@addSnapshotListener markFailed(e)
                if (snap == null) return@addSnapshotListener
                transactionsLive = transactionsLive || !snap.metadata.isFromCache
                if (!snap.metadata.isFromCache) status = SyncStatus.SYNCED
                if (transactionsLive) state.applyTransactions(snap.documents.mapNotNull(::toTransaction))
            },
            challenges(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                challengesLive = challengesLive || !snap.metadata.isFromCache
                if (challengesLive) state.applyChallenges(snap.documents.mapNotNull(::toChallenge))
            },
            accounts(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                accountsLive = accountsLive || !snap.metadata.isFromCache
                if (accountsLive) state.applyAccounts(snap.documents.mapNotNull(::toAccount))
            },
            user(uid).addSnapshotListener { snap, _ ->
                snap?.getDouble(FIELD_BUDGET)?.let(state::applyBudget)
                snap?.let(::toSchedule)?.let(state::applySchedule)
            },
        )
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners = emptyList()
        uid = null
        state.cloud = null
        status = SyncStatus.CONNECTING
    }

    /** Asks the server (never the offline cache, which may just be empty); throws if it can't be reached. */
    suspend fun hasData(uid: String) = !transactions(uid).limit(1).get(Source.SERVER).await().isEmpty

    /** Copies everything on this phone into the account, in one go. */
    suspend fun upload(uid: String) {
        commit(
            state.transactions.map { t -> { b: WriteBatch -> b.set(transactions(uid).document(t.id.toString()), fromTransaction(t)) } } +
                state.challenges.map { c -> { b: WriteBatch -> b.set(challenges(uid).document(c.startedAt.toString()), fromChallenge(c)) } } +
                state.accounts.map { a -> { b: WriteBatch -> b.set(accounts(uid).document(a.id.toString()), fromAccount(a)) } } +
                { b: WriteBatch -> b.set(user(uid), mapOf(FIELD_BUDGET to state.monthlyBudget) + fromSchedule(state.schedule), SetOptions.merge()) }
        )
    }

    /** Removes all of the account's data (used before deleting the account). The user document goes last. */
    suspend fun deleteAll(uid: String) {
        val docs = transactions(uid).get().await().documents +
            challenges(uid).get().await().documents +
            accounts(uid).get().await().documents
        commit(docs.map { d -> { b: WriteBatch -> b.delete(d.reference) } } + { b: WriteBatch -> b.delete(user(uid)) })
    }

    /** Runs [writes] in batches of at most 500, Firestore's limit per batch, in order. */
    private suspend fun commit(writes: List<(WriteBatch) -> Any>) {
        writes.chunked(MAX_BATCH).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    fun put(t: Transaction) { uid?.let { transactions(it).document(t.id.toString()).set(fromTransaction(t)) } }
    fun remove(t: Transaction) { uid?.let { transactions(it).document(t.id.toString()).delete() } }
    fun put(c: Challenge) { uid?.let { challenges(it).document(c.startedAt.toString()).set(fromChallenge(c)) } }
    fun put(a: Account) { uid?.let { accounts(it).document(a.id.toString()).set(fromAccount(a)) } }
    fun remove(a: Account) { uid?.let { accounts(it).document(a.id.toString()).delete() } }
    fun putBudget(amount: Double) { uid?.let { user(it).set(mapOf(FIELD_BUDGET to amount), SetOptions.merge()) } }
    fun putSchedule(s: PaySchedule) { uid?.let { user(it).set(fromSchedule(s), SetOptions.merge()) } }

    private fun fromAccount(a: Account) = mapOf("name" to a.name, "kind" to a.kind.name, "start" to a.startBalance, "color" to a.color)

    private fun toAccount(d: DocumentSnapshot): Account? = Account(
        id = d.id.toLongOrNull() ?: return null,
        name = d.getString("name") ?: "Card",
        kind = AccountKind.entries.firstOrNull { it.name == d.getString("kind") } ?: AccountKind.CASH,
        startBalance = d.getDouble("start") ?: 0.0,
        color = (d.getLong("color") ?: 0L).toInt(),
    )

    private fun fromSchedule(s: PaySchedule) = mapOf("payCycle" to s.cycle.name, "payWeekStart" to s.weekStart, "payAnchor" to s.anchor)

    /** The pay schedule saved on the account, or null if none was ever set (older accounts). */
    private fun toSchedule(d: DocumentSnapshot): PaySchedule? {
        val cycle = PayCycle.entries.firstOrNull { it.name == d.getString("payCycle") } ?: return null
        return PaySchedule(cycle, (d.getLong("payWeekStart") ?: 2L).toInt(), d.getLong("payAnchor") ?: 0L)
    }

    private fun fromTransaction(t: Transaction) = mapOf(
        "title" to t.title,
        "amount" to t.amount,
        "isIncome" to t.isIncome,
        "category" to t.category?.name,
        "date" to t.date,
        "toBudget" to t.toBudget,
        "account" to t.accountId,
    )

    private fun toTransaction(d: DocumentSnapshot): Transaction? = Transaction(
        id = d.id.toLongOrNull() ?: return null,
        title = d.getString("title") ?: "",
        amount = d.getDouble("amount") ?: return null,
        isIncome = d.getBoolean("isIncome") ?: false,
        category = d.getString("category")?.let { name -> Category.entries.firstOrNull { it.name == name } },
        date = d.getLong("date") ?: return null,
        toBudget = d.getBoolean("toBudget") ?: false,
        accountId = d.getLong("account") ?: Account.CASH_ID,
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
        const val MAX_BATCH = 500
    }
}
