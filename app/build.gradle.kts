import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Ad ids. Google's public TEST ids are the defaults, so a build without any setup shows only test ads. Real ids go into
// local.properties (never committed): admob.appId, admob.bannerId, admob.interstitialId. Remote Config can override the unit ids
// (release builds only; debug builds always use the test ids so nobody taps a real ad while developing).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun adProp(name: String, test: String): String = (localProps.getProperty(name) ?: "").trim().ifEmpty { test }
val realIdsMissing = listOf("admob.appId", "admob.bannerId", "admob.interstitialId", "admob.appOpenId", "admob.nativeId").filter { localProps.getProperty(it).isNullOrBlank() }
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val testAppOpenId = "ca-app-pub-3940256099942544/9257395921"
val testNativeId = "ca-app-pub-3940256099942544/2247696110"

// Firebase is configured by google-services.json (git-ignored, from your Firebase console). Without it the app still builds
// and AI features report that they are not set up.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    // Crash reports from every tester's phone (model, Android version, full stack trace) in the Firebase console.
    // Also uploads the R8 mapping file so release stack traces are readable.
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.translator.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.translator.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["admobAppId"] = adProp("admob.appId", testAppId)
        buildConfigField("boolean", "ADS_USING_TEST_IDS", realIdsMissing.isNotEmpty().toString())
        buildConfigField("String", "ADMOB_APP_ID", "\"" + adProp("admob.appId", testAppId) + "\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"" + adProp("admob.bannerId", testBannerId) + "\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"" + adProp("admob.interstitialId", testInterstitialId) + "\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"" + adProp("admob.appOpenId", testAppOpenId) + "\"")
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"" + adProp("admob.nativeId", testNativeId) + "\"")
        buildConfigField("String", "ADMOB_TEST_BANNER_ID", "\"" + testBannerId + "\"")
        buildConfigField("String", "ADMOB_TEST_INTERSTITIAL_ID", "\"" + testInterstitialId + "\"")
        buildConfigField("String", "ADMOB_TEST_APP_OPEN_ID", "\"" + testAppOpenId + "\"")
        buildConfigField("String", "ADMOB_TEST_NATIVE_ID", "\"" + testNativeId + "\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("../keystore/stylishkeyboardstation.keystore")
            storePassword = "stylishkeyboardstation"
            keyAlias = "stylishkeyboardstation"
            keyPassword = "stylishkeyboardstation"
        }
    }

    buildTypes {
        release {
            // Signed with the release key when it is present; otherwise (a CI machine, another computer) with the debug key, so
            // an optimized release APK can still be built and tested. Only the release-key build goes to Play.
            signingConfig = if (rootProject.file("../keystore/stylishkeyboardstation.keystore").exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            // R8 shrinking and optimization, and removal of unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The on-device translation and text-recognition engines ship a native library per CPU type (about 15-30 MB each).
            // Real phones are arm64-v8a or armeabi-v7a; x86 is only for emulators, which use the debug build.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/INDEX.LIST", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.splashscreen)
    // Installs the startup profile (src/main/baseline-prof.txt plus the ones Compose ships), so screens run as compiled code
    // from the first launch instead of being interpreted, also on APKs installed outside the Play Store.
    implementation(libs.profileinstaller)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.gma.next.gen)
    implementation(libs.ump)
    implementation(libs.mlkit.translate)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}

// A release build with Google's test ad ids earns nothing: say so loudly, but do not fail the build.
gradle.taskGraph.whenReady {
    val releaseBuild = allTasks.any { it.project == project && it.name.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package")) }
    if (releaseBuild && realIdsMissing.isNotEmpty()) {
        logger.warn("WARNING: release build with Google TEST ad ids. Missing in local.properties: ${realIdsMissing.joinToString()}. Ads will not earn until real ids are set.")
    }
}
