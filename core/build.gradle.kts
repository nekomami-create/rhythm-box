import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Android に依存しない純 Kotlin モジュール。
// 音源合成・シーケンサ・データモデルをここに置き、JVM 単体テストで検証する。
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
