plugins {
    id("klean.android.library")
    id("klean.android.compose")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.serialization.json)
}
