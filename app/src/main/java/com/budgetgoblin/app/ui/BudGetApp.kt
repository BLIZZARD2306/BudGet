package com.budgetgoblin.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.Auth
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.CloudSync
import com.budgetgoblin.app.ui.theme.ThemeMode
import com.budgetgoblin.app.ui.theme.lip
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.launch

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    HOME("Home", R.drawable.ic_nav_home),
    CATEGORIES("Categories", R.drawable.ic_nav_categories),
    ADD("Add", R.drawable.ic_add),
    STATS("Stats", R.drawable.ic_nav_stats),
    CHALLENGES("Challenges", R.drawable.ic_nav_challenges),
}

@Composable
fun BudGetApp(
    state: BudgetState,
    auth: Auth,
    incomingLink: String?,
    onLinkHandled: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var reaction by remember { mutableStateOf<Tip?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var uploadOffer by remember { mutableStateOf<String?>(null) }
    var confirmLink by remember { mutableStateOf<String?>(null) }
    val sync = remember { CloudSync(state) }
    DisposableEffect(sync) { onDispose { sync.stop() } }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = showAccount) { showAccount = false }
    BackHandler(enabled = !showAccount && tab != Tab.HOME) { tab = Tab.HOME }

    fun startSync(uid: String) {
        state.syncedUid = uid
        sync.start(uid)
    }

    // Sign-out clears the phone first (so the next sign-in starts clean), then signs out.
    // Both run in the app's scope so leaving the account screen can't cancel them halfway.
    fun signOut() {
        sync.stop()
        state.clearLocal()
        scope.launch { auth.signOut() }
        reaction = Tip(Mood.HAPPY, "Signed out. Your budget is safe in your account.")
    }

    fun deleteAccount() {
        val uid = auth.user?.uid ?: return
        scope.launch {
            try {
                sync.deleteAll(uid)
                sync.stop()
                auth.deleteUser()
                state.clearLocal()
                reaction = Tip(Mood.SAD, "Account deleted. Bye for now!")
            } catch (_: FirebaseAuthRecentLoginRequiredException) {
                reaction = Tip(Mood.ALERT, "For safety, sign out and sign back in, then try deleting again.")
            } catch (e: Exception) {
                reaction = Tip(Mood.SAD, "Couldn't delete the account: ${e.message}")
            }
        }
    }

    /** Uploads this phone's entries; on failure keeps them local, says so, and returns false. */
    suspend fun upload(uid: String): Boolean = try {
        sync.upload(uid)
        true
    } catch (_: Exception) {
        reaction = Tip(Mood.ALERT, "Couldn't upload your entries yet. They're safe on this phone, and I'll try again next time.")
        false
    }

    // Signing in turns sync on. The first time this phone meets an account, its own entries are
    // uploaded (or, if the account already has data, the user chooses whether to add them).
    // Nothing on the phone is replaced until the cloud has actually been reached.
    LaunchedEffect(auth.user?.uid) {
        val uid = auth.user?.uid ?: return@LaunchedEffect sync.stop()
        if (state.syncedUid == uid) return@LaunchedEffect sync.start(uid)
        val accountHasData = try {
            sync.hasData(uid)
        } catch (_: Exception) {
            reaction = Tip(Mood.ALERT, "Couldn't reach your account yet. Your entries are safe on this phone, and I'll try again next time.")
            return@LaunchedEffect
        }
        val hasLocal = state.hasOwnData && state.transactions.isNotEmpty()
        if (hasLocal && accountHasData) {
            uploadOffer = uid
            return@LaunchedEffect
        }
        if (hasLocal && !upload(uid)) return@LaunchedEffect
        reaction = Tip(Mood.CELEBRATE, "Signed in! Your budget now syncs across your phones.")
        startSync(uid)
    }

    // A tapped email link opens the app; finish signing in with the address it was sent to.
    LaunchedEffect(incomingLink) {
        val link = signInLink(auth, incomingLink ?: return@LaunchedEffect)
        onLinkHandled()
        if (link == null) return@LaunchedEffect
        val email = auth.pendingEmail
        if (email == null) {
            confirmLink = link
        } else {
            runCatching { auth.finishEmailLink(email, link) }
                .onFailure { reaction = Tip(Mood.SAD, "That sign-in link didn't work. It may have expired, so send a new one.") }
        }
        showAccount = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(if (showAccount) null else tab, label = "tab") { current ->
            when (current) {
                null -> AccountScreen(
                    auth,
                    onMessage = { reaction = it },
                    onClose = { showAccount = false },
                    onSignOut = ::signOut,
                    onDeleteAccount = ::deleteAccount,
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                )
                Tab.HOME -> HomeScreen(state, signedIn = auth.user != null, onHelp = { showHelp = true }, onAccount = { showAccount = true })
                Tab.CATEGORIES -> CategoriesScreen(state)
                Tab.ADD -> AddScreen(
                    onCancel = { tab = Tab.HOME },
                    onSave = { t ->
                        val before = state.thisMonth
                        val broken = state.add(t)
                        reaction = broken.firstOrNull()
                            ?.let { Tip(Mood.REGRETFUL, "Oops! That broke your ${it.type.title} challenge.") }
                            ?: reactionTo(t, before, state.monthlyBudget)
                        tab = Tab.HOME
                    },
                )
                Tab.STATS -> StatsScreen(state)
                Tab.CHALLENGES -> ChallengesScreen(state, onReaction = { reaction = it })
            }
        }
        // Keeps scrolled content from running under the status bar icons.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background)
        )
        // Adding a transaction is a full-screen step, so the nav bar steps aside.
        if (!showAccount && tab != Tab.ADD) {
            BottomBar(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        ReactionBanner(
            reaction = reaction,
            onDone = { reaction = null },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showHelp) HelpDialog(onDismiss = { showHelp = false })

    uploadOffer?.let { uid ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Mascot(Mood.GUARDING, size = 80.dp) },
            title = { Text("Add this phone's entries?") },
            text = {
                Text(
                    "This phone has ${state.transactions.size} transactions that aren't in your account yet. " +
                        "Add them, or skip to use only what's already in your account."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    uploadOffer = null
                    scope.launch {
                        if (upload(uid)) startSync(uid)
                    }
                }) { Text("Add them") }
            },
            dismissButton = {
                TextButton(onClick = {
                    uploadOffer = null
                    startSync(uid)
                }) { Text("Skip") }
            },
        )
    }

    confirmLink?.let { link ->
        ConfirmEmailDialog(
            onDismiss = { confirmLink = null },
            onConfirm = { email ->
                confirmLink = null
                scope.launch {
                    runCatching { auth.finishEmailLink(email, link) }
                        .onFailure { reaction = Tip(Mood.SAD, "That didn't match. Use the email the link was sent to.") }
                }
            },
        )
    }
}

