plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:notifications"))
    implementation(project(":core:capture"))
    implementation(libs.core.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
// BuildConfig.DEBUG gates the debug check-in tools in Diet settings.
android { buildFeatures { buildConfig = true } }
