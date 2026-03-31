plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "com.motorola.motomouse"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.motorola.motomouse"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.androidxLifecycleViewmodelKtx)
    implementation(libs.androidxLifecycleViewmodelCompose)
    implementation(libs.androidxActivityCompose)
    implementation(platform(libs.androidxComposeBom))
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeFoundation)
    implementation(libs.androidxComposeUiGraphics)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxComposeMaterial3)
    implementation(libs.androidxDatastorePreferences)
    implementation(libs.androidxCameraCore)
    implementation(libs.androidxCameraCamera2)
    implementation(libs.androidxCameraLifecycle)
    implementation(libs.androidxCameraView)
    implementation(libs.googleMlkitBarcodeScanning)
    implementation(libs.kotlinxCoroutinesAndroid)
    testImplementation(libs.junit)
    testImplementation(libs.jsonJvm)
    androidTestImplementation(libs.androidxJunit)
    androidTestImplementation(libs.androidxEspressoCore)
    androidTestImplementation(platform(libs.androidxComposeBom))
    androidTestImplementation(libs.androidxComposeUiTestJunit4)
    debugImplementation(libs.androidxComposeUiTooling)
    debugImplementation(libs.androidxComposeUiTestManifest)
}