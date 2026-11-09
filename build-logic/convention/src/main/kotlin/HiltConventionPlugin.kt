import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", catalog.lib("hilt-android"))
            add("ksp", catalog.lib("hilt-compiler"))
        }
        // A cached KSP result doesn't restore the generated Java (Hilt components). On a fresh
        // checkout (CI) javac then sees NO-SOURCE and Robolectric Hilt tests fail with
        // ClassNotFoundException for *_TestComponentDataSupplier. KSP is fast; never cache it.
        tasks.matching { it.name.startsWith("ksp") }.configureEach { outputs.cacheIf { false } }
    }
}
