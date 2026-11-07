pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // :web（Kotlin/JS）が Node.js 本体を取ってくるのに、プラグイン自身が
    // 内部で https://nodejs.org/dist をリポジトリとして登録する。
    // FAIL_ON_PROJECT_REPOS だとこれも「無断追加」として弾かれてしまうため、
    // 「ここで宣言したものを優先しつつ、それ以外も許す」に緩めてある。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BreakBox"
include(":app")
include(":core")
include(":web")
