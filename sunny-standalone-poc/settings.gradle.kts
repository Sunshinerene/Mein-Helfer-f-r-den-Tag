pluginManagement {
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

includeBuild("../sunny-core") {
    dependencySubstitution {
        substitute(module("de.meinhelfer.sunny:sunny-core")).using(project(":"))
    }
}
