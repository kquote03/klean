// Klean sync backend (M24): Ktor 3 server on CIO, Exposed + JDBC (SQLite default, Postgres optional),
// packaged as the klean-server container image by the repo-root Dockerfile (docs/deploy.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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

// Reproducible jars (the server Dockerfile copies installDist's output): no file timestamps, fixed order.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// The web client (M26): `pnpm build` in web/ → web/dist, packaged into the jar under web/ and served at /
// (app.klean.backend.web.WebRoutes). klean.web:
//   auto      (default) build it when pnpm is on PATH, else leave it out (the backend then answers 404 at /)
//   require   build it; fail without pnpm
//   prebuilt  package an existing web/dist without running pnpm (the Dockerfile builds it in its own stage)
//   false     leave it out
val webDir = rootProject.layout.projectDirectory.dir("web")
val pnpmOnPath = providers.environmentVariable("PATH").map { path ->
    path.split(File.pathSeparator).any { File(it, "pnpm").canExecute() }
}.orElse(false)
val webMode = providers.gradleProperty("klean.web").orElse("auto").get()
val runPnpm = when (webMode) {
    "false", "prebuilt" -> false
    "require" -> {
        check(pnpmOnPath.get()) { "-Pklean.web=require but pnpm is not on PATH (use nix develop .#web)" }
        check(webDir.file("package.json").asFile.exists()) { "-Pklean.web=require but web/package.json is missing" }
        true
    }
    "auto" -> pnpmOnPath.get() && webDir.file("package.json").asFile.exists()
    else -> error("klean.web must be auto, require, prebuilt or false (was '$webMode')")
}
if (webMode == "prebuilt") {
    check(webDir.file("dist/index.html").asFile.exists()) { "-Pklean.web=prebuilt but web/dist/index.html is missing" }
}
val packageWeb = runPnpm || webMode == "prebuilt"

val pnpmBuild by tasks.registering(Exec::class) {
    description = "Builds the web client (web/dist) with pnpm."
    group = "build"
    enabled = runPnpm
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
    if (packageWeb) {
        if (runPnpm) dependsOn(pnpmBuild)
        from(webDir.dir("dist")) { into("web") }
    }
}
