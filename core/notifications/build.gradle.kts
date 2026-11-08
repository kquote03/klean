plugins {
    id("klean.android.library")
    id("klean.hilt")
}
dependencies {
    api(project(":shared"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}
