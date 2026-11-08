plugins {
    id("klean.android.feature")
    id("klean.sqlite.jvmtest")
}
dependencies {
    implementation(project(":core:ai"))
    testImplementation(libs.androidx.junit)
    testImplementation(libs.datastore.preferences)
}
