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
        versionCode = 52
        versionName = "1.52"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // どの APK を入れたのかが分かるように、CI のビルド番号を埋め込む。
        // versionName は手で上げるので忘れると古いまま出る（実際に一度そうなった）。
        // こちらは CI が毎回入れるので、少なくとも「いつのビルドか」は必ず合う。
        val build = (project.findProperty("buildNumber") as String?)?.takeIf { it.isNotBlank() }
        buildConfigField("String", "BUILD_LABEL", "\"${build ?: "手元ビルド"}\"")
    }

    // 配布用の鍵は CI の secret から渡す。渡ってこなければデバッグ鍵に落ちる
    // （手元でリリースビルドを組むときのため）。どちらで署名したかは
    // ビルドログに出すので、切り替わったかどうかを目で確かめられる。
    // CI からは ORG_GRADLE_PROJECT_releaseStoreFile などの環境変数で渡ってくる
    // （Gradle がプロパティとして読む）。手元では渡さなければいい。
    val releaseStore = (project.findProperty("releaseStoreFile") as String?)?.takeIf { it.isNotBlank() }
    val hasReleaseKey = releaseStore != null && file(releaseStore).exists()

    signingConfigs {
        // リポジトリに固定したデバッグ鍵。毎回同じ署名になるので、CI が出力した
        // APK 同士を上書きインストールでき、保存した曲データが維持される。
        // ※世界中に知られている標準のデバッグ鍵。配布用ではない。
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStore!!)
                storePassword = project.property("releaseStorePassword") as String
                keyAlias = project.property("releaseKeyAlias") as String
                keyPassword = project.property("releaseKeyPassword") as String
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // 配布はこのビルドを使う。デバッグビルドと違い debuggable が付かない。
            //
            // 署名が変わると上書き更新ができなくなり、入れ直し＝保存した曲が
            // 消えることになる。だから鍵は軽々に変えない。変えるときは
            // 「全曲をバックアップ」で控えを取ってからにする。
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            println(
                if (hasReleaseKey) {
                    "BreakBox: 配布用の鍵で署名します"
                } else {
                    "BreakBox: ⚠ 配布用の鍵が渡っていません。デバッグ鍵（公開鍵）で署名します"
                },
            )
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
