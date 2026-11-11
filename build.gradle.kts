plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Every JVM test runs in the user's zone and locale, whatever the machine's (CI runners are UTC, this box is
// Asia/Dubai). Screens read the system zone, so Roborazzi references only match when the zone is fixed.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("user.timezone", "Asia/Dubai")
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")
    }
}
