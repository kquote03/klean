import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library") // AGP 9: Kotlin is built in
        extensions.configure<LibraryExtension> {
            namespace = "app.klean." + path.removePrefix(":").replace(':', '.')
            compileSdk = KleanSdk.COMPILE
            defaultConfig {
                minSdk = KleanSdk.MIN
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.isIncludeAndroidResources = true
            testOptions.unitTests.all {
                it.jvmArgs(KleanSdk.ROBOLECTRIC_JVM_ARGS)
                // Hilt generates test sources in modules without tests; Gradle 9 would fail those.
                it.failOnNoDiscoveredTests.set(false)
            }
        }
        // Only build a device-test APK where instrumented tests exist. Empty test APKs for every module
        // cost a parallel D8 run each and pushed connectedDebugAndroidTest out of memory.
        val hasDeviceTests = file("src/androidTest").exists()
        extensions.configure<LibraryAndroidComponentsExtension> {
            beforeVariants { variant ->
                variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.enable = hasDeviceTests
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                optIn.add("kotlin.time.ExperimentalTime")
            }
        }
        dependencies {
            add("testImplementation", catalog.lib("junit"))
        }
    }
}
