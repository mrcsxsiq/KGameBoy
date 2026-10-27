plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("dev.kgameboy.desktop.MainKt")
    applicationDefaultJvmArgs = listOf("-Xdock:name=KGameBoy", "-Dapple.awt.application.name=KGameBoy")
}

// ./gradlew :desktop:run -Prom=roms/dmg-acid2.gb [-Pframes=120 -Pscreenshot=out.png]
tasks.named<JavaExec>("run") {
    val extra = mutableListOf<String>()
    (project.findProperty("rom") as String?)?.let { extra += rootProject.file(it).absolutePath }
    (project.findProperty("frames") as String?)?.let { extra += listOf("--frames", it) }
    (project.findProperty("screenshot") as String?)?.let { extra += listOf("--screenshot", rootProject.file(it).absolutePath) }
    args = extra
    standardInput = System.`in`
}
