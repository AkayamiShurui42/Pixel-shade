plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val shizukuPlusRoot = file("../third_party/shizuku-plus")
val shizukuPlusAars = mapOf(
    "shizuku-plus-aidl.aar" to "aidl/build/outputs/aar/aidl-release.aar",
    "shizuku-plus-shared.aar" to "shared/build/outputs/aar/shared-release.aar",
    "shizuku-plus-api.aar" to "api/build/outputs/aar/api-release.aar",
    "shizuku-plus-provider.aar" to "provider/build/outputs/aar/provider-release.aar"
)

val windows = System.getProperty("os.name").lowercase().contains("windows")
val shizukuPlusGradlew = File(shizukuPlusRoot, if (windows) "gradlew.bat" else "gradlew")
val buildShizukuPlusAars = tasks.register<Exec>("buildShizukuPlusAars") {
    workingDir = shizukuPlusRoot
    commandLine(
        if (windows) {
            listOf("cmd", "/c", shizukuPlusGradlew.absolutePath)
        } else {
            listOf("bash", shizukuPlusGradlew.absolutePath)
        } + listOf(
            "--no-daemon",
            ":aidl:assembleRelease",
            ":shared:assembleRelease",
            ":api:assembleRelease",
            ":provider:assembleRelease"
        )
    )
    onlyIf {
        val missingArtifacts = shizukuPlusAars.values.filter { !File(shizukuPlusRoot, it).exists() }
        if (missingArtifacts.isNotEmpty() && !shizukuPlusGradlew.exists()) {
            throw GradleException("Missing vendored Shizuku Plus Gradle wrapper at ${shizukuPlusGradlew.absolutePath}")
        }
        missingArtifacts.isNotEmpty()
    }
}

val stageShizukuPlusAars = tasks.register("stageShizukuPlusAars") {
    dependsOn(buildShizukuPlusAars)
    doLast {
        val outputDir = file("libs")
        outputDir.mkdirs()
        shizukuPlusAars.forEach { (name, relativePath) ->
            val source = File(shizukuPlusRoot, relativePath)
            if (!source.exists()) {
                throw GradleException("Missing Shizuku Plus AAR: ${source.absolutePath}. Build the vendored client first.")
            }
            copy {
                from(source)
                into(outputDir)
                rename { name }
            }
        }
    }
}

tasks.named("preBuild") { dependsOn(stageShizukuPlusAars) }

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
            storeType = "JKS"
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
    testImplementation("junit:junit:4.13.2")
}
