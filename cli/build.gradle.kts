plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The CLI reuses the desktop window, headless renderer, audio and save stores.
    implementation(project(":desktop"))
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass = "dev.kgameboy.cli.MainKt"
    applicationName = "kgameboy"
    applicationDefaultJvmArgs = listOf("-Xdock:name=KGameBoy", "-Dapple.awt.application.name=KGameBoy")
}

// CLI version, exposed to the code through a filtered resource (version.properties).
version = "0.1.0"
tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("version.properties") { expand("version" to v) }
}

tasks.test { useJUnitPlatform() }
