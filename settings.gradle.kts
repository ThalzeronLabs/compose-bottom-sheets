pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only for the consumer check: `:sample` built against the published artifact (-PsheetsVersion=vX.Y.Z).
        // Exclusive, so that group is only ever looked up on JitPack and nothing else is.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.ThalzeronLabs") }
        }
    }
}

rootProject.name = "compose-bottom-sheets"

include(":sheets")
include(":sample")
