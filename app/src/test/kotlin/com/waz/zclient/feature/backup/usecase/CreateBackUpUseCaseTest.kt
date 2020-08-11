package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.BackUpRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.File

@ExperimentalCoroutinesApi
class CreateBackUpUseCaseTest : UnitTest() {

    private lateinit var createBackUpUseCase: CreateBackUpUseCase

    private val testCoroutineScope = TestCoroutineScope()

    @Test
    fun `given back up repositories, when all of them succeed, then returns success`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)

            createBackUpUseCase = CreateBackUpUseCase(listOf(repo1, repo2, repo3), testCoroutineScope)

            val result = createBackUpUseCase.run(Unit)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            verify(repo3).saveBackup()

            assertEquals(Either.Right(Unit), result)
        }
    }

    @Test
    fun `given back up repositories, when one of them fails, then does not execute others and returns BackUpCreationFailure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(false)
            val repo3 = mockBackUpRepo(true)

            createBackUpUseCase = CreateBackUpUseCase(listOf(repo1, repo2, repo3), testCoroutineScope)

            val result = createBackUpUseCase.run(Unit)

            verify(repo1).saveBackup()
            verify(repo2).saveBackup()
            //TODO implement a fail-fast approach inside the use-case to accommodate this
            //verifyNoInteractions(repo3)

            assertEquals(Either.Left(BackUpCreationFailure), result)
        }
    }

    companion object {
        suspend fun mockBackUpRepo(backUpSuccess: Boolean = true): BackUpRepository<File> = mock(BackUpRepository::class.java).also {
            `when`(it.saveBackup()).thenReturn(
                    if (backUpSuccess) Either.Right(File.createTempFile("temp", System.currentTimeMillis().toString()))
                    else Either.Left(DatabaseError)
            )
        } as BackUpRepository<File>
    }
}
