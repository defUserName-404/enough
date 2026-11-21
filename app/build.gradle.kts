import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version comes from the release tag (vMAJOR.MINOR.PATCH) in CI; local builds fall back to 0.0.0-dev.
val appVersion: String = (findProperty("appVersion") as String?) ?: System.getenv("APP_VERSION") ?: "0.0.0-dev"
val appVersionCode: Int = appVersion.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
    .let { p -> if (p.size == 3) p[0] * 10_000 + p[1] * 100 + p[2] else 1 }.coerceAtLeast(1)

// Release signing: CI passes env vars; locally ~/.android/enough/keystore.properties is used if present.
val signing: Map<String, String?> = run {
    val local = File(System.getProperty("user.home"), ".android/enough/keystore.properties")
    val props = Properties().apply { if (local.exists()) local.inputStream().use { load(it) } }
    fun v(env: String, key: String): String? = System.getenv(env) ?: props.getProperty(key)
    mapOf(
        "storeFile" to v("ENOUGH_KEYSTORE_PATH", "storeFile"),
        "storePassword" to v("ENOUGH_KEYSTORE_PASSWORD", "storePassword"),
        "keyAlias" to v("ENOUGH_KEY_ALIAS", "keyAlias"),
        "keyPassword" to v("ENOUGH_KEY_PASSWORD", "keyPassword"),
    )
}
val canSignRelease = signing.values.all { !it.isNullOrBlank() }

android {
    namespace = "dev.clamp"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.enough"
        minSdk = 30
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (canSignRelease) create("release") {
            storeFile = file(signing.getValue("storeFile")!!)
            storePassword = signing["storePassword"]
            keyAlias = signing["keyAlias"]
            keyPassword = signing["keyPassword"]
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["testOnly"] = "true"
        }
        release {
            manifestPlaceholders["testOnly"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG gates DISALLOW_DEBUGGING_FEATURES
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libadb.android)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.conscrypt.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
