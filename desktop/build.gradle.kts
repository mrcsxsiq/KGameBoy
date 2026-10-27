plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

// ./gradlew :desktop:run -Prom=roms/dmg-acid2.gb [-Pframes=120 -Pscreenshot=out.png]
compose.desktop {
    application {
        mainClass = "dev.kgameboy.desktop.MainKt"
        jvmArgs += listOf("-Xdock:name=KGameBoy", "-Dapple.awt.application.name=KGameBoy")
        (project.findProperty("rom") as String?)?.let { args += rootProject.file(it).absolutePath }
        (project.findProperty("frames") as String?)?.let { args += listOf("--frames", it) }
        (project.findProperty("screenshot") as String?)?.let { args += listOf("--screenshot", rootProject.file(it).absolutePath) }

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "KGameBoy"
            packageVersion = "1.0.0"
        }
    }
}
