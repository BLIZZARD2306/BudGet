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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The sign-in server said no. [reason] is its error code: "email", "wrong", "expired", "locked",
 * "too_soon", "too_many", "mail" or "disabled".
 */
class CodeException(val reason: String, val attemptsLeft: Int? = null, val retryAfter: Int? = null) :
    Exception("Sign-in server: $reason")

/** Waits for a Play Services / Firebase task without blocking. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result)
        else cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
    }
}

/**
 * Passwordless sign-in: "Continue with Google" or a 6-digit code sent by email.
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
        prefs.edit { remove("pending_handoff").remove("pending_handoff_code") } // left over from link sign-in
    }

    /** The email a code (or, in older versions, a link) was last sent to. */
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

    /** Remembers the email a code was sent to, so the code screen survives the app being closed. */
    private fun rememberEmail(email: String?) = prefs.edit {
        if (email == null) remove(KEY_PENDING_EMAIL) else putString(KEY_PENDING_EMAIL, email)
    }

    /**
     * Emails a 6-digit sign-in code. The code is made, rate-limited and checked by BudGet's own
     * server on Vercel (website repository, api/), because a check inside the app could be skipped.
     */
    suspend fun sendCode(email: String) {
        post("send-code", JSONObject().put("email", email))
        rememberEmail(email)
    }

    /** Trades the emailed code for a Firebase sign-in token and signs in with it. */
    suspend fun verifyCode(email: String, code: String) {
        val token = post("verify-code", JSONObject().put("email", email).put("code", code)).getString("token")
        auth.signInWithCustomToken(token).await()
        rememberEmail(null)
    }

    fun isSignInLink(link: String) = available && auth.isSignInWithEmailLink(link)

    /** Finishes a sign-in link sent by an older version of the app (before codes); new versions never send links. */
    suspend fun finishEmailLink(email: String, link: String) {
        auth.signInWithEmailLink(email, link).await()
        rememberEmail(null)
    }

    /** POSTs JSON to the sign-in API; throws [CodeException] with the server's reason when it says no. */
    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("$API/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (status !in 200..299) {
                throw CodeException(
                    reason = json.optString("error", "http_$status"),
                    attemptsLeft = json.optInt("attemptsLeft", -1).takeIf { it >= 0 },
                    retryAfter = json.optInt("retryAfter", -1).takeIf { it >= 0 },
                )
            }
            json
        } finally {
            conn.disconnect()
        }
    }

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
        const val API = "https://budget-goblin.vercel.app/api"
        const val RECENT_SIGN_IN = 4L * 60 * 1000 // Firebase's limit is about 5 minutes
    }
}
