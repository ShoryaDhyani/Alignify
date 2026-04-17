import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    val localProperties = Properties()
    val localPropertiesFile = file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    val mapboxDownloadsToken =
        providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN").orNull
            ?: localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN")
            ?: providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        if (!mapboxDownloadsToken.isNullOrBlank() && mapboxDownloadsToken != "YOUR_MAPBOX_SECRET_TOKEN") {
            maven {
                url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
                credentials.username = "mapbox"
                credentials.password = mapboxDownloadsToken
                authentication.create<BasicAuthentication>("basic")
            }
        }
    }
}

rootProject.name = "Alighnify"
include(":app")
