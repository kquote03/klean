plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    android {
        namespace = "app.klean.shared"
        compileSdk = 37
        minSdk = 28
    }
    jvm()
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.datetime)
            api(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
