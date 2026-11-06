import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Firebase needs app/google-services.json from the Firebase console; until it's there the app
// still builds and runs, just without sign-in and sync.
val googleServices = file("google-services.json")
if (googleServices.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Email sign-in links come from <project-id>.firebaseapp.com; the manifest needs that host.
val firebaseProjectId = googleServices.takeIf { it.exists() }
    ?.readText()
    ?.let { Regex("\"project_id\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

android {
    namespace = "com.budgetgoblin.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.budgetgoblin.app"
        minSdk = 24
        targetSdk = 37
        // versionCode goes up by 1 every release. versionName is BIG.MAJOR.MINOR:
        // BIG for very big updates with lots of changes, MAJOR for smaller feature updates,
        // MINOR for fixes and small tweaks.
        versionCode = 3
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["firebaseLinkHost"] = firebaseProjectId?.let { "$it.firebaseapp.com" } ?: "invalid.invalid"
    }

    // Release builds are signed with BudGet's own key, kept out of git: keystore.properties in the
    // project folder (storeFile, storePassword, keyAlias, keyPassword). Without it, release builds
    // come out unsigned and can't be installed. Back the key up: losing it means no more updates.
    val keystore = rootProject.file("keystore.properties")
    if (keystore.exists()) {
        val props = Properties().apply { keystore.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}