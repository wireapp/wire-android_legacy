package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.crypto.decryption.DecryptionHandler
import com.waz.zclient.feature.backup.metadata.BackupMetaData
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.zip.ZipHandler
import com.waz.zclient.framework.functional.assertLeft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.amshove.kluent.shouldEqual
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.anyString
import java.io.File
import java.util.UUID

@ExperimentalCoroutinesApi
class RestoreBackUpUseCaseTest : UnitTest() {

    private lateinit var restoreBackUpUseCase: RestoreBackUpUseCase

    private val testCoroutineScope = TestCoroutineScope()

    @Test
    fun `given an encrypted and zipped file, when it's unpacked, then call restore on all backup repositories`() {
        runBlocking {
            val decryptionHandler = mockDecryptionHandler(true)
            val zipHandler = mockZipHandler(true)
            val metaDataHandler = mockMetaDataHandler(true)
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                decryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(decryptionHandler).decryptBackup(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verify(repo1).restoreBackup()
            verify(repo2).restoreBackup()
            verify(repo3).restoreBackup()

            result.onFailure { fail(it.toString()) }
        }
    }

    @Test
    fun `given an encrypted and zipped file, when decryption fails, then do not try next steps and return a failure`() {
        runBlocking {
            val decryptionHandler = mockDecryptionHandler(false)
            val zipHandler = mockZipHandler()
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                decryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(decryptionHandler).decryptBackup(encryptedFile, userId, password)
            verifyNoInteractions(zipHandler)
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            result.assertLeft { it shouldEqual FakeDecryptionFailure }
        }
    }

    @Test
    fun `given an encrypted and zipped file, when unzipping fails, then do not try next steps and return a failure`() {
        runBlocking {
            val decryptionHandler = mockDecryptionHandler()
            val zipHandler = mockZipHandler(unzipSuccess = false)
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                decryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(zipFile, userId, password))

            // verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            result.assertLeft{ it shouldEqual FakeZipFailure }
        }
    }

