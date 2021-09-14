plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal() // FIXME
    google()
    mavenCentral()
    jcenter() // FIXME
    maven { url = uri("https://jitpack.io") }
}
