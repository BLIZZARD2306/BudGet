package com.budgetgoblin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.data.Transaction
import com.budgetgoblin.app.ui.theme.CoinGold
import kotlin.math.roundToInt

@Composable
fun CategoriesScreen(state: BudgetState) {
    val scroll = rememberScrollState()
    val month = state.thisMonth
    val spentBy = Category.entries.associateWith { c -> month.filter { it.category == c }.sumOf { it.amount } }
    val top = spentBy.entries.sortedByDescending { it.value }.take(4)

    ScreenColumn(scroll, headerHeight = 260.dp) {
        ScreenTitle("Top Categories", "$monthName's expenses")
        top.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                row.forEach { (category, spent) -> TopTile(category, spent, Modifier.weight(1f)) }
            }
        }

        SectionTitle("All categories")
        StackingCards(Category.entries, scroll, CardShape) { category ->
            CategoryCard(category, month.filter { it.category == category })
        }
    }
}

@Composable
private fun TopTile(category: Category, spent: Double, modifier: Modifier) {
    Panel(modifier, padding = 16.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryBadge(category, size = 52.dp)
            Spacer(Modifier.height(10.dp))
            Text(category.label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text(
                "${money.format(spent)} · ${(spent / category.monthlyLimit * 100).roundToInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CategoryCard(category: Category, transactions: List<Transaction>) {
    val spent = transactions.sumOf { it.amount }
    val left = category.monthlyLimit - spent
    val over = left < 0
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(categoryColors.getValue(category)), CardShape)
            .height(220.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category, size = 40.dp, selected = true)
                Spacer(Modifier.width(12.dp))
                Text(
                    category.label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                if (over) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "OVER",
                        color = Color(0xFF5A1A00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(CoinGold, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "${money.format(spent)} of ${money.format(category.monthlyLimit)}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (spent / category.monthlyLimit).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.6f).height(8.dp),
                color = if (over) CoinGold else Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (!over) "${money.format(left)} left" else "${money.format(-left)} over budget",
                color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                transactions.take(2).joinToString(" · ") { it.title }.ifEmpty { "No spending yet" },
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
        }
        Mascot(
            categoryMood(spent, category.monthlyLimit),
            size = 100.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
        )
    }
}
