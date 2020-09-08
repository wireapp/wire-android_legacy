package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.crypto.encryption.EncryptionHandler
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.zip.ZipHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.io.File
import java.util.UUID

@ExperimentalCoroutinesApi
class CreateBackUpUseCaseTest : UnitTest() {

    private lateinit var createBackUpUseCase: CreateBackUpUseCase

    private val testCoroutineScope = TestCoroutineScope()

    @Test
    fun `given back up repositories and metadata, when all of them succeed, then zip, encrypt, and return success`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)
            val zipHandler = mockZipHandler(true)
            val encryptionHandler = mockEncryptionHandler(true)
            val metaDataHandler = mockMetaDataHandler(true)

            createBackUpUseCase = CreateBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(params)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(any())
            verify(zipHandler).zip(anyString(), anyList())
            verify(encryptionHandler).encryptBackup(any(), any(), anyString(), anyString())

            assertTrue(result.isRight)
        }
    }

    @Test
    fun `given back up repositories, when one of them fails, then do not execute others and return a failure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(false)
            val repo3 = mockBackUpRepo(true)
            val zipHandler = mockZipHandler(true)
            val encryptionHandler = mockEncryptionHandler(true)
            val metaDataHandler = mockMetaDataHandler(true)

            createBackUpUseCase = CreateBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(params)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            // backups are saved asynchronously so there might be some interactions with repo3
            verifyNoInteractions(metaDataHandler)
            verifyNoInteractions(zipHandler)
            verifyNoInteractions(encryptionHandler)

            assertEquals(Either.Left(DatabaseError), result)
        }
    }

    @Test
    fun `given back up repositories and metadata, when they succeed but the zip handler fails, then return a failure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)
            val zipHandler = mockZipHandler(false)
            val encryptionHandler = mockEncryptionHandler(true)
            val metaDataHandler = mockMetaDataHandler(true)

            createBackUpUseCase = CreateBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(params)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(any()) // metadata is generated before zipping
            verify(zipHandler).zip(anyString(), anyList())
            verifyNoInteractions(encryptionHandler)

            assertEquals(Either.Left(FakeZipFailure), result)
        }
    }

    @Test
    fun `given back up repositories, when they succeed but the zip handler fails, then return a failure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)
            val zipHandler = mockZipHandler(false)
            val encryptionHandler = mockEncryptionHandler(true)
            val metaDataHandler = mockMetaDataHandler(true)

            createBackUpUseCase = CreateBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(params)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(any()) // metadata is generated before zipping
            verify(zipHandler).zip(anyString(), anyList())
            verifyNoInteractions(encryptionHandler)

            assertEquals(Either.Left(FakeZipFailure), result)
        }
    }

    @Test
    fun `given back up repositories and metadata, when they succeed but the encryption handler fails, then return a failure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)
            val zipHandler = mockZipHandler(true)
            val encryptionHandler = mockEncryptionHandler(false)
            val metaDataHandler = mockMetaDataHandler(true)

            createBackUpUseCase = CreateBackUpUseCase(
                listOf(repo1, repo2, repo3),
                zipHandler,
                encryptionHandler,
                metaDataHandler,
                backUpVersion,
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(params)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(any())
            verify(zipHandler).zip(anyString(), anyList())
            verify(encryptionHandler).encryptBackup(any(), any(), anyString(), anyString())

            assertEquals(Either.Left(FakeEncryptionFailure), result)
        }
    }

    @Test
    fun `given back up repositories and metadata, when metadata fails, then return a failure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)
            val zipHandler = mockZipHandler(true)
            val encryptionHandler = mockEncryptionHandler(true)
            val metaDataHandler = mockMetaDataHandler(false)

            createBackUpUseCase = CreateBackUpUseCase(
                    listOf(repo1, repo2, repo3),
                    zipHandler,
                    encryptionHandler,
                    metaDataHandler,
                    backUpVersion,
                    testCoroutineScope
            )

            val result = createBackUpUseCase.run(params)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(any())
            verifyNoInteractions(zipHandler)
            verifyNoInteractions(encryptionHandler)

            assertEquals(Either.Left(FakeMetaDataFailure), result)
        }
    }

    companion object {

        private val userId = UUID.randomUUID().toString()
        private const val userHandle = "user"
        private const val password = "password"
        private const val backUpVersion = 0
        private const val platform = "Android"
        private val clientId = UUID.randomUUID().toString()
        private const val version = "3.54"
        private const val creationTime = "2020-09-08T10:00:00.000Z"

        private val params = CreateBackUpUseCaseParams(userId, clientId, userHandle, password)

        suspend fun mockBackUpRepo(backUpSuccess: Boolean = true): BackUpRepository<List<File>> = mock(BackUpRepository::class.java).also {
            `when`(it.saveBackup()).thenReturn(
                    if (backUpSuccess) Either.Right(listOf(createTempFile(suffix = ".json")))
                    else Either.Left(DatabaseError)
            )
        } as BackUpRepository<List<File>>

        fun mockZipHandler(zipSuccess: Boolean = true): ZipHandler = mock(ZipHandler::class.java).also {
            `when`(it.zip(anyString(), anyList())).thenReturn(
                if (zipSuccess) Either.Right(createTempFile(suffix = ".zip"))
                else Either.Left(FakeZipFailure)
            )
        }

        fun mockEncryptionHandler(encryptionSuccess: Boolean = true): EncryptionHandler = mock(EncryptionHandler::class.java).also {
            `when`(it.encryptBackup(any(), any(), anyString(), anyString())).thenReturn(
                if (encryptionSuccess) Either.Right(createTempFile(suffix = "_encrypted"))
                else Either.Left(FakeEncryptionFailure)
            )
        }

        fun mockMetaDataHandler(metaDataSuccess: Boolean = true): MetaDataHandler = mock(MetaDataHandler::class.java).also {
            `when`(it.generateMetaDataFile(any())).thenReturn(
                if (metaDataSuccess) Either.Right(createTempFile(suffix = ".json"))
                else Either.Left(FakeMetaDataFailure)
            )
        }

        object FakeZipFailure : FeatureFailure()
        object FakeEncryptionFailure: FeatureFailure()
        object FakeMetaDataFailure: FeatureFailure()
    }
}
