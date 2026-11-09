plugins { id("klean.android.feature") }
dependencies {
    implementation(project(":core:notifications"))
    implementation(project(":core:ai"))
    implementation(libs.documentfile)
    implementation(libs.activity.compose)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
