import org.gradle.api.Plugin
import org.gradle.api.Project

/** Roborazzi for modules that don't use klean.android.compose (the app). */
class RoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.roborazzi()
}
