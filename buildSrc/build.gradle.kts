plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal() // FIXME
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
