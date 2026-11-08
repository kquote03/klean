plugins {
    id("klean.android.library")
    id("klean.hilt")
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    api(project(":shared"))
    api(project(":core:common"))
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    api(libs.sqlite.bundled)
    implementation(libs.datastore.preferences)
    implementation(libs.exifinterface)
    implementation(libs.documentfile)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
}
