plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.rezzo.academy.scene3d"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Filament: рендер (PBR) и загрузка glTF/GLB
    api("com.google.android.filament:filament-android:1.71.4")
    api("com.google.android.filament:gltfio-android:1.71.4")
    implementation("androidx.core:core-ktx:1.13.1")
}
