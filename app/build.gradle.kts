plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.mockgpslab"

    // 36.1 is the newest platform installed here; it is a "minor" SDK release of API 36.
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.example.mockgpslab"
        minSdk = 26
        // targetSdk is a plain integer in the manifest, so it cannot express the 36.1 minor release.
        targetSdk = 36
        // CI passes -PciVersionCode=<run number> so every uploaded build is newer than the last.
        versionCode = (project.findProperty("ciVersionCode") as String?)?.toInt() ?: 1
        versionName = "1.0"
    }

    // CI builds are signed with the same key every time (restored from a GitHub secret), otherwise the phone
    // rejects updates with "package conflicts". The file is gitignored and only exists on CI.
    signingConfigs {
        val ciKeystore = file("ci-debug.keystore")
        if (ciKeystore.exists()) {
            create("ci") {
                storeFile = ciKeystore
                storePassword = System.getenv("CI_KEYSTORE_PASSWORD")
                keyAlias = "ci"
                keyPassword = System.getenv("CI_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("ci")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
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

    lint {
        // Fail the build on real problems, but keep the report readable.
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        // Our tests are plain JVM; if an Android stub slips in we want it to fail loudly rather than return 0/null.
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.osmdroid.android)
    // Optional Fused mock (step 1.6). Only reached when the user turns the toggle on and Play Services exists.
    implementation(libs.play.services.location)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
