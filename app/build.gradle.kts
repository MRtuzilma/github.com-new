plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.avtolauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.avtolauncher"
        minSdk = 21
        targetSdk = 34
        versionCode = 12
        versionName = "2.5"
    }

    // Doimiy sinov imzosi: har bir yangi versiya eskisining ustidan o'rnatiladi.
    // (Sotishdan oldin alohida, maxfiy release kaliti qo'yiladi.)
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
}
