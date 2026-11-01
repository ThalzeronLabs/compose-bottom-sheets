plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.dokka)
    `maven-publish`
}

// JitPack sets VERSION to the git tag (e.g. v0.1.0); local builds fall back to the current release line.
group = "com.github.ThalzeronLabs"
version = providers.environmentVariable("VERSION").getOrElse("0.1.0")

android {
    namespace = "com.thalzeronlabs.sheets"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged resources to render Compose for the screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into FileDescriptor internals; newer JDKs need this to be exported explicitly.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dokka {
    moduleName.set("compose-bottom-sheets")
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/ThalzeronLabs/compose-bottom-sheets/tree/main/sheets/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.ThalzeronLabs"
            artifactId = "compose-bottom-sheets"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("compose-bottom-sheets")
                description.set("Material 3 bottom sheets for Jetpack Compose that you can await: confirm, choose, pickDate, pickTime and input.")
                url.set("https://github.com/ThalzeronLabs/compose-bottom-sheets")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("ThalzeronLabs")
                        name.set("Esdras Souza")
                    }
                }
                scm {
                    url.set("https://github.com/ThalzeronLabs/compose-bottom-sheets")
                    connection.set("scm:git:https://github.com/ThalzeronLabs/compose-bottom-sheets.git")
                }
            }
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Screenshot tests: Robolectric renders on the JVM, Roborazzi records / verifies the PNGs. No emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
