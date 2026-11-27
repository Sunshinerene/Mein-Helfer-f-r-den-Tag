plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.2.21"
}

android {
    namespace = "de.meinhelfer.sunny.poc"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "de.meinhelfer.sunny.devtools"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3-devtools"
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
    implementation(project(":sunny-runtime-android"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
