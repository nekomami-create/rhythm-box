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
    // :web（Kotlin/JS）のビルドに Node.js 本体が要る。Kotlin Gradle Plugin は
    // 内部で https://nodejs.org/dist をリポジトリとして登録するが、これは
    // FAIL_ON_PROJECT_REPOS だと「無断追加」として常に弾かれる（下に同じ
    // 取得先を明示的に宣言しても、プラグイン自身の登録がある時点で落ちる）。
    // PREFER_SETTINGS にして、代わりに同じ取得先をここで明示的に宣言して
    // おくことで、プラグインの無断登録は黙って無視されつつ、実際の解決は
    // ここで宣言したほうで行われる（Kotlin Gradle Plugin が示す定石の回避策）。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        ivy("https://nodejs.org/dist") {
            name = "Node Distributions at https://nodejs.org/dist"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // webpack（JS バンドラ）を揃えるのに Yarn も要る。同じ理由で同じ形の
        // 明示宣言が必要（Node.js だけで済まなかった）。
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions at https://github.com/yarnpkg/yarn/releases/download"
            patternLayout { artifact("v[revision]/yarn-v[revision].tar.gz") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "BreakBox"
include(":app")
include(":core")
include(":web")
