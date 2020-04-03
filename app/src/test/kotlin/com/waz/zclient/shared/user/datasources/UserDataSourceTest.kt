package com.waz.zclient.shared.user.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.eq
import com.waz.zclient.shared.user.UsersRepository
import com.waz.zclient.shared.user.datasources.local.UsersLocalDataSource
import com.waz.zclient.shared.user.datasources.remote.UserResponse
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import com.waz.zclient.shared.user.mapper.UserMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
class UserDataSourceTest : UnitTest() {

    private lateinit var usersRepository: UsersRepository

    @Mock
    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersLocalDataSource: UsersLocalDataSource

    @Mock
    private lateinit var userMapper: UserMapper

    @Mock
    private lateinit var userResponse: UserResponse

    @Before
    fun setup() {
        usersRepository = UsersDataSource(usersRemoteDataSource, usersLocalDataSource, userMapper)
    }

    @Test
    fun `Given profileDetails() is called and local database request is successful, then map local response`() = runBlockingTest {
        usersRepository.profileDetails()

        verify(usersLocalDataSource).profileDetails()

        usersLocalDataSource.profileDetails().map {
            verify(userMapper).toUser(it)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given profileDetails() is called and local database request fails and api request succeeds, then map api response and emit it in flow`() = runBlockingTest {
        `when`(usersRemoteDataSource.profileDetails()).thenReturn(Either.Right(userResponse))

        usersRepository.profileDetails()

        verify(usersLocalDataSource).profileDetails()

        cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

        usersRemoteDataSource.profileDetails().map {
            runBlockingTest {
                val user = userMapper.toUser(it)
                verify(usersLocalDataSource).insertUser(eq(userMapper.toUserEntity(user)))

                usersRepository.profileDetails().single() shouldBe user
            }
        }

    }

    @Test
    fun `Given changeName() is called and remote request fails, then don't update database`() = runBlockingTest {
        `when`(usersRemoteDataSource.changeName(TEST_NAME)).thenReturn(Either.Left(ServerError))

        usersRepository.changeName(TEST_NAME)

        verify(usersRemoteDataSource).changeName(eq(TEST_NAME))
        verifyNoInteractions(usersLocalDataSource)
    }

    @Test
    fun `Given changeName() is called and remote request is success, then update database`() = runBlockingTest {
        `when`(usersRemoteDataSource.changeName(TEST_NAME)).thenReturn(Either.Right(Unit))

        usersRepository.changeName(TEST_NAME)

        verify(usersLocalDataSource).changeName(eq(TEST_NAME))
    }

    @Test
    fun `Given changeEmail() is called and remote request fails, then don't update database`() = runBlockingTest {
        `when`(usersRemoteDataSource.changeEmail(TEST_EMAIL)).thenReturn(Either.Left(ServerError))

        usersRepository.changeEmail(TEST_EMAIL)

        verify(usersRemoteDataSource).changeEmail(eq(TEST_EMAIL))
        verifyNoInteractions(usersLocalDataSource)
    }

    @Test
    fun `Given changeEmail() is called and remote request is success, then update database`() = runBlockingTest {
        `when`(usersRemoteDataSource.changeEmail(TEST_EMAIL)).thenReturn(Either.Right(Unit))

        usersRepository.changeEmail(TEST_EMAIL)

        verify(usersLocalDataSource).changeEmail(eq(TEST_EMAIL))
    }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "email@wire.com"
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }

}
