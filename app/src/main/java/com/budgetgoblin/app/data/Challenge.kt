package com.budgetgoblin.app.data

import java.util.concurrent.TimeUnit

/** A spending challenge: avoid [category] (or any spending when null) for [days] days. */
enum class ChallengeType(val title: String, val category: Category?, val days: Int) {
    NO_SHOPPING("No shopping", Category.SHOPPING, 7),
    NO_COFFEE("No coffee runs", Category.COFFEE, 5),
    NO_FUN("No fun spending", Category.FUN, 3),
    NO_SPEND("No-spend weekend", null, 2);

    val durationMillis get() = TimeUnit.DAYS.toMillis(days.toLong())

    fun isBrokenBy(t: Transaction) = !t.isIncome && (category == null || t.category == category)
}

enum class ChallengeStatus { ACTIVE, COMPLETED, FAILED }

data class Challenge(
    val type: ChallengeType,
    val startedAt: Long,
    val status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val endedAt: Long? = null,
) {
    val endsAt get() = startedAt + type.durationMillis

    fun encode() = listOf(type.name, startedAt, status.name, endedAt ?: "").joinToString("|")

    companion object {
        fun decode(line: String): Challenge? {
            val p = line.split("|")
            if (p.size != 4) return null
            return Challenge(
                type = ChallengeType.entries.firstOrNull { it.name == p[0] } ?: return null,
                startedAt = p[1].toLongOrNull() ?: return null,
                status = ChallengeStatus.entries.firstOrNull { it.name == p[2] } ?: return null,
                endedAt = p[3].toLongOrNull(),
            )
        }
    }
}
