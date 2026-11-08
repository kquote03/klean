plugins {
    id("klean.android.library")
    id("klean.android.compose")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
