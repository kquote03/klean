// Test helpers shared by every module's screenshot and clutter tests (UI overhaul, plan §2.1 and §2.3b).
// A plain library rather than test fixtures, so feature modules depend on it with testImplementation.
plugins {
    id("klean.android.library")
    id("klean.android.compose")
}
dependencies {
    implementation(project(":core:design"))
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui.test.junit4)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.junit)
}
