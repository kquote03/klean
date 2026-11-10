plugins {
    id("klean.android.library")
    id("klean.android.compose")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    // KleanScreen: BackHandler for the Add menu; the lifecycle owner that names a screen for its group chrome.
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
