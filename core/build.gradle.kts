import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(17)
    jvm()

    // Browser build. Tests run under Node.js, which supports WasmGC.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
