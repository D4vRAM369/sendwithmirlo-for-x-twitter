plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.d4vram369.mirlox"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.d4vram369.mirlox"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Sin R8: renombraría la clase que LSPatch busca por su nombre en assets/xposed_init.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // compileOnly: la API la pone LSPatch dentro del proceso de X; meterla en el APK rompe la carga.
    compileOnly("de.robv.android.xposed:api:82")
}
