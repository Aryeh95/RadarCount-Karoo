pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download the JDK 17 toolchain when the machine has none.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "radarcount-karoo"
include(":app")

/** A gradle.properties value, else the environment variable CI sets, else empty. */
fun credential(property: String, env: String): String =
    providers.gradleProperty(property).getOrElse(System.getenv(env) ?: "")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext is only on GitHub Packages, which needs a login even to read.
        maven("https://maven.pkg.github.com/hammerheadnav/karoo-ext") {
            credentials {
                username = credential("gpr.user", "USERNAME")
                password = credential("gpr.key", "TOKEN")
            }
        }
    }
}
