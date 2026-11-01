package com.budgetgoblin.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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

@Composable
fun AddScreen(onCancel: () -> Unit, onSave: (Transaction) -> Unit) {
    val scroll = rememberScrollState()
    var isIncome by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(Category.FOOD) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var split by rememberSaveable { mutableStateOf(false) }
    var people by rememberSaveable { mutableIntStateOf(2) }
    val total = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val splitting = split && !isIncome
    // When splitting a bill, only your share counts toward the budget.
    val amount = total?.let { if (splitting) it / people else it }

    ScreenColumn(scroll, headerHeight = 230.dp) {
        ScreenTitle(if (splitting) "Split a bill" else "Add transaction", "Log what you spent or earned")
        Panel {
            Mascot(
                if (splitting) Mood.SPLITTING else if (isIncome) Mood.INVESTING else Mood.ADDING,
                size = 88.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Expense", "Income").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = isIncome == (i == 1),
                        onClick = { isIncome = i == 1 },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            if (!isIncome) {
                Label("Category")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Category.entries.forEach { CategoryTile(it, selected = it == category, onClick = { category = it }) }
                }
            }

            Label("How much?")
            BasicTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Amount" },
                decorationBox = { field ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (amountText.isEmpty()) {
                            Text(
                                money.format(0),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        } else field()
                        HorizontalDivider(Modifier.width(160.dp).padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20, 50, 100).forEach { preset ->
                    QuickAmount(preset, selected = total == preset.toDouble(), onClick = { amountText = preset.toString() }, modifier = Modifier.weight(1f))
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

            if (!isIncome) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Split with friends", Modifier.weight(1f))
                    Switch(checked = split, onCheckedChange = { split = it })
                }
                if (split) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { people = (people - 1).coerceAtLeast(2) }) { Text("−") }
                        Text("$people people", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(90.dp))
                        FilledTonalIconButton(onClick = { people = (people + 1).coerceAtMost(20) }) { Text("+") }
                        Spacer(Modifier.weight(1f))
                        if (amount != null) {
                            Text(
                                "You pay\n" + money.format(amount),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_close), "Cancel", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                PillButton(
                    "Add",
                    enabled = amount != null,
                    modifier = Modifier.weight(1f),
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
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
    )
}

/** A category tile like the kit's: white with a coloured icon, or filled with the category colour when chosen. */
@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .size(width = 92.dp, height = 104.dp)
            .semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Column(
            Modifier
                .background(
                    if (selected) Brush.linearGradient(categoryColors.getValue(category))
                    else SolidColor(MaterialTheme.colorScheme.surface),
                    RoundedCornerShape(20.dp),
                )
                .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CategoryBadge(category, size = 44.dp, selected = selected)
            Spacer(Modifier.height(8.dp))
            Text(
                category.label.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickAmount(value: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(40.dp),
    ) {
        Text(
            money.format(value).substringBefore('.'),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
