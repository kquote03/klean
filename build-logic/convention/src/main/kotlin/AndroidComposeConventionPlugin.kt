import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> { buildFeatures { compose = true } }
        dependencies {
            val bom = platform(catalog.lib("compose-bom"))
            add("implementation", bom)
            add("implementation", catalog.lib("compose-ui"))
            add("implementation", catalog.lib("compose-material3"))
            add("implementation", catalog.lib("compose-material-icons"))
            add("implementation", catalog.lib("compose-ui-tooling-preview"))
            add("debugImplementation", catalog.lib("compose-ui-tooling"))
        }
    }
}
