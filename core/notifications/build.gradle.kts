plugins {
    id("klean.android.library")
    id("klean.hilt")
    id("klean.sqlite.jvmtest")
}
dependencies {
    api(project(":shared"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.coroutines.test)
}
dependencies {
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
