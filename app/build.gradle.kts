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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "app.klean.HiltTestRunner"
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
    listOf("common", "design", "data", "ui", "notifications", "ai", "capture").forEach { implementation(project(":core:$it")) }
    listOf("diet", "onboarding", "settings").forEach { implementation(project(":feature:$it")) }
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navsuite)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material.icons)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.serialization.json)
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
}

// ForbiddenCopyTest reads every module's res/ and manifest; declare them so the test is never stale.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootDir) {
            include("**/src/main/res/**/*.xml", "**/src/main/AndroidManifest.xml")
            exclude("**/build/**", ".gradle/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("userFacingResources")
}
