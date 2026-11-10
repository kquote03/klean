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

// Release images build for amd64+arm64 (-Pklean.image.platforms=linux/amd64,linux/arm64, registry push only);
// local jibDockerBuild/jibBuildTar keep one platform because Jib can't load a multi-platform image into Docker.
val imagePlatforms = providers.gradleProperty("klean.image.platforms").orElse("linux/amd64").get()
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
val imageRevision = providers.gradleProperty("klean.image.revision").orElse("dev").get()

jib {
    from {
        // Pinned by digest so the same sources always give the same image. Bump: crane digest eclipse-temurin:21-jre
        image = "eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a"
        platforms {
            imagePlatforms.forEach { p ->
                val (os, arch) = p.split("/")
                platform { this.os = os; architecture = arch }
            }
        }
    }
    to {
        // CI overrides with -Djib.to.image=ghcr.io/kquote03/klean-backend:<version> and -Djib.to.tags=…
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
        // creationTime left at Jib's default (epoch) so builds are reproducible.
        labels.set(
            mapOf(
                "org.opencontainers.image.source" to "https://github.com/kquote03/klean",
                "org.opencontainers.image.title" to "klean-backend",
                "org.opencontainers.image.description" to "Klean sync server with the Klean web app at /",
                "org.opencontainers.image.revision" to imageRevision,
            ),
        )
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
// klean.web: auto (default: build when pnpm is on PATH), require (release images: fail without pnpm), false (skip).
val webMode = providers.gradleProperty("klean.web").orElse("auto").get()
val buildWeb = when (webMode) {
    "false" -> false
    "require" -> {
        check(pnpmOnPath.get()) { "-Pklean.web=require but pnpm is not on PATH (use nix develop .#web)" }
        check(webDir.file("package.json").asFile.exists()) { "-Pklean.web=require but web/package.json is missing" }
        true
    }
    "auto" -> pnpmOnPath.get() && webDir.file("package.json").asFile.exists()
    else -> error("klean.web must be auto, require or false (was '$webMode')")
}

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
