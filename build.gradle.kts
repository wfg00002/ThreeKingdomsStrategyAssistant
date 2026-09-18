// build.gradle.kts (项目根)
// 顶层构建文件，声明插件版本，统一配置
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
}
