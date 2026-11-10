plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:notifications"))
    implementation(project(":core:capture"))
    implementation(project(":core:health"))
    // The sleep schedule editor is the quiet-hours picker (M20: the schedule is the source of quiet hours).
    implementation(project(":feature:settings"))
    implementation(libs.core.ktx)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
