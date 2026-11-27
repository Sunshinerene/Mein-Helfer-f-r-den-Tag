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

rootProject.name = "MeinHelferFuerDenTag"
include(":app")
include(":sunny-runtime-android")
includeBuild("sunny-core") {
    dependencySubstitution {
        substitute(module("de.meinhelfer.sunny:sunny-core")).using(project(":"))
    }
}
