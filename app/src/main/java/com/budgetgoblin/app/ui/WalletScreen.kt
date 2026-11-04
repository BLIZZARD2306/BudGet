package com.budgetgoblin.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.Account
import com.budgetgoblin.app.data.AccountKind
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.ui.theme.CoinGold

/** The wallet's card colours (gradient pairs), picked per card. */
val CardColors = listOf(
    listOf(Color(0xFF3FBF73), Color(0xFF1E8A4F)), // green
    listOf(Color(0xFF4F8BF0), Color(0xFF2B5BC4)), // blue
    listOf(Color(0xFF8E6BE8), Color(0xFF5A3FB8)), // purple
    listOf(Color(0xFFFF8A65), Color(0xFFE2553B)), // coral
    listOf(Color(0xFF2FC0CF), Color(0xFF15879A)), // teal
    listOf(Color(0xFF4A4E5E), Color(0xFF262833)), // graphite
)

@DrawableRes
fun AccountKind.icon(): Int = when (this) {
    AccountKind.CASH -> R.drawable.px_cash
    AccountKind.E_WALLET -> R.drawable.px_phone
    AccountKind.DEBIT, AccountKind.CREDIT -> R.drawable.px_card
}

private val CardHeight = 196.dp
private val CardPeek = 68.dp

