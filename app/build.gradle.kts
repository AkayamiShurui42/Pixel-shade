plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val buildEpochMinute = (System.currentTimeMillis() / 60_000L).toInt()
val pixelShadeVersionCode = providers.gradleProperty("pixelShadeVersionCode")
    .orNull
    ?.toIntOrNull()
    ?: (10_000_000 + buildEpochMinute)
require(pixelShadeVersionCode in 1..2_100_000_000) {
    "pixelShadeVersionCode must be a valid positive Android version code"
}

android {
    namespace = "com.crimson.pixelshade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crimson.pixelshade"
        minSdk = 26
        targetSdk = 35
        // Epoch-minute codes keep later local and CI builds upgrade-compatible.
        // Release automation may override this explicitly with -PpixelShadeVersionCode.
        versionCode = pixelShadeVersionCode
        versionName = ciRunNumber?.let { "0.1.0-dev.ci.$it" }
            ?: "0.1.0-dev.local.$buildEpochMinute"
    }

    val ciDebugKeystore = file("pixelshade-debug.keystore")
    signingConfigs {
        create("pixelShadeCiDebug") {
            storeFile = ciDebugKeystore
            storePassword = "pixelshade-debug"
            keyAlias = "pixelshade-debug"
            keyPassword = "pixelshade-debug"
            storeType = "JKS"
        }
    }

    buildTypes {
        getByName("debug") {
            // Fail closed if the stable development key was not restored by the
            // canonical build script instead of silently changing signer identity.
            signingConfig = signingConfigs.getByName("pixelShadeCiDebug")
        }
    }

    lint {
        abortOnError = true
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(files("libs/shizuku-plus-aidl.aar"))
    implementation(files("libs/shizuku-plus-shared.aar"))
    implementation(files("libs/shizuku-plus-api.aar"))
    implementation(files("libs/shizuku-plus-provider.aar"))

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
