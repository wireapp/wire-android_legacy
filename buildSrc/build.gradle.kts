plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
    jcenter()
}

fun applyFrom(filePath: String) {
    if (File(filePath).exists()) apply(from = filePath)
    rootProject
}

applyFrom("local.gradle.kts")
