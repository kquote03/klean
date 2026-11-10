// AI delegation and Hermes (M23): DelegationAgent (partial AI, Hermes API Server), approvals, questions.
plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:notifications"))
    implementation(project(":core:capture"))
    implementation(project(":core:ai"))
    implementation(project(":feature:tasks"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.core.ktx)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
