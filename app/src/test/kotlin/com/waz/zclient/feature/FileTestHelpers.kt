package com.waz.zclient.feature

import java.io.File
import java.util.Base64
import kotlin.random.Random

fun generateText(length: Int): String = Base64.getEncoder().encodeToString(Random.Default.nextBytes(length))

fun createTempDir(): File = File.createTempFile("temp", System.currentTimeMillis().toString()).apply {
    delete()
    mkdirs()
    deleteOnExit()
}

fun uniqueTextFileName(): String {
    Thread.sleep(1)
    return "ZipHandlerTest_${System.currentTimeMillis()}.txt"
}

fun uniqueZipFileName(): String {
    Thread.sleep(1)
    return "ZipHandlerTest_${System.currentTimeMillis()}.zip"
}

fun createTextFile(dir: File, length: Int = 100): File =
    File(dir, uniqueTextFileName()).apply {
        bufferedWriter().use { it.write(generateText(length)) }
    }