/** The sign-in link inside [uri], which may arrive wrapped as the "link" parameter of a Hosting URL. */
private fun signInLink(auth: Auth, uri: String): String? =
    listOfNotNull(uri, Uri.parse(uri).getQueryParameter("link")).firstOrNull(auth::isSignInLink)

/** Asked when a link is opened on a different phone from the one that requested it. */
@Composable
private fun ConfirmEmailDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Mascot(Mood.CURIOUS, size = 80.dp) },
        title = { Text("Confirm your email") },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("The email the link was sent to") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        },
        confirmButton = { TextButton(enabled = email.isNotBlank(), onClick = { onConfirm(email.trim()) }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Duolingo-style nav bar: flat, a 2dp line on top, icons only. The current tab sits in an
 * outlined rounded box; Add is always a small coral chunky button.
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(scheme.background)) {
        HorizontalDivider(thickness = 2.dp, color = scheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { tab ->
                if (tab == Tab.ADD) AddButton(onClick = { onSelect(Tab.ADD) }) else NavItem(tab, current, onSelect)
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val selected = tab == current
    Box(
        Modifier
            .size(52.dp)
            .then(
                if (selected) {
                    Modifier
                        .border(2.dp, scheme.primary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .background(scheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                } else Modifier
            )
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
            .semantics { contentDescription = tab.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(tab.icon),
            contentDescription = null,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** The coral + for adding a transaction: a small chunky button in the middle of the bar. */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val coral = MaterialTheme.colorScheme.primary
    Chunky(
        face = coral,
        edge = coral.lip(),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        modifier = Modifier.size(52.dp).semantics { contentDescription = "Add transaction" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
