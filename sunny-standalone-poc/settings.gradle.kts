pluginManagement {
    plugins {
        id("com.android.library") version "8.10.1"
        id("org.jetbrains.kotlin.android") version "2.2.21"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sunny-standalone-poc"

include(":sunny-runtime-android")
project(":sunny-runtime-android").projectDir = file("../sunny-runtime-android")

includeBuild("../sunny-core") {
    dependencySubstitution {
        substitute(module("de.meinhelfer.sunny:sunny-core")).using(project(":"))
    }
}
