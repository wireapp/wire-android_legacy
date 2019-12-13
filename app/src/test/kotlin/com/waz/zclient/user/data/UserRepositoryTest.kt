package com.waz.zclient.user.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.userEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class UserRepositoryTest {


    private lateinit var usersRepository: UsersRepository

    @Mock
    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersLocalDataSource: UsersLocalDataSource


    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        usersRepository = UsersDataSource(usersRemoteDataSource, usersLocalDataSource)
    }


    @Test
    fun `Given profile() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlocking {
            `when`(usersLocalDataSource.profile()).thenReturn(Either.Right(userEntity))

            usersRepository.profile()

            verify(usersLocalDataSource).profile()

            usersRepository.profile().map {

                assertEquals(it, userEntity.toUser())
            }
        }
    }


    @Test
    fun `Given profile() is called, when the local data source failed, remote data source is called, then map the data response to domain`() {
        runBlocking {

            `when`(usersLocalDataSource.profile()).thenReturn(Either.Left(Failure.CancellationError))
            `when`(usersRemoteDataSource.profile()).thenReturn(Either.Right(userEntity))

            usersRepository.profile()

            verify(usersLocalDataSource).profile()
            verify(usersRemoteDataSource).profile()

            usersRepository.profile().map {

                assertEquals(it, userEntity.toUser())
            }
        }
    }


    @Test(expected = CancellationException::class)
    fun `Given profile() is called, when the local data source failed and the remote data source failed, then return an error`() {
        runBlocking {

            `when`(usersLocalDataSource.profile()).thenReturn(Either.Left(Failure.ServerError(TEST_CODE, TEST_ERROR_MESSAGE)))
            `when`(usersRemoteDataSource.profile()).thenReturn(Either.Left(Failure.CancellationError))

            usersRepository.profile()

            verify(usersLocalDataSource).profile()
            verify(usersRemoteDataSource).profile()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(usersRepository.profile().isLeft)
        }
    }

    companion object {
        private const val TEST_CODE = 404
        private const val TEST_ERROR_MESSAGE = "Network request failed"
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
