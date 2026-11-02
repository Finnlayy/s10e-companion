import java.security.MessageDigest
import java.io.FileInputStream
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.s10e.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.s10e.companion"
        minSdk = 31
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    lint {
        // Die App kommt nie in den Play Store und zielt bewusst auf Android 12 des S10e.
        disable += "ExpiredTargetSdkVersion"
    }

    signingConfigs {
        create("release") {
            val keystore = System.getenv("S10E_KEYSTORE")
            val keystorePassword = System.getenv("S10E_KEYSTORE_PASSWORD")
            val alias = System.getenv("S10E_KEY_ALIAS")

            if (!keystore.isNullOrEmpty() && !keystorePassword.isNullOrEmpty() && !alias.isNullOrEmpty()) {
                storeFile = file(keystore)
                storePassword = keystorePassword
                keyAlias = alias
                keyPassword = keystorePassword
            } else {
                val debugConfig = signingConfigs.getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val fetchSpeechAar by tasks.registering {
    val aarFile = file("libs/sherpa-onnx-1.13.8.aar")
    val expectedSize = 50129134L
    val expectedSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
    val downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"
    
    outputs.file(aarFile)
    
    doLast {
        fun checkFile(): Boolean {
            if (!aarFile.exists() || aarFile.length() != expectedSize) return false
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(aarFile).use { fis ->
                var bytesRead = fis.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = fis.read(buffer)
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            return sha256 == expectedSha256
        }

        if (checkFile()) {
            println("sherpa-onnx AAR is already valid.")
        } else {
            println("Downloading sherpa-onnx AAR...")
            aarFile.parentFile.mkdirs()
            URL(downloadUrl).openStream().use { input ->
                aarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!checkFile()) {
                throw GradleException("Downloaded sherpa-onnx AAR verification failed")
            }
            println("sherpa-onnx AAR downloaded and verified.")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(fetchSpeechAar)
}

dependencies {

    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
