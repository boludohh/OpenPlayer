import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Cargar las propiedades del keystore desde el módulo /app del proyecto
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.openplayer.music"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.openplayer.music"
        minSdk = 27
        targetSdk = 37
        versionCode = 82
        versionName = "0.35.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }

    buildFeatures {
        compose = true
        // BuildConfig generado para uso interno (VERSION_NAME, etc.).
        // Ya no se inyectan claves de API: Deezer no requiere credenciales.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Configuración de sourceSets para incluir los binarios nativos en el APK
    sourceSets {
        getByName("main") {
            // Directorio de librerías nativas precompiladas:
            // - bass/: libbass.so, libbassmix.so, libbassflac.so, libbassopus.so, libbass_aac.so
            jniLibs.directories += "src/main/cpp/bass"
        }
    }
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3 MediaSession (notificación, controles, Bluetooth, Android Auto)
    implementation(libs.media3.session)

    // Coil 3: carga de imágenes para carátulas de pistas (y futuro uso web)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // KTagLib: Kotlin bindings para TagLib 2.3.2 (lectura/escritura de metadatos)
    // Licencia: Apache-2.0 (compatible con GPL-3.0)
    implementation(libs.ktaglib)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}