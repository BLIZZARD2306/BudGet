package com.budgetgoblin.app.ui

import java.util.Locale
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat
import com.budgetgoblin.app.data.PaySchedule
import com.budgetgoblin.app.data.PayCycle
import com.budgetgoblin.app.data.SyncStatus
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.Auth
import com.budgetgoblin.app.data.CodeException
import com.budgetgoblin.app.ui.theme.CoinGold
import com.budgetgoblin.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sign in (Google or an emailed link, no passwords) or, when signed in, manage the account. */
@Composable
fun AccountScreen(
    auth: Auth,
    onMessage: (Tip) -> Unit,
    onWork: (Work, Boolean) -> Unit,
    onClose: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    syncStatus: SyncStatus,
    schedule: PaySchedule,
    onSchedule: (PaySchedule) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val user = auth.user
    ScreenColumn(rememberScrollState()) {
        if (user == null) {
            SignedOut(auth, onMessage, onWork, onClose)
        } else {
            BackTitle("Your account", onBack = onClose)
            SignedIn(auth, syncStatus, onSignOut, onDeleteAccount)
        }
        PaySchedulePanel(schedule, onSchedule)
        AppearancePanel(themeMode, onThemeMode)
        CurrencyPanel()
    }
}

private enum class EmailStep { CHOOSE, ENTER, SENT }

private const val RESEND_SECONDS = 60
private const val CODE_LENGTH = 6

/**
 * Signed-out flow, one screen per step like the kit: choose a method, enter an email,
 * then a screen to type the emailed code, with resend.
 */
@Composable
private fun ColumnScope.SignedOut(auth: Auth, onMessage: (Tip) -> Unit, onWork: (Work, Boolean) -> Unit, onClose: () -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(EmailStep.CHOOSE) }
    var email by rememberSaveable { mutableStateOf(auth.pendingEmail ?: "") }
    var sentAt by rememberSaveable { mutableLongStateOf(0L) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = step != EmailStep.CHOOSE) {
        step = if (step == EmailStep.SENT) EmailStep.ENTER else EmailStep.CHOOSE
    }

    fun send() {
        busy = true
        error = null
        val w = Work(Mood.ADDING, "Sending your code…")
        onWork(w, true)
        scope.launch {
            try {
                auth.sendCode(email.trim())
                sentAt = System.currentTimeMillis()
                code = ""
                step = EmailStep.SENT
            } catch (e: Exception) {
                onMessage(Tip(Mood.SAD, sendError(e)))
            } finally {
                busy = false
                onWork(w, false)
            }
        }
    }

    fun verify() {
        busy = true
        error = null
        val w = Work(Mood.GUARDING, "Letting you in…")
        onWork(w, true)
        scope.launch {
            try {
                auth.verifyCode(email.trim(), code) // once signed in, this screen gives way to the account
            } catch (e: Exception) {
                error = verifyError(e)
                code = ""
            } finally {
                busy = false
                onWork(w, false)
            }
        }
    }

    when (step) {
        EmailStep.CHOOSE -> {
            BackTitle("Sign in", onBack = onClose)
            ChooseMethod(
                available = auth.available,
                busy = busy,
                onGoogle = {
                    busy = true
                    val w = Work(Mood.GUARDING, "Waiting for Google…")
                    onWork(w, true)
                    scope.launch {
                        try {
                            auth.signInWithGoogle(activity)
                        } catch (_: GetCredentialCancellationException) {
                            // The user closed the account picker.
                        } catch (_: NoCredentialException) {
                            onMessage(Tip(Mood.HELP, "No Google account on this phone. Add one in Settings, or continue with email."))
                        } catch (e: Exception) {
                            onMessage(Tip(Mood.SAD, "Couldn't sign in: ${e.message}"))
                        } finally {
                            busy = false
                            onWork(w, false)
                        }
                    }
                },
                onEmail = { step = EmailStep.ENTER },
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)) {
                Text("Not now")
            }
        }
        EmailStep.ENTER -> {
            BackTitle("Sign in with email") { step = EmailStep.CHOOSE }
            EnterEmail(
                email = email,
                onEmailChange = { email = it },
                busy = busy,
                onSend = ::send,
            )
        }
        EmailStep.SENT -> {
            BackTitle("Check your email") { step = EmailStep.ENTER }
            CodeSent(
                email = email.trim(),
                code = code,
                onCodeChange = {
                    code = it
                    error = null
                    if (it.length == CODE_LENGTH && !busy) verify() // six digits typed: go straight in
                },
                error = error,
                sentAt = sentAt,
                busy = busy,
                onVerify = ::verify,
                onResend = ::send,
                onOpenEmail = {
                    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        activity.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        onMessage(Tip(Mood.HELP, "No email app found. Open your email in a browser on this phone instead."))
                    }
                },
                onChangeEmail = { step = EmailStep.ENTER },
            )
        }
    }
}

