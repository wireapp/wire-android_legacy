package com.waz.zclient.feature.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waz.model.UserId
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import com.waz.zclient.feature.backup.encryption.EncryptionHandler
import com.waz.zclient.feature.backup.encryption.EncryptionHandlerDataSource
import org.amshove.kluent.shouldEqual
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class EncryptionHandlerTest {

    @Test
    fun given_a_file_when_encrypted_and_decrypted_with_password_and_userId_then_decrypted_contents_are_the_same_as_original() {
        val tempDir = createTempDir()
        val textFile = createTextFile(tempDir)
        val originalContents = textFile.readText()
        val password = generateText(8)
        val userId = UserId.apply()

        val encryptionHandler: EncryptionHandler = EncryptionHandlerDataSource()

        when (val res1 = encryptionHandler.encrypt(textFile, userId, password)) {
            is Left -> fail(res1.a.toString())
            is Right -> {
                val encryptedFile = res1.b
                println(encryptedFile.absolutePath)
                when (val res2 = encryptionHandler.decrypt(encryptedFile, userId, password)) {
                    is Left -> fail(res2.a.toString())
                    is Right -> {
                        val decryptedFile = res2.b
                        val unzippedContents = decryptedFile.readText()
                        unzippedContents shouldEqual originalContents
                    }
                }
            }
        }
    }

    private fun createTextFile(dir: File, length: Int = 100): File =
        File(dir, uniqueTextFileName()).apply {
            bufferedWriter().use { it.write(generateText(length)) }
        }

    private fun generateText(length: Int): String = Base64.getEncoder().encodeToString(Random.Default.nextBytes(length))

    private fun createTempDir(): File = File.createTempFile("temp", System.currentTimeMillis().toString()).apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

    private fun uniqueTextFileName(): String {
        Thread.sleep(1)
        return "ZipHandlerTest_${System.currentTimeMillis()}.txt"
    }
}