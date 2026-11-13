import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    id("com.android.application")
}

val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)

android {
    namespace = "io.github.whoami018.lunmella"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.whoami018.lunmella"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("RELEASE_STORE_FILE")?.toString()
                ?: System.getenv("RELEASE_STORE_FILE")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD")?.toString()
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS")?.toString()
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD")?.toString()
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            vcsInfo {
                include = false
            }
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    applicationVariants.configureEach {
        val variant = this
        variant.outputs.configureEach {
            val output = this as ApkVariantOutputImpl
            val abi = output.filters.find { it.filterType == "ABI" }?.identifier
            val abiCode = abiCodes[abi]
            if (abiCode != null) {
                output.versionCodeOverride = variant.versionCode * 10 + abiCode
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.activity:activity:1.11.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:common:0.18.1")
}
