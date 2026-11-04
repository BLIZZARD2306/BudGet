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
import androidx.compose.material3.CircularProgressIndicator
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
import com.budgetgoblin.app.ui.theme.CoinGold
import com.budgetgoblin.app.ui.theme.ThemeMode
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sign in (Google or an emailed link, no passwords) or, when signed in, manage the account. */
@Composable
fun AccountScreen(
    auth: Auth,
    onMessage: (Tip) -> Unit,
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
            SignedOut(auth, onMessage, onClose)
        } else {
            BackTitle("Your account", onBack = onClose)
            SignedIn(auth, syncStatus, onSignOut, onDeleteAccount)
        }
        PaySchedulePanel(schedule, onSchedule)
        AppearancePanel(themeMode, onThemeMode)
    }
}

private enum class EmailStep { CHOOSE, ENTER, SENT }

private const val RESEND_SECONDS = 60

/**
 * Signed-out flow, one screen per step like the kit: choose a method, enter an email,
 * then a "check your email" screen with resend.
 */
@Composable
private fun ColumnScope.SignedOut(auth: Auth, onMessage: (Tip) -> Unit, onClose: () -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(EmailStep.CHOOSE) }
    var email by rememberSaveable { mutableStateOf(auth.pendingEmail ?: "") }
    var sentAt by rememberSaveable { mutableLongStateOf(0L) }
    var busy by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step != EmailStep.CHOOSE) {
        step = if (step == EmailStep.SENT) EmailStep.ENTER else EmailStep.CHOOSE
    }

    fun send() {
        busy = true
        scope.launch {
            try {
                auth.sendEmailLink(email.trim())
                sentAt = System.currentTimeMillis()
                step = EmailStep.SENT
            } catch (e: Exception) {
                onMessage(Tip(Mood.SAD, sendError(e)))
            }
            busy = false
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
                    scope.launch {
                        try {
                            auth.signInWithGoogle(activity)
                        } catch (_: GetCredentialCancellationException) {
                            // The user closed the account picker.
                        } catch (_: NoCredentialException) {
                            onMessage(Tip(Mood.HELP, "No Google account on this phone. Add one in Settings, or continue with email."))
                        } catch (e: Exception) {
                            onMessage(Tip(Mood.SAD, "Couldn't sign in: ${e.message}"))
                        }
                        busy = false
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
            LinkSent(
                email = email.trim(),
                sentAt = sentAt,
                busy = busy,
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

private fun sendError(e: Exception) = when (e) {
    is FirebaseTooManyRequestsException -> "Too many emails sent for now. Try again later, or continue with Google."
    is FirebaseAuthInvalidCredentialsException -> "That email address doesn't look right."
    else -> "Couldn't send the link: ${e.message}"
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
        if (busy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
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
                "We'll email you a sign-in link. Tap it on this phone and you're in: no password, nothing to type.",
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
        PillButton("Send sign-in link", enabled = valid && !busy, modifier = Modifier.fillMaxWidth(), onClick = onSend)
        if (busy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun LinkSent(
    email: String,
    sentAt: Long,
    busy: Boolean,
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

    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.CURIOUS, size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text("Link sent!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "We sent a sign-in link to",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(email, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Step("1", "Open the email here or on your computer")
        Step("2", "Tap the Sign in link. This screen signs in by itself")
        Step("3", "Not there? Check Spam or Promotions. It can take a few minutes.")
        Spacer(Modifier.height(20.dp))
        PillButton("Open email app", modifier = Modifier.fillMaxWidth(), onClick = onOpenEmail)
        Spacer(Modifier.height(12.dp))
        OutlineButton(
            if (wait > 0) "Resend link in 0:%02d".format(wait) else "Resend link",
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
                    onClick = { onChange(schedule.copy(cycle = c)) },
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

/**
 * Sign out and delete are run by the app (onSignOut / onDeleteAccount), not here: this screen
 * leaves the composition the moment the user is signed out, which would cancel its own coroutines.
 */
@Composable
private fun SignedIn(auth: Auth, syncStatus: SyncStatus, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val user = auth.user ?: return
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
            PillButton("Sign out", modifier = Modifier.fillMaxWidth(), onClick = onSignOut)
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
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
