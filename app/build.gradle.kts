import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase is applied only when the config file is present.
//
// The google-services plugin fails the build outright without google-services.json, and that file
// cannot live in the repository — it is per-project and names the app. Gating on it means the
// project builds and runs for anyone who clones it, and Crashlytics switches on the moment the file
// is dropped in, with no code change.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Release signing is driven by keystore.properties, which is gitignored.
// CI writes it from repository secrets; see .github/workflows/release.yml.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    // namespace is the code package (the R class); applicationId is the Play identity. They are
    // allowed to differ, and here they do: the identity is owned, the package name is not worth a
    // 37-file rename that cannot be compile-checked in this environment.
    namespace = "com.pushuprpg.app"
    // API 37 (Android 17) is the first level published with a minor suffix: the SDK package is
    // platforms;android-37.0, not platforms;android-37. compileSdkMinor is what pins resolution to
    // it — without it AGP looks for a package name that is never published, which is why an
    // earlier attempt at compileSdk = 37 failed at sdkmanager with "Failed to find package".
    //
    // 37 is not optional: AndroidX (Compose 1.12, navigation 2.10.1) refuses to be consumed by a
    // module compiled against anything older. targetSdk stays at 36 — compileSdk only decides which
    // APIs are visible, not which runtime behaviours the app opts in to.
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "io.github.cjsdudwls1.pushuprpg"
        minSdk = 26
        // Google Play requires new apps and updates to target API 36 from 2026-08-31.
        targetSdk = 36
        // Every bundle uploaded to Play needs a higher code than the last. The release workflow
        // passes one derived from its run number; a local build stays at 1.
        versionCode = (findProperty("pushup.versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "0.1.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Korean-only for v1: the default resources are Korean, so shipping an "en"
        // configuration without an English translation would just bloat the bundle.
        resourceConfigurations += listOf("ko")
    }

    signingConfigs {
        // A fixed debug key, checked in.
        //
        // Without this AGP falls back to ~/.android/debug.keystore and generates one if it is
        // missing — which it always is on an ephemeral CI runner. Every build then carried a
        // different signature, so the debug APK people download could never install over the one
        // they already had: Android rejects an update signed by a different key.
        //
        // These are Android's own documented debug credentials. They are not a secret, they are
        // not the release key, and Play will not accept anything signed with them.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false

            // A universal debug APK is 78MB, and about three quarters of that is MediaPipe's
            // native code for CPU architectures no phone in the test group has. Testers download
            // this over mobile data, so the debug channel ships arm64 only — every Android phone
            // sold in the last decade. Nothing here touches the release bundle, which still
            // carries every ABI and lets Play send each device only its own slice.
            //
            // Running on an x86 emulator: -Ppushup.debugAbis=arm64-v8a,x86_64
            val debugAbis = (findProperty("pushup.debugAbis") as String? ?: "arm64-v8a")
                .split(",").map(String::trim).filter(String::isNotEmpty)
            ndk { abiFilters += debugAbis }

            // Which build is actually on the phone. CI passes the short commit, so Android's app
            // info screen names it — a debug channel that silently fails to update is otherwise
            // indistinguishable from a fix that did not work, which has already cost a round of
            // testing here.
            (findProperty("pushup.buildId") as String?)
                ?.takeIf { it.isNotBlank() }
                ?.let { versionNameSuffix = "-$it" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // The pose model is already compressed; re-compressing it slows first-run load.
        noCompress += "task"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 compiles Kotlin itself — applying org.jetbrains.kotlin.android on top of it is a hard
    // error, not a warning. Kotlin's jvmTarget defaults to targetCompatibility above, so there is
    // no kotlin { compilerOptions { } } block here; :core still uses the Kotlin JVM plugin and
    // keeps its own.


    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.billing.ktx)

    // Always on the classpath so the telemetry code compiles either way; it no-ops at runtime when
    // Firebase has not been initialised.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
