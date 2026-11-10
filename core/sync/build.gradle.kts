// Android sync (M25): SyncEngine over per-table adapters, pairing, WorkManager, Settings › Sync and the setup page.
plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:notifications"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.zxing.android.embedded)
    testImplementation(project(":backend"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.work.testing)
    testImplementation(libs.datastore.preferences)
}

// SyncTablesTest reads Room's exported schema, so a schema change re-runs it.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("core/data/schemas")).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("roomSchemas")
}
