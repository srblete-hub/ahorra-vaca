import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Claves de Supabase (parte social). Van en supabase.properties, fuera de git:
//   url=https://xxxx.supabase.co
//   anonKey=eyJ...
// Sin ese archivo la app funciona igual, solo que sin amigos.
val supabase = Properties().apply {
    rootProject.file("supabase.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.caudal.finanzas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.caudal.finanzas"
        // Android 10+: BiometricPrompt nativo, sin el diálogo antiguo de huella.
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        // Solo procesadores de móvil: el lector de tickets pesa ~11 MB por cada uno y los de emulador (x86) sobran.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField("String", "SUPABASE_URL", "\"${supabase.getProperty("url", "").trim().trimEnd('/')}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabase.getProperty("anonKey", "").trim()}\"")
    }

    // Hasta tener clave propia, la release se firma con la clave de debug de este ordenador,
    // con todos los esquemas de firma (v1+v2+v3) para que cualquier instalador la acepte.
    signingConfigs {
        create("compatible") {
            val debug = signingConfigs.getByName("debug")
            storeFile = debug.storeFile
            storePassword = debug.storePassword
            keyAlias = debug.keyAlias
            keyPassword = debug.keyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("compatible")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Pruebas en el PC con Robolectric: recorren la app real y guardan capturas en screenshots/.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("screenshots.dir", rootProject.file("screenshots").absolutePath)
            // Robolectric no carga su librería nativa si la ruta del usuario tiene espacios:
            // se le pasa la ruta corta de Windows (p. ej. C:\Users\EMPLEA~1).
            System.getenv("ROBOLECTRIC_HOME")?.let { home -> it.systemProperty("user.home", home) }
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

// Room guarda aquí el esquema de cada versión de la base de datos, para comprobar las migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    testImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Peticiones al servidor de Supabase (parte social)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Huella dactilar
    implementation("androidx.biometric:biometric:1.1.0")
    // Leer el texto de la foto de un ticket, dentro del móvil y sin internet (gratis).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.72.0")
}
