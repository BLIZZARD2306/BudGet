package com.budgetgoblin.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (emulators can't pass Play Integrity) use a debug token. On first run it's
 * printed to Logcat as "Enter this debug secret into the allow list..."; add it in
 * Firebase console > App Check > Apps > Manage debug tokens. Never ships in release builds.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
