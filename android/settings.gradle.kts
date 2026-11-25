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
    }
}

rootProject.name = "SeveredChainsAndroid"

// app: o APK (launcher + ponte nativa para a JVM HotSpot)
// runtime-test: programa Java 25 que roda dentro da HotSpot no aparelho (marco M1)
include(":app", ":runtime-test")
