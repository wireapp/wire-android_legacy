package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.BackUpRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class CreateBackUpUseCaseTest : UnitTest() {

    private lateinit var createBackUpUseCase: CreateBackUpUseCase

    @Test
    fun `given back up repositories, when all of them succeed, then returns success`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(true)
            val repo3 = mockBackUpRepo(true)

            createBackUpUseCase = CreateBackUpUseCase(listOf(repo1, repo2, repo3))

            val result = createBackUpUseCase.run(Unit)

            verify(repo1).backUp()
            verify(repo2).backUp()
            verify(repo3).backUp()

            assertEquals(Either.Right(Unit), result)
        }
    }

    @Test
    fun `given back up repositories, when one of them fails, then does not execute others and returns BackUpCreationFailure`() {
        runBlocking {
            val repo1 = mockBackUpRepo(true)
            val repo2 = mockBackUpRepo(false)
            val repo3 = mockBackUpRepo(true)

            createBackUpUseCase = CreateBackUpUseCase(listOf(repo1, repo2, repo3))

            val result = createBackUpUseCase.run(Unit)

            verify(repo1).backUp()
            verify(repo2).backUp()
            verifyNoInteractions(repo3)

            assertEquals(Either.Left(BackUpCreationFailure), result)
        }
    }

    companion object {
        suspend fun mockBackUpRepo(backUpSuccess: Boolean = true) = mock(BackUpRepository::class.java).also {
            `when`(it.backUp()).thenReturn(if (backUpSuccess) Either.Right(Unit) else Either.Left(DatabaseError))
        }
    }
}
