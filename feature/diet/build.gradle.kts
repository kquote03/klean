plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:notifications"))
    implementation(libs.core.ktx)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
}
