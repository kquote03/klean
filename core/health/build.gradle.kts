// Health Connect (M22): availability, permissions, sleep and exercise records behind HealthConnectGateway.
plugins {
    id("klean.android.feature")
}
dependencies {
    api(libs.health.connect)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
