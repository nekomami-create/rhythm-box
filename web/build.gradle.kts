plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// core と同じ音楽ロジックを、Android に依存しない形でそのまま使う。
// core/build.gradle.kts は変えていないので :app の依存解決には影響しない。
//
// js のみの1ターゲットだが、kotlin("js")（単一ターゲット）ではなく
// multiplatform + js() にしてある。core 側の kotlin.concurrent.Volatile
// （@OptionalExpectation）は commonMain 経由でないと解決できないため
// （kotlin("js") の main は「共通ソース」として扱われない）。
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "breakbox-web.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("../core/src/main/kotlin")
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jsMain by getting {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}
