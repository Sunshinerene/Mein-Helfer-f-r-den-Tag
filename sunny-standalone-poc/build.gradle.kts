plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.2.21"
}

android {
    namespace = "de.meinhelfer.sunny.poc"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "de.meinhelfer.sunny.poc"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2-poc"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.enable = false
    }
}

dependencies {
    implementation("de.meinhelfer.sunny:sunny-core:0.1")
    testImplementation("junit:junit:4.13.2")
}
