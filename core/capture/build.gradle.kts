plugins {
    id("klean.android.library")
    id("klean.android.compose")
    id("klean.hilt")
    // VoiceRoute is a type-safe navigation route.
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    api(project(":shared"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    implementation(project(":core:notifications"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    testImplementation(libs.coroutines.test)
    testImplementation(project(":core:testing"))
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}
