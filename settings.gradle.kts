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

rootProject.name = "RezzoAcademy"

// :core    — модели данных, база знаний (JSON), Room, DataStore
// :scene3d — 3D-движок (Filament + glTF), без интерфейса
// :app     — интерфейс (Jetpack Compose), навигация, экраны
include(":app", ":core", ":scene3d")
