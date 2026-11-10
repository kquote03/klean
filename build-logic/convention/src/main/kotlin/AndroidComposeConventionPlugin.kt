import com.android.build.api.dsl.LibraryExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> {
            buildFeatures { compose = true }
            // Roborazzi renders with Robolectric's native graphics (docs/ui/design-system.md, plan §2.1).
            testOptions.unitTests.isIncludeAndroidResources = true
            testOptions.unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
        roborazzi()
        dependencies {
            val bom = platform(catalog.lib("compose-bom"))
            add("implementation", bom)
            add("implementation", catalog.lib("compose-ui"))
            add("implementation", catalog.lib("compose-material3"))
            add("implementation", catalog.lib("compose-material-icons"))
            add("implementation", catalog.lib("compose-ui-tooling-preview"))
            add("debugImplementation", catalog.lib("compose-ui-tooling"))
            add("testImplementation", bom)
            listOf("roborazzi", "roborazzi-compose", "roborazzi-junit-rule", "robolectric", "compose-ui-test-junit4", "androidx-test-core", "androidx-junit")
                .forEach { add("testImplementation", catalog.lib(it)) }
            add("debugImplementation", catalog.lib("compose-ui-test-manifest"))
        }
    }
}

/**
 * Screenshot tests: `recordRoborazziDebug` writes, `verifyRoborazziDebug` compares. Reference images are
 * committed under `src/test/snapshots/` (tests pass that path to `captureRoboImage`).
 */
fun Project.roborazzi() {
    pluginManager.apply("io.github.takahirom.roborazzi")
    extensions.configure<RoborazziExtension> { outputDir.set(file("src/test/snapshots")) }
}
