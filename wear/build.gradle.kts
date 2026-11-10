// Wear OS app (M27): tiles, a food-log complication and the Data Layer codec. min SDK 30. The watch never
// talks to the backend; it gets a WatchState snapshot from the phone and sends WatchMessages back.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "app.klean.wear"
    compileSdk = 37 // = KleanSdk.COMPILE (build-logic isn't on this script's classpath without a klean plugin)
    defaultConfig {
        // Same id as the phone, which the Data Layer requires for pairing.
        applicationId = "app.klean"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { isMinifyEnabled = false }
    }
    signingConfigs {
        // Same key as the phone (CI passes it): the Data Layer pairs the two apps by package AND signature.
        getByName("debug") {
            providers.environmentVariable("KLEAN_DEBUG_KEYSTORE").orNull?.let { storeFile = file(it) }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        // Robolectric (SDK 36 sandbox) on JDK 21; same as KleanSdk.ROBOLECTRIC_JVM_ARGS.
        it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        // WearColorsTest reads the phone's colour tokens.
        it.inputs.file(rootProject.file("core/design/tokens/klean-color-tokens.json")).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("colorTokens")
    }
}
dependencies {
    // WearProtocol (paths, WatchState, WatchMessage, WearCodec) is shared with the phone.
    implementation(project(":shared"))
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.wear.input)
    implementation(libs.play.services.wearable)
    implementation(libs.concurrent.futures)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.wear.tiles.testing)
}
