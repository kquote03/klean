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
        image = "klean-backend"
        tags = setOf("dev")
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
