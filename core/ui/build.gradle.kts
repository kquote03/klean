plugins {
    id("klean.android.library")
    id("klean.android.compose")
    id("klean.hilt")
}
dependencies {
    api(project(":shared"))
    api(project(":core:design"))
    api(project(":core:data"))
    api(libs.navigation.compose)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
