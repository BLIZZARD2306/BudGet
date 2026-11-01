package com.budgetgoblin.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.Transaction
import com.budgetgoblin.app.data.startOfDay
import com.budgetgoblin.app.ui.theme.Mint
import com.budgetgoblin.app.ui.theme.MintDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun HomeScreen(state: BudgetState, signedIn: Boolean, onHelp: () -> Unit, onAccount: () -> Unit) {
    val scroll = rememberScrollState()
    val month = state.thisMonth
    val spent = month.filterNot { it.isIncome }.sumOf { it.amount }
    val budget = state.monthlyBudget
    var editingBudget by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    ScreenColumn(scroll, headerHeight = 330.dp) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Account button: the goblin avatar, with a green dot while signed in and syncing.
            Box(
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(onClick = onAccount)
                    .semantics { contentDescription = if (signedIn) "Account, signed in and syncing" else "Sign in to sync" },
                contentAlignment = Alignment.Center,
            ) {
                Mascot(Mood.HAPPY, size = 40.dp)
                if (signedIn) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    )
                }
            }
            if (!signedIn) {
                TextButton(onClick = onAccount) { Text("Sign in to sync") }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onHelp, modifier = Modifier.semantics { contentDescription = "Help" }) {
                Text("?", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Budget for $monthName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier
                    .clickable { editingBudget = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Monthly budget ${money.format(budget)}, tap to edit" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(money.format(budget), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        RemainingCard(remaining = budget - spent, spent = spent, budget = budget)
        Spacer(Modifier.height(16.dp))
        WeekPanel(state.transactions)
        Spacer(Modifier.height(20.dp))
        SpeechBubble(overallTip(month, budget))

        SectionTitle("Recent") {
            if (state.transactions.size > 5) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "See all") }
            }
        }
        TransactionList(if (showAll) state.transactions else state.transactions.take(5), state::delete)
    }

    if (editingBudget) {
        BudgetDialog(
            current = budget,
            onDismiss = { editingBudget = false },
            onSave = { state.setBudget(it); editingBudget = false },
        )
    }
}

/** The kit's green "Remaining budget" panel, as a 3D tilt card with the goblin floating above it. */
@Composable
private fun RemainingCard(remaining: Double, spent: Double, budget: Double) {
    TiltCard(
        background = Brush.linearGradient(listOf(Mint, MintDark)),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Mascot(
                if (remaining > 0) Mood.GUARDING else Mood.BROKE,
                size = 92.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).depth(50.dp),
            )
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Remaining budget", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.depth(20.dp))
                Text(
                    money.format(remaining),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.depth(40.dp),
                )
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 1f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).depth(30.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${money.format(spent)} spent · ${if (budget > 0) (spent / budget * 100).toInt() else 0}% used",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    modifier = Modifier.depth(30.dp),
                )
            }
        }
    }
}

/** Spending for each of the last 7 days as rounded bars (today in coral), plus today vs yesterday. */
@Composable
private fun WeekPanel(transactions: List<Transaction>) {
    val days = (6 downTo 0).map { ago ->
        val from = startOfDay(ago)
        val to = startOfDay(ago - 1)
        transactions.filter { !it.isIncome && it.date in from until to }.sumOf { it.amount }
    }
    val labels = (6 downTo 0).map { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -it) }.get(Calendar.DAY_OF_MONTH) }
    val max = days.max().coerceAtLeast(1.0)
    val bar = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val today = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant

    Panel {
        Text("This week", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics {
                    contentDescription = "Spending over the last 7 days: " +
                        days.zip(labels).joinToString { (v, d) -> "day $d ${money.format(v)}" }
                }
        ) {
            val slot = size.width / days.size
            val w = 8.dp.toPx()
            days.forEachIndexed { i, v ->
                val x = slot * i + slot / 2 - w / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                val h = (v / max * size.height).toFloat().coerceAtLeast(if (v > 0) w else 0f)
                drawRoundRect(
                    if (i == days.lastIndex) today else bar,
                    Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, d ->
                Text(
                    "$d",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = if (i == labels.lastIndex) today else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == labels.lastIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row {
            DayTotal("TODAY", days[6], today, Modifier.weight(1f))
            DayTotal("YESTERDAY", days[5], MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayTotal(label: String, amount: Double, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money.format(amount), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TransactionList(transactions: List<Transaction>, onDelete: (Transaction) -> Unit) {
    Panel(padding = 0.dp) {
        if (transactions.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(Mood.HELP, size = 88.dp)
                Spacer(Modifier.height(8.dp))
                Text("Nothing here yet. Tap + to log your first transaction.", textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.padding(vertical = 6.dp)) {
                transactions.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    TransactionRow(t, onDelete = { onDelete(t) })
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.category != null) {
            CategoryBadge(t.category)
        } else {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("💰", fontSize = 20.sp) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${t.category?.label ?: "Income"} · ${dayLabel(t.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (t.isIncome) "+" else "−") + money.format(t.amount),
            color = if (t.isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = "Delete ${t.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun dayLabel(millis: Long): String = when {
    millis >= startOfDay(0) -> "Today"
    millis >= startOfDay(1) -> "Yesterday"
    else -> SimpleDateFormat("MMM d").format(Date(millis))
}

/** The kit's "Create budget" screen as a dialog: type an amount or pick a preset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.toLong().toString()) }
    val amount = text.toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Mascot(Mood.GUARDING, size = 88.dp) },
        title = { Text("Budget for $monthName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monthly budget") },
                    prefix = { Text(money.currency?.symbol ?: "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000, 1500, 2000, 2500, 3000, 4000).forEach { preset ->
                        FilterChip(
                            selected = amount == preset.toDouble(),
                            onClick = { text = preset.toString() },
                            label = { Text(money.format(preset).substringBefore('.')) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { amount?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
