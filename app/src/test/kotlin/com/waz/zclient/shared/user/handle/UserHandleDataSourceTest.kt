package com.waz.zclient.shared.user.handle

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.shared.user.datasources.local.UsersLocalDataSource
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@ExperimentalCoroutinesApi
class UserHandleRepositoryTest : UnitTest() {

    private lateinit var handleRepository: UserHandleRepository

    @Mock
    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersLocalDataSource: UsersLocalDataSource

    @Before
    fun setup() {
        handleRepository = UserHandleDataSource(usersRemoteDataSource, usersLocalDataSource)
    }

    @Test
    fun `Given changeHandle() is called and remote request fails, then don't update database`() = runBlockingTest {
        `when`(usersRemoteDataSource.changeHandle(TEST_HANDLE)).thenReturn(Either.Left(ServerError))

        handleRepository.changeHandle(TEST_HANDLE)

        verify(usersRemoteDataSource).changeHandle(eq(TEST_HANDLE))
        verifyNoInteractions(usersLocalDataSource)
    }

    @Test
    fun `Given changeHandle() is called and remote request is success, then update database`() = runBlockingTest {
        `when`(usersRemoteDataSource.changeHandle(TEST_HANDLE)).thenReturn(Either.Right(Unit))

        handleRepository.changeHandle(TEST_HANDLE)

        verify(usersLocalDataSource).changeHandle(eq(TEST_HANDLE))
    }

    @Test
    fun `Given checkHandleExists() is called, then call api to check`() = runBlockingTest {
        handleRepository.doesHandleExist(TEST_HANDLE)

        verify(usersRemoteDataSource).doesHandleExist(eq(TEST_HANDLE))
    }

    companion object {
        private const val TEST_HANDLE = "@Handle"
    }
}
