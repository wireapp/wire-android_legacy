package com.waz.zclient.feature.backup.usecase

import com.waz.model.UserId
import com.waz.zclient.UnitTest
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
import org.junit.Assert.assertEquals
import org.junit.Test
import com.waz.zclient.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.anyList
import org.mockito.Mockito.mock
import java.io.File
import java.util.UUID

@ExperimentalCoroutinesApi
class CreateBackUpUseCaseTest : UnitTest() {

    private lateinit var createBackUpUseCase: CreateBackUpUseCase

    private val testCoroutineScope = TestCoroutineScope()

    private val userId = UserId.apply(UUID.randomUUID().toString())
    private val userHandle = "user"
    private val password = "password"

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
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(CreateBackUpUseCaseParams(userId, userHandle, password))

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(userId, userHandle)
            verify(zipHandler).zip(anyString(), anyList())
            verify(encryptionHandler).encrypt(any(), any(), anyString())

            assert(result.isRight)
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
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(CreateBackUpUseCaseParams(userId, userHandle, password))

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
                    testCoroutineScope
            )

            val result = createBackUpUseCase.run(CreateBackUpUseCaseParams(userId, userHandle, password))

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(userId, userHandle) // metadata is generated before zipping
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
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(CreateBackUpUseCaseParams(userId, userHandle, password))

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(userId, userHandle) // metadata is generated before zipping
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
                testCoroutineScope
            )

            val result = createBackUpUseCase.run(CreateBackUpUseCaseParams(userId, userHandle, password))

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(userId, userHandle)
            verify(zipHandler).zip(anyString(), anyList())
            verify(encryptionHandler).encrypt(any(), any(), anyString())

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
                    testCoroutineScope
            )

            val result = createBackUpUseCase.run(CreateBackUpUseCaseParams(userId, userHandle, password))

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()
            verify(metaDataHandler).generateMetaDataFile(userId, userHandle)
            verifyNoInteractions(zipHandler)
            verifyNoInteractions(encryptionHandler)

            assertEquals(Either.Left(FakeMetaDataFailure), result)
        }
    }

    companion object {
        /**
         * Returns Mockito.any() as nullable type to avoid java.lang.IllegalStateException when null is returned.
         * Taken from https://stackoverflow.com/a/48091649/2975925
         */
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
            `when`(it.encrypt(any(), any(), anyString())).thenReturn(
                if (encryptionSuccess) Either.Right(createTempFile(suffix = "_encrypted"))
                else Either.Left(FakeEncryptionFailure)
            )
        }

        fun mockMetaDataHandler(metaDataSuccess: Boolean = true): MetaDataHandler = mock(MetaDataHandler::class.java).also {
            `when`(it.generateMetaDataFile(any(), anyString())).thenReturn(
                if (metaDataSuccess) Either.Right(createTempFile(suffix = ".json"))
                else Either.Left(FakeMetaDataFailure)
            )
        }

        object FakeZipFailure : FeatureFailure()
        object FakeEncryptionFailure: FeatureFailure()
        object FakeMetaDataFailure: FeatureFailure()
    }
}
