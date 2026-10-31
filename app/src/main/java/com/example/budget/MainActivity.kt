package com.example.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.budget.data.BudgetState
import com.example.budget.data.BudgetStore
import com.example.budget.ui.BudGetApp
import com.example.budget.ui.theme.BudGetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = BudgetState(BudgetStore(applicationContext))
        setContent {
            BudGetTheme {
                BudGetApp(state)
            }
        }
    }
}
