plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.meinhelfer.tag"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.meinhelfer.tag"
        minSdk = 26
        targetSdk = 35
        versionCode = 94
        versionName = "0.91.0"
    }

    // Fester Test-Schluessel, damit spaetere APK-Updates ueber die alte Version installiert werden koennen.
    // Nur fuer Test-Builds. Fuer eine echte Veroeffentlichung wird ein privater Release-Schluessel verwendet.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Testergebnisse im Build-Protokoll anzeigen
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// Datenbank-Schema exportieren, damit ein Test die Umstellungen pruefen kann
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Texterkennung direkt auf dem Handy (Modell ist in der App enthalten, keine Datenuebertragung)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    // Health Connect (Beta): Werte von Uhr, Band, Waage, Blutdruckgerät lesen (1.1.0 stabil braucht compileSdk 36 und AGP 8.9, daher vorerst alpha11)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    // Echtes SQLite fuer den Test der Datenbank-Umstellungen (nur beim Bauen, nicht in der App)
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
}
