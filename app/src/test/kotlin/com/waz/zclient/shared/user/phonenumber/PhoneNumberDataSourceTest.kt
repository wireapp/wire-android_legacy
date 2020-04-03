package com.waz.zclient.shared.user.phonenumber

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
import org.mockito.Mockito

@ExperimentalCoroutinesApi
class PhoneNumberDataSourceTest : UnitTest() {

    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Mock
    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersLocalDataSource: UsersLocalDataSource

    @Before
    fun setup() {
        phoneNumberRepository = PhoneNumberDataSource(usersRemoteDataSource, usersLocalDataSource)
    }

    @Test
    fun `Given changePhone() is called and remote request fails then don't update database`() = runBlockingTest {
        Mockito.`when`(usersRemoteDataSource.changePhone(TEST_PHONE)).thenReturn(Either.Left(ServerError))

        phoneNumberRepository.changePhone(TEST_PHONE)

        Mockito.verify(usersRemoteDataSource).changePhone(eq(TEST_PHONE))
        Mockito.verifyNoInteractions(usersLocalDataSource)
    }

    @Test
    fun `Given changePhone() is called and remote request is success, then update database`() = runBlockingTest {
        Mockito.`when`(usersRemoteDataSource.changePhone(TEST_PHONE)).thenReturn(Either.Right(Unit))

        phoneNumberRepository.changePhone(TEST_PHONE)

        Mockito.verify(usersLocalDataSource).changePhone(eq(TEST_PHONE))
    }

    @Test
    fun `Given deletePhone() is called and remote request fails then don't update database`() = runBlockingTest {
        Mockito.`when`(usersRemoteDataSource.deletePhone()).thenReturn(Either.Left(ServerError))

        phoneNumberRepository.deletePhone()

        Mockito.verify(usersRemoteDataSource).deletePhone()
        Mockito.verifyNoInteractions(usersLocalDataSource)
    }

    @Test
    fun `Given deletePhone() is called and remote request is success, then update database`() = runBlockingTest {
        Mockito.`when`(usersRemoteDataSource.deletePhone()).thenReturn(Either.Right(Unit))

        phoneNumberRepository.deletePhone()

        Mockito.verify(usersLocalDataSource).deletePhone()
    }

    companion object {
        private const val TEST_PHONE = "+49766378499"
    }

}
