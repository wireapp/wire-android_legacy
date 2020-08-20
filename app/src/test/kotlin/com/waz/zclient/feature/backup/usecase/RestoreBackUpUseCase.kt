package com.waz.zclient.feature.backup.usecase

import com.waz.model.UserId
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.encryption.EncryptionHandler
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.zip.ZipHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.io.File
import java.util.UUID

@ExperimentalCoroutinesApi
class RestoreBackUpUseCaseTest : UnitTest() {
    private lateinit var restoreBackUpUseCase: RestoreBackUpUseCase

    private val testCoroutineScope = TestCoroutineScope()

    private val userId = UserId.apply(UUID.randomUUID().toString())
    private val password = "password"
    private val metadataFile = File(MetaDataHandler.FILENAME) // a mock file, don't create it
    private val zipFile = File("mocked.zip")
    private val encryptedFile = File("file_encrypted.zip")

    @Test
    fun `given an encrypted and zipped file, when it's unpacked, then call restore on all backup repositories`() {
        runBlocking {
            val encryptionHandler = mockEncryptionHandler(true)
            val zipHandler = mockZipHandler(true)
            val metaDataHandler = mockMetaDataHandler(true)
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verify(metaDataHandler).checkMetaData(metadataFile, userId)
            verify(repo1).restoreBackup()
            verify(repo2).restoreBackup()
            verify(repo3).restoreBackup()

            assert(result.isRight)
        }
    }

    @Test
    fun `given an encrypted and zipped file, when decryption fails, then do not try next steps and return a failure`() {
        runBlocking {
            val encryptionHandler = mockEncryptionHandler(false)
            val zipHandler = mockZipHandler()
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verifyNoInteractions(zipHandler)
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            assert(result.isLeft)
        }
    }

    @Test
    fun `given an encrypted and zipped file, when unzipping fails, then do not try next steps and return a failure`() {
        runBlocking {
            val encryptionHandler = mockEncryptionHandler()
            val zipHandler = mockZipHandler(unzipSuccess = false)
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            assert(result.isLeft)
        }
    }

    @Test
    fun `given an encrypted and zipped file, when there's no metadata file, then do not try next steps and return a failure`() {
        runBlocking {
            val encryptionHandler = mockEncryptionHandler()
            val zipHandler = mockZipHandler(unzipSuccess = true, hasMetadata = false)
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            assert(result.isLeft)
        }
    }

    @Test
    fun `given an encrypted and zipped file, when metadata is wrong, then do not try next steps and return a failure`() {
        runBlocking {
            val encryptionHandler = mockEncryptionHandler()
            val zipHandler = mockZipHandler()
            val metaDataHandler = mockMetaDataHandler(false)
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo()
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verify(metaDataHandler).checkMetaData(metadataFile, userId)
            verifyNoInteractions(repo1)
            verifyNoInteractions(repo2)
            verifyNoInteractions(repo3)

            assert(result.isLeft)
        }
    }

    @Test
    fun `given an encrypted and zipped file, when one of the repositories fails at restoration, then return a failure`() {
        runBlocking {
            val encryptionHandler = mockEncryptionHandler()
            val zipHandler = mockZipHandler()
            val metaDataHandler = mockMetaDataHandler()
            val repo1 = mockBackUpRepo()
            val repo2 = mockBackUpRepo(false)
            val repo3 = mockBackUpRepo()

            restoreBackUpUseCase = RestoreBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                testCoroutineScope
            )

            val result = restoreBackUpUseCase.run(RestoreBackUpUseCaseParams(encryptedFile, userId, password))

            verify(encryptionHandler).decrypt(encryptedFile, userId, password)
            verify(zipHandler).unzip(zipFile)
            verify(metaDataHandler).checkMetaData(metadataFile, userId)
            verify(repo1).restoreBackup()
            verify(repo2).restoreBackup()
            verify(repo3).restoreBackup()

            assert(result.isLeft)
        }
    }

    private suspend fun mockBackUpRepo(backUpSuccess: Boolean = true): BackUpRepository<List<File>> = mock(BackUpRepository::class.java).also {
        `when`(it.restoreBackup()).thenReturn(
            if (backUpSuccess) Either.Right(Unit)
            else Either.Left(DatabaseError)
        )
    } as BackUpRepository<List<File>>

    private fun mockZipHandler(unzipSuccess: Boolean = true, hasMetadata: Boolean = true): ZipHandler = mock(ZipHandler::class.java).also {
        `when`(it.unzip(any())).thenReturn(
            if (unzipSuccess) {
                val files = (1 .. 3).map { createTempFile(suffix = ".json") }
                Either.Right(if (hasMetadata) files + File(MetaDataHandler.FILENAME) else files)
            } else {
                Either.Left(FakeZipFailure)
            }
        )
    }

    private fun mockEncryptionHandler(decryptionSuccess: Boolean = true): EncryptionHandler = mock(EncryptionHandler::class.java).also {
        `when`(it.decrypt(any(), any(), anyString())).thenReturn(
            if (decryptionSuccess) Either.Right(zipFile)
            else Either.Left(FakeEncryptionFailure)
        )
    }

    private fun mockMetaDataHandler(metaDataSuccess: Boolean = true): MetaDataHandler = mock(MetaDataHandler::class.java).also {
        `when`(it.checkMetaData(any(), any())).thenReturn(
            if (metaDataSuccess) Either.Right(Unit)
            else Either.Left(FakeMetaDataFailure)
        )
    }

    object FakeZipFailure : FeatureFailure()
    object FakeEncryptionFailure: FeatureFailure()
    object FakeMetaDataFailure: FeatureFailure()
}
