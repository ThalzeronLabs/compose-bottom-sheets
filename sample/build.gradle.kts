plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.thalzeronlabs.sheets.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.thalzeronlabs.sheets.sample"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// By default the sample uses the library from this repository. With -PsheetsVersion=v0.2.0 it uses the artifact
// JitPack serves for that tag instead, exactly like an app would: that is the consumer check in CI.
val sheetsVersion: String? = providers.gradleProperty("sheetsVersion").orNull

dependencies {
    if (sheetsVersion == null) {
        implementation(project(":sheets"))
    } else {
        implementation("com.github.ThalzeronLabs:compose-bottom-sheets:$sheetsVersion")
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
}
