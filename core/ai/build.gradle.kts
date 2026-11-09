plugins {
    id("klean.android.library")
    id("klean.hilt")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    api(project(":shared"))
    implementation(project(":core:common"))
    api(project(":core:data"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    // On-device model runtime (Task 14.1, see ON_DEVICE.md). Native libs: arm64-v8a + x86_64 only.
    implementation(libs.litertlm)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.documentfile)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.work.testing)
}
