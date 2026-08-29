plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.crimson.pixelshade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crimson.pixelshade"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber ?: 1
        versionName = ciRunNumber?.let { "0.1.0-dev.$it" } ?: "0.1.0-dev"
    }

    val ciDebugKeystore = file("pixelshade-debug.keystore")
    signingConfigs {
        create("pixelShadeCiDebug") {
            storeFile = ciDebugKeystore
            storePassword = "pixelshade-debug"
            keyAlias = "pixelshade-debug"
            keyPassword = "pixelshade-debug"
        }
    }

    buildTypes {
        getByName("debug") {
            if (ciDebugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("pixelShadeCiDebug")
            }
        }
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
}
