import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma de release: datos en keystore.properties, en la carpeta que contiene este repo (así las contraseñas
// ni siquiera están dentro del repo) o, si no, en su raíz (ignorado por git). Sin ese archivo, assembleRelease
// sale sin firmar y el resto del proyecto compila igual.
val signing = listOf("../keystore.properties", "keystore.properties").map(rootProject::file).firstOrNull { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use(::load) }
}

android {
    namespace = "io.github.d4vram369.sendwithmirlo"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.d4vram369.sendwithmirlo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signing != null) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (signing != null) signingConfig = signingConfigs.getByName("release")
            // Sin R8: renombraría la clase que LSPatch busca por su nombre en META-INF/xposed/java_init.list.
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
    // compileOnly: la API (libxposed, la moderna) la pone LSPatch dentro del proceso de X; meterla en el APK rompe la carga.
    compileOnly("io.github.libxposed:api:102.0.0")
}
