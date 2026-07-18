// Kotlin 컴파일은 AGP 9의 내장 지원을 쓴다 (kotlin-android 플러그인 불필요)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
