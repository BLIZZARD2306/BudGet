package com.budgetgoblin.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.data.total
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.ui.theme.CoinGold
import kotlin.math.roundToInt

@Composable
fun StatsScreen(state: BudgetState) {
    val scroll = rememberScrollState()
    val month = state.thisMonth
    val income = month.filter { it.isIncome }.total()
    val spent = month.filterNot { it.isIncome }.total()
    val slices = Category.entries
        .map { c -> c to month.filter { it.category == c }.total() }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

    ScreenColumn(scroll) {
        ScreenTitle("Statistics", "$monthName at a glance")
        Panel {
            Donut(slices, spent, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            slices.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 12.dp)) {
                    row.forEach { (c, v) -> LegendItem(c, v, spent, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (slices.isEmpty()) Text("No spending yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stat("Income", "+" + money.format(income), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            Stat("Spent", "−" + money.format(spent), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        SavingsGoal(income, spent)

        slices.firstOrNull()?.let { (top, value) ->
            Spacer(Modifier.height(16.dp))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.CURIOUS, size = 72.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Goblin's report", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Text(
                            "${top.label} takes the biggest bite: ${money.format(value)}, " +
                                "${(value / spent * 100).roundToInt()}% of your spending this month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A ring split by category with small gaps, total in the middle, like the kit's statistics chart. */
@Composable
private fun Donut(slices: List<Pair<Category, Double>>, total: Double, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .size(220.dp)
            .semantics {
                contentDescription = "Spending by category: " +
                    slices.joinToString { (c, v) -> "${c.label} ${(v / total * 100).roundToInt()}%" }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 34.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            if (slices.isEmpty()) {
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (slices.size > 1) 2.5f else 0f
            var start = -90f
            slices.forEach { (c, v) ->
                val sweep = (v / total * 360).toFloat()
                drawArc(c.color(), start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(money.format(total), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendItem(category: Category, value: Double, total: Double, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 30.dp).background(category.color(), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "${(value / total * 100).roundToInt()}% · ${money.format(value)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Panel(modifier, padding = 16.dp) {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SavingsGoal(income: Double, spent: Double) {
    val goal = income * SAVINGS_GOAL
    val saved = (income - spent).coerceAtLeast(0.0)
    val reached = goal > 0 && saved >= goal
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(if (reached) Mood.LEVEL_UP else Mood.INVESTING, size = 72.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (reached) "Savings goal reached!" else "Savings goal",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    when {
                        income == 0.0 -> "Add income to set a goal"
                        reached -> "${money.format(saved)} saved · goal was ${money.format(goal)}"
                        else -> "${money.format(saved)} of ${money.format(goal)} · ${(SAVINGS_GOAL * 100).toInt()}% of income"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ChunkyProgress(
                    progress = if (goal > 0) (saved / goal).toFloat() else 0f,
                    color = if (reached) CoinGold else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
