plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.rhythmbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.rhythmbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "1.24"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // リポジトリに固定したデバッグ鍵。毎回同じ署名になるので、CI が出力した
        // APK 同士を上書きインストールでき、保存した曲データが維持される。
        // ※デバッグ用の公開鍵であり、Google Play 配布用ではない。
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // 配布はこのビルドを使う。デバッグビルドと違い debuggable が付かない。
            // 署名はデバッグ用と同じ固定鍵。署名が変わると上書き更新ができなくなり、
            // 保存した曲が消えてしまうため、鍵は変えない。
            signingConfig = signingConfigs.getByName("debug")
            // R8 での圧縮は実機での動作確認ができていないので、いまは切っている。
            // （有効にすると APK は小さくなるが、壊れても CI では検出できない）
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        // ヘルプ画面にバージョンを出すために使う。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.ui.tooling)
}
