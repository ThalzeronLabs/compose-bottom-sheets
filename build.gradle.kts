plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.dokka) apply false
    // Only its task classes are used, see sheets/build.gradle.kts ("Public API guard").
    alias(libs.plugins.binary.compatibility.validator) apply false
}
