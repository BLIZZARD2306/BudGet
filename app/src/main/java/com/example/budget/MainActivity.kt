package com.example.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.example.budget.data.BudgetStore
import com.example.budget.ui.BudgetScreen
import com.example.budget.ui.theme.BudGetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = BudgetStore(applicationContext)
        setContent {
            BudGetTheme {
                val transactions = remember { mutableStateListOf(*store.load().toTypedArray()) }
                BudgetScreen(
                    transactions = transactions,
                    onAdd = { transactions.add(0, it); store.save(transactions) },
                    onDelete = { transactions.remove(it); store.save(transactions) },
                )
            }
        }
    }
}
