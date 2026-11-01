package com.budgetgoblin.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Turns on Firebase App Check before anything talks to Firebase, so Auth and Firestore only
 * accept requests from this genuine app (the API key alone isn't enough). The provider comes
 * from the build type: Play Integrity in release, an allow-listed debug token in debug.
 */
class BudGetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
        }
    }
}
