package com.example.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.budget.data.Category
import com.example.budget.data.Transaction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var isIncome by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(Category.FOOD) }
    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow {
                    listOf("Expense", "Income").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = isIncome == (i == 1),
                            onClick = { isIncome = i == 1 },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Description") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (!isIncome) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.forEach {
                            FilterChip(
                                selected = category == it,
                                onClick = { category = it },
                                label = { Text("${it.emoji} ${it.label}") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && title.isNotBlank(),
                onClick = {
                    onSave(
                        Transaction(
                            id = System.currentTimeMillis(),
                            title = title.trim(),
                            amount = amount ?: return@TextButton,
                            isIncome = isIncome,
                            category = if (isIncome) null else category,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
