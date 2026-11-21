import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.oss.licenses)
}

// AdMob ids: the one place they live. debug and promo (the recorder) keep Google's public TEST ids,
// which always serve test ads and never earn or cost anything. Only release uses the app's own ids;
// the release build stops if they are blank or still the test ones (see the check below).
// Never tap real ads in a development build.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdUnitId = "ca-app-pub-3940256099942544/5224354917"
val prodAdmobAppId = "ca-app-pub-4613906044576751~9445204503"
val prodRainAdUnitId = "ca-app-pub-4613906044576751/9329654437"
val prodCoinAdUnitId = "ca-app-pub-4613906044576751/1471345716"

// The upload key for Play (docs/release/README.md). keystore.properties sits at the project's root
// and is never committed (.gitignore): storeFile, storePassword, keyAlias, keyPassword. Without it the
// release build is simply left unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.studioguu.miniaturegarden"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.studioguu.miniaturegarden"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"

        manifestPlaceholders["admobAppId"] = testAdmobAppId
        buildConfigField("String", "ADMOB_RAIN_AD_UNIT_ID", "\"$testAdUnitId\"")
        buildConfigField("String", "ADMOB_COIN_AD_UNIT_ID", "\"$testAdUnitId\"")
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            manifestPlaceholders["admobAppId"] = prodAdmobAppId
            buildConfigField("String", "ADMOB_RAIN_AD_UNIT_ID", "\"$prodRainAdUnitId\"")
            buildConfigField("String", "ADMOB_COIN_AD_UNIT_ID", "\"$prodCoinAdUnitId\"")
            // R8 shrinks and obfuscates the release (2026-11-08); the mapping for Play Console is
            // build/outputs/mapping/release/mapping.txt. Unused resources go too.
            isMinifyEnabled = true
            isShrinkResources = true
            // Debug symbols of the native libraries (SQLCipher ...) go into the AAB, so that Play
            // Console can read native crashes and stops warning about their absence.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // The promo recorder (src/promo, promo/README.md): plays a script and writes the garden
        // out frame by frame. Never published: only the release build goes to the store, and none
        // of src/promo is in it. Its own id, so it sits beside the game on the same device with
        // its own storage.
        create("promo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".promo"
            versionNameSuffix = "-promo"
            matchingFallbacks += "debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Compose UI tests run under Robolectric and need the app's strings and themes.
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// A release that would ship Google's test ads (or none) is a mistake: stop it.
gradle.taskGraph.whenReady {
    val building = allTasks.any { it.project == project && it.name.matches(Regex("(assemble|bundle|package)Release")) }
    if (building) {
        val ids = listOf(prodAdmobAppId, prodRainAdUnitId, prodCoinAdUnitId)
        check(ids.none { it.isBlank() || it.startsWith("ca-app-pub-3940256099942544") }) {
            "release needs the production AdMob ids (blank or Google test ids found); see the top of app/build.gradle.kts"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// No instrumented tests yet: they come before the first update, for the migrations (2026-11-08).
// Until src/androidTest exists no androidTest variant is made, so connectedAndroidTest has nothing
// to run, rather than failing on finding none.
androidComponents {
    beforeVariants { variant ->
        if (!file("src/androidTest").exists()) variant.androidTest.enable = false
    }
}

dependencies {
    // Core / Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room (encrypted with SQLCipher)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher.android)

    // Settings
    implementation(libs.androidx.datastore.preferences)

    // Rewarded ads
    implementation(libs.play.services.ads)
    implementation(libs.play.review)
    // The open-source licence list in settings, written at build time by the oss-licenses plugin.
    implementation(libs.play.services.oss.licenses)
    implementation(libs.androidx.media3.exoplayer)

    // Notifications posted while the app is away
    implementation(libs.androidx.work.runtime)

    // Coroutines / Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // The empty activity the Compose test rule shows its content in; debug builds only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
