plugins {
    id("klean.android.library")
    id("klean.hilt")
}
dependencies {
    api(project(":shared")) // re-exports coroutines + kotlinx-datetime
}
