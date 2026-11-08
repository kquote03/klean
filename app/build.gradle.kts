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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    testImplementation(libs.junit)
}
