plugins { `kotlin-dsl` }
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}
gradlePlugin {
    plugins {
        register("androidLibrary") { id = "klean.android.library"; implementationClass = "AndroidLibraryConventionPlugin" }
        register("androidCompose") { id = "klean.android.compose"; implementationClass = "AndroidComposeConventionPlugin" }
        register("hilt") { id = "klean.hilt"; implementationClass = "HiltConventionPlugin" }
        register("androidFeature") { id = "klean.android.feature"; implementationClass = "AndroidFeatureConventionPlugin" }
        register("sqliteJvmTest") { id = "klean.sqlite.jvmtest"; implementationClass = "SqliteJvmTestConventionPlugin" }
    }
}
