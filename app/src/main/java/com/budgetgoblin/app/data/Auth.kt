package com.budgetgoblin.app.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Waits for a Play Services / Firebase task without blocking. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result)
        else cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
    }
}

/**
 * Passwordless sign-in: "Continue with Google" or a one-tap link sent by email.
 * Everything is a no-op when the app was built without google-services.json ([available] is false).
 */
class Auth(private val context: Context) {
    val available = FirebaseApp.getApps(context).isNotEmpty()
    private val auth get() = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var user by mutableStateOf<FirebaseUser?>(if (available) auth.currentUser else null)
        private set

    init {
        if (available) auth.addAuthStateListener { user = it.currentUser }
    }

    /** The project's free Hosting address; email links come from here and open the app (see the manifest). */
    private val linkDomain get() = "${FirebaseApp.getInstance().options.projectId}.firebaseapp.com"

    /** The email a link was last sent to, so tapping the link on this phone signs straight in. */
    val pendingEmail: String? get() = prefs.getString(KEY_PENDING_EMAIL, null)

    suspend fun signInWithGoogle(activity: Activity) {
        val clientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val option = GetSignInWithGoogleOption.Builder(context.getString(clientIdRes)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google didn't return an account"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    /**
     * A one-time ID carried in the email link. If the link is opened on another device (a computer),
     * the sign-in page asks for [handoffCode], which only this phone shows, and posts the link to
     * handoff/<id>/<code> in the Realtime Database for this phone to pick up. The code stops a link
     * requested by someone else (for your email, from their phone) from signing *them* in when you
     * open the email: you never see their code, so the page never hands your link over.
     */
    var pendingHandoff by mutableStateOf(prefs.getString(KEY_HANDOFF, null))
        private set

    /** The 6-digit code to type on the computer's sign-in page, shown on "Check your email". */
    var handoffCode by mutableStateOf(prefs.getString(KEY_HANDOFF_CODE, null))
        private set

    private val handoffUrl get() = "$databaseUrl/handoff/$pendingHandoff/$handoffCode.json"

    suspend fun sendEmailLink(email: String) {
        val handoff = UUID.randomUUID().toString().replace("-", "")
        val code = String.format(Locale.ROOT, "%06d", SecureRandom().nextInt(1_000_000))
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://$linkDomain/finishSignIn?h=$handoff")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(context.packageName, true, null)
            .build() // the link itself comes from the project's default firebaseapp.com domain
        auth.sendSignInLinkToEmail(email, settings).await()
        prefs.edit { putString(KEY_PENDING_EMAIL, email).putString(KEY_HANDOFF, handoff).putString(KEY_HANDOFF_CODE, code) }
        pendingHandoff = handoff
        handoffCode = code
    }

    fun isSignInLink(link: String) = available && auth.isSignInWithEmailLink(link)

    suspend fun finishEmailLink(email: String, link: String) {
        auth.signInWithEmailLink(email, link).await()
        if (pendingHandoff != null && handoffCode != null) runCatching { http("DELETE", handoffUrl) }
        prefs.edit { remove(KEY_PENDING_EMAIL).remove(KEY_HANDOFF).remove(KEY_HANDOFF_CODE) }
        pendingHandoff = null
        handoffCode = null
    }

    /**
     * Waits (checking every 2 seconds, for up to 15 minutes) for the link to be opened on another
     * device and returns it, or null if it never was.
     */
    suspend fun awaitHandoff(): String? {
        if (pendingHandoff == null || handoffCode == null) return null // links sent before codes existed
        repeat(450) {
            val body = runCatching { http("GET", handoffUrl) }.getOrNull()
            if (body != null && body != "null") {
                val link = runCatching { JSONObject(body).optString("link") }.getOrNull()
                if (!link.isNullOrEmpty() && isSignInLink(link)) return link
            }
            delay(2000)
        }
        runCatching { http("DELETE", handoffUrl) } // nothing arrived; leave no link behind
        return null
    }

    private suspend fun http(method: String, url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** The Realtime Database (default US instance) that carries sign-in links between devices. */
    private val databaseUrl get() = "https://${FirebaseApp.getInstance().options.projectId}-default-rtdb.firebaseio.com"

    suspend fun signOut() {
        auth.signOut()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    /** Whether the account was signed into within the last few minutes, which Firebase needs before deleting it. */
    val signedInRecently: Boolean
        get() = (auth.currentUser?.metadata?.lastSignInTimestamp ?: 0L) > System.currentTimeMillis() - RECENT_SIGN_IN

    /** Deletes the sign-in account itself; Firebase may ask for a fresh sign-in first. */
    suspend fun deleteUser() {
        auth.currentUser?.delete()?.await()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    private companion object {
        const val KEY_PENDING_EMAIL = "pending_email"
        const val KEY_HANDOFF = "pending_handoff"
        const val KEY_HANDOFF_CODE = "pending_handoff_code"
        const val RECENT_SIGN_IN = 4L * 60 * 1000 // Firebase's limit is about 5 minutes
    }
}