private fun sendError(e: Exception) = when ((e as? CodeException)?.reason) {
    "email" -> "That email address doesn't look right."
    "too_soon" -> "Give it ${e.waitText()} before asking for another code."
    "too_many" -> "Too many codes for now. Try again in ${e.waitText()}, or continue with Google."
    "mail" -> "Couldn't send the email just now. Try again in a minute, or continue with Google."
    null -> "Couldn't send the code. Check your internet and try again."
    else -> "Couldn't send the code. Try again in a minute."
}

private fun verifyError(e: Exception): String {
    val c = e as? CodeException ?: return "Couldn't check the code. Check your internet and try again."
    return when (c.reason) {
        "wrong" -> when (val left = c.attemptsLeft) {
            null -> "That code isn't right. Check the newest email from BudGet."
            1 -> "That code isn't right. 1 try left."
            else -> "That code isn't right. $left tries left."
        }
        "expired" -> "That code has expired. Send a new one."
        "locked" -> "Too many wrong tries. Send a new code."
        "disabled" -> "This account has been switched off."
        else -> "Couldn't check the code. Try again."
    }
}

private fun Exception.waitText(): String {
    val s = (this as? CodeException)?.retryAfter ?: return "a little while"
    return if (s < 60) "$s seconds" else "${(s + 59) / 60} minutes"
}

/** The kit's sub-screen title: a back arrow on the left, title centred. */
@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            PixelIcon(R.drawable.px_back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun ChooseMethod(available: Boolean, busy: Boolean, onGoogle: () -> Unit, onEmail: () -> Unit) {
    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.GUARDING, size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sync across your phones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Sign in to back up your budget and pick up where you left off on any phone. No password needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (!available) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Mood.ALERT, size = 48.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sign-in isn't switched on in this build yet. It needs google-services.json from Firebase.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlineButton("Continue with Google", leading = "G", enabled = available && !busy, onClick = onGoogle)
        Spacer(Modifier.height(12.dp))
        OutlineButton("Continue with email", icon = R.drawable.px_mail, enabled = available && !busy, onClick = onEmail)
    }
}

