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
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}
