package com.budgetgoblin.app.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import com.budgetgoblin.app.data.Account
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.data.Transaction
import com.budgetgoblin.app.ui.theme.Nunito

/**
 * Logging a transaction as a full-screen step, like a Duolingo lesson: ✕ and a progress bar on top,
 * one big question, pickable tiles, and a full-width button pinned to the bottom.
 */
@Composable
fun AddScreen(accounts: List<Account>, onCancel: () -> Unit, onSave: (Transaction) -> Unit) {
    var accountId by rememberSaveable { mutableLongStateOf(Account.CASH_ID) }
    var isIncome by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(Category.FOOD) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var split by rememberSaveable { mutableStateOf(false) }
    var toBudget by rememberSaveable { mutableStateOf(false) }
    var people by rememberSaveable { mutableIntStateOf(2) }
    val total = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val splitting = split && !isIncome
    // When splitting a bill, only your share counts toward the budget.
    val amount = total?.let { if (splitting) it / people else it }
    val progress = listOf(true, amount != null, title.isNotBlank()).count { it } / 3f
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(scheme.background).imePadding()) {
        Row(
            Modifier.statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.px_close), "Close", tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
            ChunkyProgress(progress, color = scheme.tertiary, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    isIncome -> "How much came in?"
                    splitting -> "Split a bill"
                    else -> "What did you spend on?"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            SpeechBubble(
                Tip(
                    if (splitting) Mood.SPLITTING else if (isIncome) Mood.INVESTING else Mood.ADDING,
                    when {
                        isIncome -> "Money in! Tell me how much."
                        splitting -> "Only your share counts toward the budget."
                        else -> "Pick a category, then the amount."
                    },
                ),
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple(false, "Expense", R.drawable.px_money_out), Triple(true, "Income", R.drawable.px_money_in)).forEach { (income, label, icon) ->
                    val selected = isIncome == income
                    Tile(selected, onClick = { isIncome = income }, modifier = Modifier.weight(1f).height(60.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelIcon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, size = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            if (!isIncome) {
                Label("Category")
                Category.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { c ->
                            Tile(c == category, onClick = { category = c }, modifier = Modifier.weight(1f).height(104.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CategoryBadge(c, size = 44.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(c.label, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Label("How much?")
            Panel(padding = 16.dp) {
                BasicTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(
                        fontFamily = Nunito,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = scheme.onSurface,
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (splitting) "Total bill" else "Amount" },
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.Center) {
                            if (amountText.isEmpty()) {
                                Text(money.format(0), fontSize = 40.sp, fontWeight = FontWeight.Black, color = scheme.outlineVariant)
                            }
                            field()
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20, 50, 100).forEach { preset ->
                    Tile(total == preset.toDouble(), onClick = { amountText = preset.toString() }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(money.format(preset).substringBefore('.'), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }

            Label(if (isIncome) "Received in" else "Paid with")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                accounts.forEach { a ->
                    Tile(a.id == accountId, onClick = { accountId = a.id }, modifier = Modifier.height(56.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(26.dp).background(Brush.linearGradient(CardColors[a.color.coerceIn(0, CardColors.lastIndex)]), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { PixelIcon(a.kind.icon(), contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, size = 14.dp) }
                            Spacer(Modifier.width(8.dp))
                            Text(a.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                }
            }

            Label("What for?")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(if (isIncome) "Salary, gift…" else "${category.label} (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (isIncome) {
                Spacer(Modifier.height(16.dp))
                Panel(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Add to my current budget", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Raises Remaining until your next payday",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = toBudget, onCheckedChange = { toBudget = it })
                    }
                }
            }

            if (!isIncome) {
                Spacer(Modifier.height(16.dp))
                Panel(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Split with friends", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                        Switch(checked = split, onCheckedChange = { split = it })
                    }
                    if (split) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tile(false, onClick = { people = (people - 1).coerceAtLeast(2) }, modifier = Modifier.size(48.dp)) {
                                Text("−", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Text(
                                "$people people",
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(96.dp),
                            )
                            Tile(false, onClick = { people = (people + 1).coerceAtMost(20) }, modifier = Modifier.size(48.dp)) {
                                Text("+", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            if (amount != null) {
                                Text(
                                    "YOU PAY\n" + money.format(amount),
                                    color = scheme.primary,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        HorizontalDivider(thickness = 2.dp, color = scheme.outlineVariant)
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            PillButton(
                if (isIncome) "Add income" else "Add expense",
                enabled = amount != null,
                color = scheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val value = amount ?: return@PillButton
                    val label = title.trim().ifEmpty { if (isIncome) "Income" else category.label }
                    onSave(
                        Transaction(
                            id = System.currentTimeMillis(),
                            title = label + if (splitting) " (split ÷$people)" else "",
                            amount = value,
                            isIncome = isIncome,
                            category = if (isIncome) null else category,
                            toBudget = isIncome && toBudget,
                            accountId = accountId,
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp),
    )
}