@Composable
private fun EnterEmail(email: String, onEmailChange: (String) -> Unit, busy: Boolean, onSend: () -> Unit) {
    val valid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.ADDING, size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text("Enter your email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "We'll email you a 6-digit code. Type it here and you're in: no password needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email address") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (valid && !busy) onSend() }),
            trailingIcon = if (valid) ({ Text("✓", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold) }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        PillButton("Send code", enabled = valid && !busy, modifier = Modifier.fillMaxWidth(), onClick = onSend)
    }
}

@Composable
private fun CodeSent(
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    error: String?,
    sentAt: Long,
    busy: Boolean,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onOpenEmail: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sentAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val wait = (RESEND_SECONDS - (now - sentAt) / 1000).coerceAtLeast(0)
    val complete = code.length == CODE_LENGTH

    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.CURIOUS, size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text("Code sent!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "We emailed a 6-digit code to",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(email, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        // Our emails often land in Spam, so say so up front instead of as a last resort.
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, PanelShape)
                .padding(12.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                "Check your Spam folder",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "Our email often lands in Spam or Promotions. If you find it there, tap \"Not spam\" so the next one reaches your inbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { typed -> onCodeChange(typed.filter(Char::isDigit).take(CODE_LENGTH)) },
            label = { Text("6-digit code") },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            supportingText = { Text(error ?: "It works for 10 minutes. Never share it with anyone.") },
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            ),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (complete && !busy) onVerify() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PillButton("Sign in", enabled = complete && !busy, modifier = Modifier.fillMaxWidth(), onClick = onVerify)
        Spacer(Modifier.height(12.dp))
        OutlineButton("Open email app", icon = R.drawable.px_mail, enabled = true, onClick = onOpenEmail)
        Spacer(Modifier.height(12.dp))
        OutlineButton(
            if (wait > 0) "Send a new code in 0:%02d".format(wait) else "Send a new code",
            enabled = wait == 0L && !busy,
            onClick = onResend,
        )
        TextButton(onClick = onChangeEmail, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Use a different email")
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(number, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OutlineButton(text: String, leading: String? = null, icon: Int? = null, enabled: Boolean, onClick: () -> Unit) {
    OutlinePillButton(
        text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        leading = when {
            icon != null -> { { PixelIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 22.dp) } }
            leading != null -> { { Text(leading, color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 20.sp) } }
            else -> null
        },
    )
}

/**
 * Pay schedule: how often you get paid. The budget amount is per period, and Remaining resets on
 * each payday. Weekly and every-2-weeks also pick the payday; every-2-weeks picks which week too.
 */
@Composable
private fun PaySchedulePanel(schedule: PaySchedule, onChange: (PaySchedule) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SectionTitle("Pay schedule")
    Text(
        "How often do you get paid? Your budget is per pay period, and Remaining resets on each payday.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    PayCycle.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            row.forEach { c ->
                Tile(
                    selected = c == schedule.cycle,
                    onClick = {
                        // Every 2 weeks starts this week unless a payday week was already picked.
                        val anchor = if (c == PayCycle.BIWEEKLY && schedule.anchor == 0L) {
                            PaySchedule.lastWeekday(System.currentTimeMillis(), schedule.weekStart)
                        } else schedule.anchor
                        onChange(schedule.copy(cycle = c, anchor = anchor))
                    },
                    modifier = Modifier.weight(1f).height(76.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.label.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.6.sp)
                        Text(c.detail, fontSize = 11.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    if (schedule.cycle == PayCycle.WEEKLY || schedule.cycle == PayCycle.BIWEEKLY) {
        Text("Payday", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        val days = listOf(
            Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W", Calendar.THURSDAY to "T",
            Calendar.FRIDAY to "F", Calendar.SATURDAY to "S", Calendar.SUNDAY to "S",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { (d, letter) ->
                Tile(
                    selected = schedule.weekStart == d,
                    onClick = {
                        val last = PaySchedule.lastWeekday(System.currentTimeMillis(), d)
                        onChange(schedule.copy(weekStart = d, anchor = last))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(letter, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
    if (schedule.cycle == PayCycle.BIWEEKLY) {
        // Which week the last payday was: this one, or the one before.
        val thisWeek = PaySchedule.lastWeekday(System.currentTimeMillis(), schedule.weekStart)
        val lastWeek = thisWeek - 7L * 24 * 60 * 60 * 1000
        val anchoredThisWeek = schedule.periodAt().start == thisWeek
        Text("Your last payday was", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(thisWeek to anchoredThisWeek, lastWeek to !anchoredThisWeek).forEach { (date, selected) ->
                Tile(
                    selected = selected,
                    onClick = { onChange(schedule.copy(anchor = date)) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(date)), fontWeight = FontWeight.ExtraBold) }
            }
        }
    }

    val p = schedule.periodAt()
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    Spacer(Modifier.height(10.dp))
    Text(
        "This period: ${fmt.format(Date(p.start))} – ${fmt.format(Date(p.end - 1))} · ${schedule.resetLabel()}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = scheme.primary,
    )
}

/** Appearance: follow the phone, or always light / always dark. */
@Composable
private fun AppearancePanel(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    SectionTitle("Appearance")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { m ->
            Tile(
                selected = m == mode,
                onClick = { onMode(m) },
                modifier = Modifier.weight(1f).height(84.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelIcon(
                        when (m) {
                            ThemeMode.SYSTEM -> R.drawable.px_phone
                            ThemeMode.LIGHT -> R.drawable.px_sun
                            ThemeMode.DARK -> R.drawable.px_moon
                        },
                        contentDescription = null,
                        tint = if (m == ThemeMode.LIGHT) CoinGold else if (m == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(m.label.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.6.sp)
                }
            }
        }
    }
}

@Composable
private fun CurrencyPanel() {
    val context = LocalContext.current
    SectionTitle("Currency")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppCurrency.entries.forEach { c ->
            Tile(
                selected = c == appCurrency,
                onClick = { AppCurrency.save(context, c) },
                modifier = Modifier.weight(1f).height(84.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        c.symbol,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = if (c == appCurrency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(c.label.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.6.sp)
                }
            }
        }
    }
}

/**
 * Sign out and delete are run by the app (onSignOut / onDeleteAccount), not here: this screen
 * leaves the composition the moment the user is signed out, which would cancel its own coroutines.
 */
@Composable
private fun SignedIn(auth: Auth, syncStatus: SyncStatus, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val user = auth.user ?: return
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.GUARDING, size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                user.displayName ?: user.email ?: "Signed in",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (user.displayName != null && user.email != null) {
                Text(user.email!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            val (headline, detail, ok) = when (syncStatus) {
                SyncStatus.SYNCED -> Triple("✓  Synced across your phones", "Changes you make offline upload as soon as you're back online.", true)
                SyncStatus.CONNECTING -> Triple("Connecting to your account…", "Your entries are saved on this phone in the meantime.", false)
                SyncStatus.OFFLINE -> Triple("Offline right now", "Your entries are saved on this phone and upload when you're back online.", false)
                SyncStatus.BLOCKED -> Triple("This phone can't sync yet", "Your account refused this phone. If it's a test build, add its App Check debug token in Firebase.", false)
            }
            Text(
                headline,
                color = if (ok) MaterialTheme.colorScheme.tertiary else CoinGold,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PillButton("Sign out", modifier = Modifier.fillMaxWidth(), onClick = { confirmSignOut = true })
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Mascot(Mood.ANXIOUS, size = 80.dp, tappable = false) },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (syncStatus == SyncStatus.SYNCED) {
                        "Your budget stays safe in your account. This phone will be cleared until you sign back in."
                    } else {
                        "This phone hasn't finished syncing. Changes that haven't uploaded yet will be lost if you sign out now."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay signed in") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Mascot(Mood.SHOCKED, size = 80.dp) },
            title = { Text("Delete your account?") },
            text = { Text("This permanently deletes your account and every transaction, budget and challenge saved in it, on all your phones.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccount()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
