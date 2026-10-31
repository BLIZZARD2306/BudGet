package com.example.budget.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.example.budget.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.budget.data.Category
import com.example.budget.data.Transaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

private val money: NumberFormat = NumberFormat.getCurrencyInstance()
private val CardShape = RoundedCornerShape(28.dp)

private val categoryColors = mapOf(
    Category.FOOD to listOf(Color(0xFFF97316), Color(0xFFDB2777)),
    Category.TRANSPORT to listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)),
    Category.BILLS to listOf(Color(0xFF8B5CF6), Color(0xFF4338CA)),
    Category.FUN to listOf(Color(0xFF10B981), Color(0xFF0F766E)),
    Category.OTHER to listOf(Color(0xFF64748B), Color(0xFF334155)),
)

@Composable
fun BudgetScreen(
    transactions: List<Transaction>,
    onAdd: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val income = transactions.filter { it.isIncome }.sumOf { it.amount }
    val spent = transactions.filterNot { it.isIncome }.sumOf { it.amount }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            LargeFloatingActionButton(onClick = { adding = true }, modifier = Modifier.navigationBarsPadding()) {
                Text("+", fontSize = 32.sp, modifier = Modifier.semantics { contentDescription = "Add transaction" })
            }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Starts at the very top of the screen: StackingCards measures pin offsets from there.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.statusBarsPadding().height(16.dp))
                Text(
                    "Budget",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    SimpleDateFormat("MMMM yyyy").format(Date()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                BalanceCard(balance = income - spent, income = income, spent = spent)

                Spacer(Modifier.height(36.dp))
                SectionTitle("Categories")
                Spacer(Modifier.height(12.dp))
                StackingCards(Category.entries, scroll, CardShape) { category ->
                    CategoryCard(category, transactions.filter { it.category == category })
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle("All transactions")
                transactions.forEach { TransactionRow(it, onDelete = { onDelete(it) }) }
                Spacer(Modifier.navigationBarsPadding().height(120.dp))
            }
            // Keeps scrolled content from running under the status bar icons.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }

    if (adding) {
        AddTransactionDialog(
            onDismiss = { adding = false },
            onSave = { onAdd(it); adding = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun BalanceCard(balance: Double, income: Double, spent: Double) {
    TiltCard(
        background = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF7C3AED), Color(0xFF312E81))),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total balance", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.depth(20.dp))
                Spacer(Modifier.weight(1f))
                // Mascot floats highest above the card. FilterQuality.None keeps the pixel art crisp.
                Image(
                    bitmap = ImageBitmap.imageResource(R.drawable.mascot),
                    contentDescription = "BudGet mascot",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(64.dp).depth(50.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                money.format(balance),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.depth(40.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.depth(30.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Income", "+" + money.format(income), Modifier.weight(1f))
                Pill("Spent", "−" + money.format(spent), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Pill(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryCard(category: Category, transactions: List<Transaction>) {
    val spent = transactions.sumOf { it.amount }
    val left = category.monthlyLimit - spent
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(categoryColors.getValue(category)), CardShape)
            .padding(24.dp)
            .height(200.dp),
    ) {
        Text(
            "${category.emoji}  ${category.label}",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${money.format(spent)} of ${money.format(category.monthlyLimit)}",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (spent / category.monthlyLimit).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (left >= 0) "${money.format(left)} left" else "${money.format(-left)} over budget",
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.weight(1f))
        val recent = transactions.take(2).joinToString(" · ") { it.title }
        Text(
            recent.ifEmpty { "No spending yet" },
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TransactionRow(t: Transaction, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.category?.emoji ?: "💰", fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, fontWeight = FontWeight.Medium)
            Text(
                t.category?.label ?: "Income",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (t.isIncome) "+" else "−") + money.format(t.amount),
            color = if (t.isIncome) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onDelete) {
            Text("✕", modifier = Modifier.semantics { contentDescription = "Delete ${t.title}" })
        }
    }
    HorizontalDivider()
}
