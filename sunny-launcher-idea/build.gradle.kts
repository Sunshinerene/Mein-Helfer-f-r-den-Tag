plugins {
    id("com.android.application") version "8.10.1"
}

android {
    namespace = "de.meinhelfer.sunny.launcher.idea"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "de.meinhelfer.sunny.launcher.idea"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-idea"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            signingConfig = null
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.enable = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
