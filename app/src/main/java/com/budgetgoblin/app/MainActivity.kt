package com.budgetgoblin.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.budgetgoblin.app.data.Auth
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.BudgetStore
import com.budgetgoblin.app.ui.AppCurrency
import com.budgetgoblin.app.ui.BudGetApp
import com.budgetgoblin.app.ui.appCurrency
import com.budgetgoblin.app.ui.theme.BudGetTheme
import com.budgetgoblin.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    /** A link the app was opened with (an email sign-in link), until it's been handled. */
    private val incomingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingLink.value = intent?.dataString
        appCurrency = AppCurrency.load(this)
        val state = BudgetState(BudgetStore(applicationContext))
        val auth = Auth(applicationContext)
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        val themeMode = mutableStateOf(
            ThemeMode.entries.firstOrNull { it.name == settings.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM
        )
        setContent {
            val dark = when (themeMode.value) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not just the phone's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            BudGetTheme(themeMode.value) {
                BudGetApp(
                    state,
                    auth,
                    incomingLink.value,
                    onLinkHandled = { incomingLink.value = null },
                    themeMode = themeMode.value,
                    onThemeMode = { mode ->
                        themeMode.value = mode
                        settings.edit { putString(KEY_THEME, mode.name) }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingLink.value = intent.dataString
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
