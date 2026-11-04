package com.budgetgoblin.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer build published on the Vercel download page (see release.ps1). */
data class AppUpdate(val versionCode: Long, val versionName: String, val notes: String, val apkUrl: String)

/**
 * Updates outside the Play Store: release.ps1 uploads the APK and a small latest.json to the Vercel page (website/);
 * the app compares that version with its own, downloads the APK and hands it to Android's installer.
 */
class Updater(private val context: Context) {
    private val installed: Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** The published update if it's newer than this install; null if not, or if the check fails. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(open(LATEST_URL).inputStream.bufferedReader().use { it.readText() })
            AppUpdate(
                versionCode = json.getLong("versionCode"),
                versionName = json.optString("versionName"),
                notes = json.optString("notes"),
                apkUrl = URL(URL(LATEST_URL), json.optString("apk", "budget.apk")).toString(),
            ).takeIf { it.versionCode > installed }
        }.getOrNull()
    }

    /** Downloads the APK into the cache, reporting progress 0..1. Returns the file, or null on failure. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            val file = File(dir, "budget-${update.versionCode}.apk")
            val connection = open(update.apkUrl)
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            file
        }.getOrNull()
    }

    /** Android 8+ asks once per app before it may install others; true when that's still needed. */
    fun needsInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Opens Android's install screen for the downloaded APK. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            if (responseCode != HttpURLConnection.HTTP_OK) error("HTTP $responseCode for $url")
        }

    private companion object {
        const val LATEST_URL = "https://budget-goblin.vercel.app/app/latest.json"
    }
}
