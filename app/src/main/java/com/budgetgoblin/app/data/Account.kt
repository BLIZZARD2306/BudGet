package com.budgetgoblin.app.data

/** What kind of money holder a card is; only changes its label and icon. */
enum class AccountKind(val label: String) {
    CASH("Cash"),
    E_WALLET("E-wallet"),
    DEBIT("Debit card"),
    CREDIT("Credit card"),
}

/**
 * A card in the wallet (cash, GCash, a bank card...). Its balance is [startBalance] plus money in
 * minus money out of the transactions paid with it. [color] indexes the wallet's card colours.
 */
data class Account(
    val id: Long,
    val name: String,
    val kind: AccountKind,
    val startBalance: Double,
    val color: Int,
) {
    fun encode() = listOf(id, kind.name, startBalance, color, name.replace('\n', ' ').replace('|', '/')).joinToString("|")

    companion object {
        /** Cash always exists; transactions with no card chosen count here. */
        const val CASH_ID = 0L

        fun cash(startBalance: Double = 0.0) = Account(CASH_ID, "Cash", AccountKind.CASH, startBalance, 0)

        fun decode(line: String): Account? {
            val p = line.split("|", limit = 5)
            if (p.size != 5) return null
            return Account(
                id = p[0].toLongOrNull() ?: return null,
                kind = AccountKind.entries.firstOrNull { it.name == p[1] } ?: AccountKind.CASH,
                startBalance = p[2].toDoubleOrNull() ?: 0.0,
                color = p[3].toIntOrNull() ?: 0,
                name = p[4],
            )
        }
    }
}
