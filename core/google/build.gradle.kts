// Google Tasks overlay (M21): GoogleAuth (Play services AuthorizationClient, or a fake), GoogleTasksClient
// (read lists and tasks, PATCH only; never creates a task), the `task` row cache and an offline outbox.
plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.play.services.auth)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// NoTaskCreationTest scans this module's sources, so a source change re-runs it.
tasks.withType<Test>().configureEach {
    inputs.dir(file("src/main/kotlin")).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("googleSources")
}
