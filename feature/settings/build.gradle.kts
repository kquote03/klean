plugins { id("klean.android.feature") }
dependencies {
    implementation(libs.documentfile)
    testImplementation(libs.datastore.preferences)
}
