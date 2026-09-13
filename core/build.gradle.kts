plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// :core is deliberately a PURE Kotlin/JVM module.
//
// Every game rule — rep detection, depth grading, combat maths, progression, balance — lives here
// so it can be unit tested on a plain JVM with no emulator, no camera and no Android SDK.
// Do not add an Android dependency to this module; `PoseFrame` takes plain floats precisely so
// that MediaPipe types never leak in.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
