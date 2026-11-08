import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

/**
 * Lets Robolectric tests use `BundledSQLiteDriver`. The Android artifact calls
 * `System.loadLibrary("sqliteJni")`, which finds nothing on the host JVM. The JVM artifact ships the
 * same JNI library for desktop platforms, so we extract the linux x86_64 build and put it on
 * `java.library.path` for unit tests. Production code is unchanged.
 */
class SqliteJvmTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val natives = configurations.create("sqliteJvmNatives") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            }
        }
        dependencies.add(natives.name, catalog.lib("sqlite-bundled-jvm"))
        val archives = objects.newInstance(ArchiveOps::class.java).archives
        val outDir = layout.buildDirectory.dir("sqlite-natives")
        val extract = tasks.register<Sync>("extractSqliteNatives") {
            from(natives.elements.map { files -> files.map { archives.zipTree(it.asFile) } }) {
                include("natives/linux_x64/**")
                eachFile { path = name }
            }
            includeEmptyDirs = false
            into(outDir)
        }
        tasks.withType<Test>().configureEach {
            dependsOn(extract)
            systemProperty("java.library.path", outDir.get().asFile.absolutePath)
        }
    }
}

abstract class ArchiveOps @Inject constructor(val archives: ArchiveOperations)
