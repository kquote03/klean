plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("klean.hilt")
}
android {
    namespace = "app.klean"
    compileSdk = KleanSdk.COMPILE
    defaultConfig {
        applicationId = "app.klean"
        minSdk = KleanSdk.MIN
        targetSdk = KleanSdk.TARGET
        // Commit count: every CI/nightly APK installs as an upgrade over the previous one.
        versionCode = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionName = "0.1.0-$versionCode-${gitOutput("rev-parse", "--short", "HEAD") ?: "dev"}"
        testInstrumentationRunner = "app.klean.HiltTestRunner"
    }
    signingConfigs {
        // CI passes the shared debug key explicitly, so nightly APKs install over local debug builds.
        getByName("debug") {
            providers.environmentVariable("KLEAN_DEBUG_KEYSTORE").orNull?.let { storeFile = file(it) }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all { it.jvmArgs(KleanSdk.ROBOLECTRIC_JVM_ARGS) }
}
dependencies {
    implementation(project(":shared"))
    listOf("common", "design", "data", "ui", "notifications", "ai", "capture", "sync").forEach { implementation(project(":core:$it")) }
    listOf("diet", "onboarding", "settings", "tasks", "reminders", "calendar", "contacts", "workout", "sleep").forEach { implementation(project(":feature:$it")) }
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navsuite)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material.icons)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.serialization.json)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.datastore.preferences)
    androidTestImplementation(libs.work.testing)
}

// ForbiddenCopyTest reads every module's res/ and manifest; declare them so the test is never stale.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootDir) {
            include("**/src/main/res/**/*.xml", "**/src/main/AndroidManifest.xml")
            exclude("**/build/**", ".gradle/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("userFacingResources")
    // NoRawColorsTest reads every module's Kotlin sources.
    inputs.files(
        fileTree(rootDir) {
            include("**/src/main/**/*.kt")
            exclude("**/build/**", ".gradle/**", "web/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("mainKotlinSources")
}

fun gitOutput(vararg args: String): String? = providers.exec {
    commandLine("git", *args)
    isIgnoreExitValue = true
}.standardOutput.asText.orNull?.trim()?.takeIf { it.isNotEmpty() }
