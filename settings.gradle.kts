pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "klean"
include(
    ":app", ":shared",
    ":core:common", ":core:design", ":core:data", ":core:ui",
    ":core:notifications", ":core:ai", ":core:capture",
    ":feature:diet", ":feature:onboarding", ":feature:settings",
    ":feature:tasks", ":feature:reminders", ":feature:calendar", ":feature:contacts", ":feature:workout", ":feature:sleep",
)
include(":backend")
