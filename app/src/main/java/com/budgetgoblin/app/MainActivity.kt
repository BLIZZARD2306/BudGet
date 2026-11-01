package com.budgetgoblin.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.budgetgoblin.app.data.Auth
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.BudgetStore
import com.budgetgoblin.app.ui.BudGetApp
import com.budgetgoblin.app.ui.theme.BudGetTheme

class MainActivity : ComponentActivity() {
    /** A link the app was opened with (an email sign-in link), until it's been handled. */
    private val incomingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingLink.value = intent?.dataString
        val state = BudgetState(BudgetStore(applicationContext))
        val auth = Auth(applicationContext)
        setContent {
            BudGetTheme {
                BudGetApp(state, auth, incomingLink.value, onLinkHandled = { incomingLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingLink.value = intent.dataString
    }
}
