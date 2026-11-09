plugins { id("klean.android.feature") }
dependencies {
    implementation(project(":feature:settings"))
    implementation(project(":core:notifications"))
    implementation(libs.activity.compose)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.androidx.junit)
}
