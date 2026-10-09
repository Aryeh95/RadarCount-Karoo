import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // karoo-ext's inline addConsumer<T>() decodes events in our own code;
    // with this plugin they compile to direct serializer calls instead of
    // a reflective lookup at run time.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.aryeh95.radarcount"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.aryeh95.radarcount"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing: a permanent keystore supplied via environment
    // variables (CI secrets or a local shell). Falls back to the debug key
    // so local builds still work, but those cannot update a CI-signed
    // install and vice versa.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && file(keystorePath).exists()
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "radarcount"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug builds carry the commit in their version, so a test build on
        // the Karoo can be told apart from the release it replaces.
        debug {
            // Outside a git checkout (a source archive) or without git, "dev".
            val sha = runCatching {
                providers.exec {
                    commandLine("git", "rev-parse", "--short", "HEAD")
                    isIgnoreExitValue = true
                }.standardOutput.asText.get().trim()
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "dev"
            versionNameSuffix = "-$sha"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// manifest.json's latestApkUrl and scripts/check_manifest.py expect this
// name. Debug builds get it too, without their commit suffix.
androidComponents {
    onVariants { variant ->
        val apkName = "radarcount-karoo-${android.defaultConfig.versionName}.apk"
        variant.outputs.forEach { (it as VariantOutputImpl).outputFileName.set(apkName) }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    val jupiter = "5.11.4"

    implementation("io.hammerhead:karoo-ext:1.1.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // The settings app
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Not used directly, but karoo-ext and Compose on their own pull in older
    // versions of these than the app has shipped with; hold them there.
    constraints {
        implementation("androidx.core:core:1.15.0")
        implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiter")
    testImplementation("com.google.truth:truth:1.4.4")

    // On-device render checks and store screenshots
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
