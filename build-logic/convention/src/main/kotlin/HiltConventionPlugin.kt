import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.dependencies

/** A lease-only build service: holding it means "a KSP task is running". */
abstract class KspSerialService : BuildService<BuildServiceParameters.None>

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", catalog.lib("hilt-android"))
            add("ksp", catalog.lib("hilt-compiler"))
        }
        // KSP2 runs inside the Gradle daemon (noIsolation workers, one cached classloader shared by
        // every KSP task). When two KSP tasks run at once (configuration cache = parallel tasks),
        // one can see library jars as empty: Hilt then fails with "'DevicePrefs' could not be
        // resolved", or silently emits partial aggregated deps and :app:hiltJavaCompileDebug reports
        // missing bindings. Rerunning every KSP task concurrently failed 4 builds out of 8 locally
        // (CI run 42612864484). One KSP task at a time across the whole build; each takes seconds.
        val kspSerial = gradle.sharedServices.registerIfAbsent("kspSerial", KspSerialService::class.java) {
            maxParallelUsages.set(1)
        }
        // A cached KSP result doesn't restore the generated Java (Hilt components). On a fresh
        // checkout (CI) javac then sees NO-SOURCE and Robolectric Hilt tests fail with
        // ClassNotFoundException for *_TestComponentDataSupplier. KSP is fast; never cache it.
        tasks.matching { it.name.startsWith("ksp") }.configureEach {
            outputs.cacheIf { false }
            usesService(kspSerial)
        }
    }
}
