plugins {
    id("klean.android.library")
    id("klean.hilt")
}
dependencies {
    api(project(":shared"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
}
