// settings.gradle.kts
// 项目级设置文件，定义项目名称与仓库源
pluginManagement {
    repositories {
        google()                                  // Google Maven 仓库（AndroidX / ML Kit）
        mavenCentral()                            // Maven Central（Kotlin / 其他依赖）
        gradlePluginPortal()                      // Gradle 插件门户
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ThreeKingdomsStrategyAssistant"
include(":app")
