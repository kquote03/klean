import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("klean.android.library")
        pluginManager.apply("klean.android.compose")
        pluginManager.apply("klean.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            listOf(":core:ui", ":core:design", ":core:data", ":core:common", ":shared").forEach {
                add("implementation", project(it))
            }
            listOf("navigation-compose", "hilt-navigation-compose", "lifecycle-runtime-compose",
                "lifecycle-viewmodel-compose", "serialization-json").forEach { add("implementation", catalog.lib(it)) }
            listOf("robolectric", "turbine", "coroutines-test", "androidx-test-core").forEach { add("testImplementation", catalog.lib(it)) }
        }
    }
}
