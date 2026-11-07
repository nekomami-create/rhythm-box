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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // :web（Kotlin/JS）のビルドに Node.js 本体が要る。Kotlin Gradle Plugin は
        // 内部で https://nodejs.org/dist をリポジトリとして登録するが、
        // FAIL_ON_PROJECT_REPOS はそれも「無断追加」として弾いてしまう
        // （PREFER_SETTINGS に緩めると今度は無視されて依存解決に失敗する）。
        // ここで同じリポジトリを明示的に宣言しておけば、プラグイン側の登録と
        // 競合せずに解決できる（Kotlin Gradle Plugin が示す定石の回避策）。
        ivy("https://nodejs.org/dist") {
            name = "Node Distributions at https://nodejs.org/dist"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}

rootProject.name = "BreakBox"
include(":app")
include(":core")
include(":web")
