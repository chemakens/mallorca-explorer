pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mallorca-explorer"

include(":app")
include(":core:core-common")
include(":core:core-data")
include(":core:core-domain")
include(":core:core-ui")
include(":feature:feature-map")
include(":feature:feature-explore")
include(":feature:feature-place")
include(":feature:feature-itinerary")
include(":feature:feature-trips")
include(":feature:feature-favorites")
