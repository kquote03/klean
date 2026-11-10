// Klean sync backend (M24): Ktor 3 server on CIO, Exposed + JDBC (SQLite default, Postgres optional),
// packaged as a container image with Jib (`./gradlew :backend:jibDockerBuild` → klean-backend:dev).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jib)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

application {
    mainClass.set("app.klean.backend.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle.prov)
    implementation(libs.logback.classic)
    implementation(libs.logstash.encoder)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    // Integration tests each open their own SQLite file under build/tmp; keep forks bounded (14 GB box).
    maxParallelForks = 1
    systemProperty("klean.test.tmp", layout.buildDirectory.dir("tmp/backend-tests").get().asFile.absolutePath)
}

jib {
    from {
        image = "eclipse-temurin:21-jre"
    }
    to {
        // Override for CI publishing: -Djib.to.image=ghcr.io/kquote03/klean-backend:<tag>
        image = "klean-backend:dev"
    }
    container {
        mainClass = "app.klean.backend.MainKt"
        ports = listOf("8080")
        volumes = listOf("/data")
        environment = mapOf(
            "KLEAN_DATA_DIR" to "/data",
            "KLEAN_PORT" to "8080",
        )
        jvmFlags = listOf("-XX:MaxRAMPercentage=75")
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}

// Jib 3.5 reads Task.project at execution time; let Gradle fall back to a non-cached configuration for it.
tasks.matching { it.name.startsWith("jib") }.configureEach {
    notCompatibleWithConfigurationCache("Jib uses Task.project at execution time")
}

// The web client (M26): `pnpm build` in web/ → web/dist, packaged into the jar under web/ and served at /
// (app.klean.backend.web.WebRoutes). Skipped when web/ or pnpm is missing (the Android CI job, a plain JDK
// box) or with -Pklean.web=false; the backend then answers 404 at / and nothing else changes.
val webDir = rootProject.layout.projectDirectory.dir("web")
val pnpmOnPath = providers.environmentVariable("PATH").map { path ->
    path.split(File.pathSeparator).any { File(it, "pnpm").canExecute() }
}.orElse(false)
val webEnabled = providers.gradleProperty("klean.web").map { it != "false" }.orElse(true)
val buildWeb = webEnabled.get() && pnpmOnPath.get() && webDir.file("package.json").asFile.exists()

val pnpmBuild by tasks.registering(Exec::class) {
    description = "Builds the web client (web/dist) with pnpm."
    group = "build"
    enabled = buildWeb
    workingDir = webDir.asFile
    commandLine("sh", "-c", "pnpm install --frozen-lockfile --prefer-offline && pnpm build")
    inputs.files(fileTree(webDir) {
        include("src/**", "public/**", "scripts/**", "index.html", "package.json", "pnpm-lock.yaml", "tsconfig.json", "vite.config.ts")
        exclude("src/generated/**", "public/icon*.svg")
    }).withPropertyName("webSources").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("core/design/tokens/klean-color-tokens.json")).withPropertyName("tokens")
    inputs.files(
        rootProject.layout.projectDirectory.dir("core/design/src/main/res/font").asFileTree,
        rootProject.layout.projectDirectory.file("app/src/main/res/drawable/ic_launcher_foreground.xml"),
        rootProject.layout.projectDirectory.file("app/src/main/res/drawable/ic_launcher_background.xml"),
    ).withPropertyName("sharedAssets").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(webDir.dir("dist"))
}

tasks.processResources {
    if (buildWeb) {
        dependsOn(pnpmBuild)
        from(webDir.dir("dist")) { into("web") }
    }
}
