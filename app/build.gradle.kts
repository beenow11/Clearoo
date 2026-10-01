plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.clearoo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clearoo.app"
        // Android 11+: needed for MediaStore trash requests.
        minSdk = 30
        // Google Play requires targeting a recent Android version for new apps and updates.
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.0"
    }

    signingConfigs {
        // A fixed debug key checked into the repo, so every CI build is signed the same way
        // and installs as an update. (Each CI runner would otherwise generate its own key.)
        getByName("debug") {
            storeFile = file("clearoo-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Play upload key. Only CI release builds have it, via repository secrets (see docs/PLAY_STORE.md).
        System.getenv("UPLOAD_KEYSTORE_PATH")?.let { path ->
            create("upload") {
                storeFile = file(path)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
}
