plugins { `kotlin-dsl` }
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    // Applied by klean.android.compose: every Compose module gets Roborazzi screenshot tests.
    implementation(libs.roborazzi.gradlePlugin)
}
gradlePlugin {
    plugins {
        register("androidLibrary") { id = "klean.android.library"; implementationClass = "AndroidLibraryConventionPlugin" }
        register("androidCompose") { id = "klean.android.compose"; implementationClass = "AndroidComposeConventionPlugin" }
        register("hilt") { id = "klean.hilt"; implementationClass = "HiltConventionPlugin" }
        register("androidFeature") { id = "klean.android.feature"; implementationClass = "AndroidFeatureConventionPlugin" }
        register("roborazzi") { id = "klean.roborazzi"; implementationClass = "RoborazziConventionPlugin" }
        register("sqliteJvmTest") { id = "klean.sqlite.jvmtest"; implementationClass = "SqliteJvmTestConventionPlugin" }
    }
}
