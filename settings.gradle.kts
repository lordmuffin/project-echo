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

rootProject.name = "ProjectEcho"

// Main Application Modules    
include(":app")
include(":app:phone")
include(":app:watch")

// Core Modules
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:ui")
include(":core:domain")
include(":core:data")
include(":core:wearable")

// Feature Modules
include(":features:audio")
include(":features:permissions")

// Test Modules
include(":core:testing")