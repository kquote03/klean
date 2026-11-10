pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "klean"
// -Pklean.serverOnly=true (the server Dockerfile): only what the server needs, so no Android SDK is involved.
val serverOnly = providers.gradleProperty("klean.serverOnly").orNull == "true"
if (serverOnly) {
    include(":shared", ":backend")
} else {
include(
    ":app", ":shared",
    ":core:common", ":core:design", ":core:data", ":core:ui",
    ":core:notifications", ":core:ai", ":core:capture", ":core:sync",
    ":feature:diet", ":feature:onboarding", ":feature:settings",
    ":feature:tasks", ":feature:reminders", ":feature:calendar", ":feature:contacts", ":feature:workout", ":feature:sleep",
)
include(":backend")
}