@Composable
fun WalletScreen(state: BudgetState, onOpenCard: (Account) -> Unit, scrollToTop: Int = 0) {
    val scroll = rememberScrollState()
    // Tapping Wallet again in the nav bar brings you back to the top.
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) scroll.animateScrollTo(0) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val accounts = state.accounts

    ScreenColumn(scroll) {
        ScreenTitle("Wallet", "${money.format(state.totalBalance)} across ${accounts.size} ${if (accounts.size == 1) "card" else "cards"}")

        // Wallet-style stack: only each card's top strip shows, the last card shows in full.
        Box(Modifier.fillMaxWidth().height(CardPeek * (accounts.size - 1) + CardHeight)) {
            accounts.forEachIndexed { i, a ->
                WalletCard(
                    account = a,
                    balance = state.balanceOf(a),
                    modifier = Modifier.padding(top = CardPeek * i),
                    onClick = { onOpenCard(a) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinePillButton(
            "Add card",
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
            leading = { PixelIcon(R.drawable.px_nav_add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 18.dp) },
        )

        SectionTitle("Category budgets")
        val month = state.thisMonth
        Panel(padding = 0.dp) {
            Category.entries.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                BudgetRow(c, month.filter { it.category == c }.sumOf { it.amount })
            }
        }
    }

    if (adding) {
        CardDialog(
            existing = null,
            onDismiss = { adding = false },
            onSave = { state.saveAccount(it); adding = false },
        )
    }
}

/** One card in the wallet. Its top strip (name and balance) is what shows when stacked. */
@Composable
fun WalletCard(account: Account, balance: Double, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, height: Dp? = CardHeight) {
    val colors = CardColors[account.color.coerceIn(0, CardColors.lastIndex)]
    Box(
        modifier
            .fillMaxWidth()
            // No fixed height = real card proportions (85.6 × 54 mm).
            .then(if (height != null) Modifier.height(height) else Modifier.aspectRatio(1.586f))
            .background(Brush.linearGradient(colors), CardShape)
            .border(2.dp, Color.White.copy(alpha = 0.12f), CardShape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open ${account.name}", onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "${account.name}, ${account.kind.label}, balance ${money.format(balance)}" }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(account.kind.icon(), contentDescription = null, tint = Color.White, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(account.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(money.format(balance), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            // A pixel "chip", like a real card's.
            Box(Modifier.size(width = 34.dp, height = 24.dp).background(CoinGold, PixelShape(2.dp, 2)))
            Spacer(Modifier.height(12.dp))
            Text(account.kind.label.uppercase(), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Text(
            if (balance < 0) "OWED" else "BALANCE",
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** A slim category budget: badge, name, bar, and "spent / limit". */
@Composable
private fun BudgetRow(category: Category, spent: Double) {
    val over = spent > category.monthlyLimit
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(category, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.label, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(
                    "${money.format(spent)} / ${money.format(category.monthlyLimit).substringBefore('.')}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ChunkyProgress((spent / category.monthlyLimit).toFloat(), color = if (over) MaterialTheme.colorScheme.primary else category.color())
        }
    }
}

/** Add a new card, or edit one ([existing]). Cash can be edited but not renamed into another kind. */
@Composable
fun CardDialog(existing: Account?, onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: (() -> Unit)? = null) {
    val isCash = existing?.id == Account.CASH_ID
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: AccountKind.E_WALLET) }
    var start by rememberSaveable { mutableStateOf(existing?.startBalance?.let { if (it == 0.0) "" else it.toString() } ?: "") }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: 1) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val startAmount = if (start.isBlank()) 0.0 else start.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a card" else "Edit ${existing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletCard(
                    Account(existing?.id ?: 0, name.ifBlank { "Card name" }, kind, startAmount ?: 0.0, color),
                    balance = startAmount ?: 0.0,
                    height = null,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Name (e.g. GCash, BPI)") },
                    singleLine = true,
                    enabled = !isCash,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isCash) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountKind.entries.filter { it != AccountKind.CASH }.forEach { k ->
                            Tile(k == kind, onClick = { kind = k }, modifier = Modifier.weight(1f).height(64.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    PixelIcon(k.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp)
                                    Text(k.label.substringBefore(' '), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    label = { Text("Money on it before tracking") },
                    prefix = { Text(appCurrency.symbol) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardColors.forEachIndexed { i, pair ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Brush.linearGradient(pair), CircleShape)
                                .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable(onClickLabel = "Colour ${i + 1}") { color = i },
                        )
                    }
                }
                if (onDelete != null && !isCash) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Remove this card", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (isCash || name.isNotBlank()) && startAmount != null,
                onClick = {
                    onSave(
                        Account(
                            id = existing?.id ?: System.currentTimeMillis(),
                            name = if (isCash) "Cash" else name.trim(),
                            kind = if (isCash) AccountKind.CASH else kind,
                            startBalance = startAmount ?: 0.0,
                            color = color,
                        )
                    )
                },
            ) { Text("SAVE", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.ExtraBold) } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${existing?.name}?") },
            text = { Text("Its history moves to Cash, so your totals stay right.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("REMOVE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL", fontWeight = FontWeight.ExtraBold) } },
        )
    }
}

/** A card's audit: the card, its totals, and every entry with the balance after it. */
@Composable
fun CardDetailScreen(state: BudgetState, account: Account, onBack: () -> Unit) {
    val history = state.historyOf(account)
    val moneyIn = history.filter { it.isIncome }.sumOf { it.amount }
    val moneyOut = history.filterNot { it.isIncome }.sumOf { it.amount }
    val balance = state.balanceOf(account)
    var editing by rememberSaveable { mutableStateOf(false) }

    ScreenColumn(rememberScrollState()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
                PixelIcon(R.drawable.px_back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(account.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Center)
            Box(Modifier.size(48.dp).clickable(onClickLabel = "Edit card") { editing = true }, contentAlignment = Alignment.Center) {
                PixelIcon(R.drawable.px_edit, contentDescription = "Edit card", tint = MaterialTheme.colorScheme.onSurface, size = 20.dp)
            }
        }
        WalletCard(account, balance)
        Spacer(Modifier.height(16.dp))
        Panel(padding = 16.dp) {
            Row {
                AuditTotal("STARTED WITH", account.startBalance, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                AuditTotal("MONEY IN", moneyIn, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                AuditTotal("MONEY OUT", moneyOut, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }

        SectionTitle("History")
        Panel(padding = 0.dp) {
            if (history.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(Mood.CURIOUS, size = 80.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing paid with this card yet. Pick it under \"Paid with\" when you add an entry.", textAlign = TextAlign.Center)
                }
            } else {
                // Balance after each entry, worked out from the oldest one forward.
                val after = HashMap<Long, Double>()
                var running = account.startBalance
                history.asReversed().forEach { t ->
                    running += if (t.isIncome) t.amount else -t.amount
                    after[t.id] = running
                }
                Column(Modifier.padding(vertical = 6.dp)) {
                    history.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        AuditRow(t, after[t.id] ?: 0.0)
                    }
                }
            }
        }
    }

    if (editing) {
        CardDialog(
            existing = account,
            onDismiss = { editing = false },
            onSave = { state.saveAccount(it); editing = false },
            onDelete = { editing = false; state.removeAccount(account); onBack() },
        )
    }
}

@Composable
private fun AuditTotal(label: String, amount: Double, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money.format(amount), fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun AuditRow(t: com.budgetgoblin.app.data.Transaction, balanceAfter: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (t.category != null) {
            CategoryBadge(t.category)
        } else {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape), contentAlignment = Alignment.Center) {
                PixelIcon(R.drawable.px_income, contentDescription = null, tint = Color.White)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.title, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                "${t.category?.label ?: if (t.toBudget) "Budget top-up" else "Income"} · ${java.text.SimpleDateFormat("MMM d").format(java.util.Date(t.date))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (t.isIncome) "+" else "−") + money.format(t.amount),
                color = if (t.isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
            )
            Text("= " + money.format(balanceAfter), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
