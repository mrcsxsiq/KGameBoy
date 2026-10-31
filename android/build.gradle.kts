plugins {
    id("com.android.application")
}

android {
    namespace = "dev.kgameboy.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.kgameboy"
        minSdk = 26
        targetSdk = 36
        val semver = rootProject.property("kgameboy.version") as String
        val (major, minor, patch) = semver.substringBefore('-').substringBefore('+').split('.').map(String::toInt)
        versionCode = major * 10000 + minor * 100 + patch
        versionName = semver
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
}