    @Test
    fun `given an encrypted and zipped file, when there's no metadata file, then do not try next steps and return a failure`() {
        runBlocking {
            val decryptionHandler = mockDecryptionHandler()
            val zipHandler = mockZipHandler(unzipSuccess = true, hasMetadata = false)
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                decryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(zipFile, userId, password))

            verify(zipHandler).unzip(zipFile)
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            result.assertLeft { it shouldEqual NoMetaDataFileFailure }
        }
    }

    @Test
    fun `given an encrypted and zipped file, when metadata is wrong, then do not try next steps and return a failure`() {
        runBlocking {
            val decryptionHandler = mockDecryptionHandler()
            val zipHandler = mockZipHandler()
            val metaDataHandler = mockMetaDataHandler(false)
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                decryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(decryptionHandler).decryptBackup(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verify(metaDataHandler).readMetaData(metadataFile)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            result.assertLeft { it shouldEqual FakeMetaDataFailure }
        }
    }

    @Test
    fun `given an encrypted and zipped file, when one of the repositories fails at restoration, then return a failure`() {
        runBlocking {
            val decryptionHandler = mockDecryptionHandler()
            val zipHandler = mockZipHandler()
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo(false)
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                decryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(decryptionHandler).decryptBackup(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verify(metaDataHandler).readMetaData(metadataFile)
            verify(repo1).restoreBackup()
            verify(repo2).restoreBackup()
            verify(repo3).restoreBackup()

            result.assertLeft { it shouldEqual DatabaseError }
        }
    }

    @Test
    fun `given a valid metadata json, when checkMetaData is called, return success`() {
        val decryptionHandler = mockDecryptionHandler()
        val zipHandler = mockZipHandler()
        val metaDataHandler = mockMetaDataHandler()

        restoreBackUpUseCase = RestoreBackUpUseCase(
            emptyList(),
            zipHandler,
            decryptionHandler,
            metaDataHandler,
            backUpVersion,
            testCoroutineScope
        )

        restoreBackUpUseCase.checkMetaData(metadataFile, userId).onFailure { fail(it.toString()) }
    }

    @Test
    fun `given a valid metadata json, when it is checked and the userId is wrong, return a failure`() {
        val differentUserId = UUID.randomUUID().toString()
        val decryptionHandler = mockDecryptionHandler()
        val zipHandler = mockZipHandler()
        val metaDataHandler = mockMetaDataHandler()

        restoreBackUpUseCase = RestoreBackUpUseCase(
            emptyList(),
            zipHandler,
            decryptionHandler,
            metaDataHandler,
            backUpVersion,
            testCoroutineScope
        )

        restoreBackUpUseCase.checkMetaData(metadataFile, differentUserId)
            .assertLeft { it shouldEqual UserIdInvalid }
    }

    @Test
    fun `given a valid metadata json, when it is checked and the backup version is unhandled, return a failure`() {
        val decryptionHandler = mockDecryptionHandler()
        val zipHandler = mockZipHandler()
        val metaDataHandler = mockMetaDataHandler()

        // We should be able to handle all backup versions older (smaller) than the current one,
        // i.e. the current version should be the newest one.
        // If the version from the backup file is bigger than the current one, it means something's wrong.
        val currentBackupVersion = backUpVersion - 1
        restoreBackUpUseCase = RestoreBackUpUseCase(
            emptyList(),
            zipHandler,
            decryptionHandler,
            metaDataHandler,
            currentBackupVersion,
            testCoroutineScope
        )

        restoreBackUpUseCase.checkMetaData(metadataFile, userId)
            .assertLeft { it shouldEqual UnknownBackupVersion(backUpVersion) }
    }

    companion object {

        private const val FILE_NAME_ZIPPED = "file_zipped.zip"
        private const val FILE_NAME_ENCRYPTED = "file_encrypted.zip"
        private val userId = UUID.randomUUID().toString()
        private val clientId = UUID.randomUUID().toString()
        private const val password = "password"
        private val metadataFile = File(MetaDataHandler.FILE_NAME) // a mock file, don't create it
        private val zipFile = File(FILE_NAME_ZIPPED)
        private val encryptedFile = File(FILE_NAME_ENCRYPTED)
        private const val backUpVersion = 0
        private const val userHandle = "user"

        private val metaData = BackupMetaData(
            userId = userId,
            clientId = clientId,
            userHandle = userHandle,
            backUpVersion = backUpVersion
        )

        private suspend fun mockBackUpRepo(backUpSuccess: Boolean = true): BackUpRepository<List<File>> = mock(BackUpRepository::class.java).also {
            `when`(it.restoreBackup()).thenReturn(
                    if (backUpSuccess) Either.Right(Unit)
                    else Either.Left(DatabaseError)
            )
        } as BackUpRepository<List<File>>

        private fun mockZipHandler(unzipSuccess: Boolean = true, hasMetadata: Boolean = true): ZipHandler = mock(ZipHandler::class.java).also {
            `when`(it.unzip(any())).thenReturn(
                    if (unzipSuccess) {
                        val files = (1..3).map { createTempFile(suffix = ".json") }
                        Either.Right(if (hasMetadata) files + File(MetaDataHandler.FILE_NAME) else files)
                    } else {
                        Either.Left(FakeZipFailure)
                    }
            )
        }

        private fun mockDecryptionHandler(decryptionSuccess: Boolean = true): DecryptionHandler = mock(DecryptionHandler::class.java).also {
            `when`(it.decryptBackup(any(), any(), anyString())).thenReturn(
                    if (decryptionSuccess) Either.Right(zipFile)
                    else Either.Left(FakeDecryptionFailure)
            )
        }

        private fun mockMetaDataHandler(metaDataSuccess: Boolean = true): MetaDataHandler = mock(MetaDataHandler::class.java).also {
            `when`(it.readMetaData(any())).thenReturn(
                    if (metaDataSuccess) Either.Right(metaData)
                    else Either.Left(FakeMetaDataFailure)
            )
        }

        object FakeZipFailure : FeatureFailure()
        object FakeDecryptionFailure : FeatureFailure()
        object FakeMetaDataFailure : FeatureFailure()
    }
}
