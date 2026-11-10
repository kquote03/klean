plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:notifications"))
    implementation(project(":core:capture"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
