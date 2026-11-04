package com.budgetgoblin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.budgetgoblin.app.data.AppUpdate
import com.budgetgoblin.app.data.Updater
import kotlinx.coroutines.launch
import java.io.File

/** "A new version is out": downloads the APK and opens Android's installer. */
@Composable
fun UpdateDialog(update: AppUpdate, updater: Updater, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(-1f) } // -1 = not downloading
    var apk by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    var askedPermission by remember { mutableStateOf(false) }
    val downloading = progress >= 0f && apk == null

    fun go() {
        if (updater.needsInstallPermission()) {
            askedPermission = true
            updater.openInstallPermissionSettings()
            return
        }
        apk?.let { return updater.install(it) }
        failed = false
        progress = 0f
        scope.launch {
            val file = updater.download(update) { progress = it }
            if (file == null) {
                failed = true
                progress = -1f
            } else {
                apk = file
                updater.install(file)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = { Mascot(Mood.LEVEL_UP, size = 88.dp, tappable = false) },
        title = { Text("Update available", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Version ${update.versionName} is out. Your data stays as it is.")
                if (update.notes.isNotBlank()) {
                    Text(update.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    downloading -> ChunkyProgress(progress, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.fillMaxWidth())
                    failed -> Text("Download failed. Check your connection and try again.", color = MaterialTheme.colorScheme.primary)
                    askedPermission && updater.needsInstallPermission() ->
                        Text("Turn on \"Allow from this source\" for BudGet, come back, then tap Update.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::go, enabled = !downloading) {
                Text(
                    when {
                        downloading -> "Downloading…"
                        apk != null -> "Install"
                        failed -> "Try again"
                        else -> "Update"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !downloading) { Text("Later") } },
    )
}
