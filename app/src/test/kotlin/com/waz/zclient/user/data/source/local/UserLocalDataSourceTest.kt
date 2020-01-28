package com.waz.zclient.user.data.source.local

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.pref.GlobalPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*

@ExperimentalCoroutinesApi
class UserLocalDataSourceTest : UnitTest() {

    private lateinit var usersLocalDataSource: UsersLocalDataSource

    @Mock
    private lateinit var userDatabase: UserDatabase

    @Mock
    private lateinit var globalPreferences: GlobalPreferences

    @Mock
    private lateinit var userDbService: UserDbService

    @Mock
    private lateinit var user: UserDao

    @Before
    fun setup() {
        lenient().`when`(userDatabase.userDbService()).thenReturn(userDbService)
        `when`(globalPreferences.activeUserId).thenReturn(TEST_USER_ID)
        usersLocalDataSource = UsersLocalDataSource(userDbService, globalPreferences)
    }

    @Test
    fun `Given profile() is called, when dao result is successful, then return the data`() = runBlockingTest {
        usersLocalDataSource.profileDetails()

        verify(userDbService).byId(eq(TEST_USER_ID))
    }

    @Test
    fun `Given insertUser is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            usersLocalDataSource.insertUser(user)

            verify(userDbService).insert(eq(user))

            usersLocalDataSource.insertUser(user).isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given insertUser is called, when dao result is cancelled, then return the data`() {
        runBlockingTest {
            usersLocalDataSource.insertUser(user)

            verify(userDbService).insert(eq(user))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            usersLocalDataSource.insertUser(user).isRight shouldBe false
        }
    }


    @Test
    fun `Given changeName is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            usersLocalDataSource.changeName(TEST_NAME)

            verify(userDbService).updateName(eq(TEST_USER_ID), eq(TEST_NAME))

            usersLocalDataSource.changeName(TEST_NAME).isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changeName is called, when dao result is cancelled, then return error`() {
        runBlockingTest {

            usersLocalDataSource.changeName(TEST_NAME)

            verify(userDbService).updateName(eq(TEST_USER_ID), eq(TEST_NAME))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            usersLocalDataSource.changeName(TEST_NAME).isRight shouldBe false
        }

    }

    @Test
    fun `Given changeHandle is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            usersLocalDataSource.changeHandle(TEST_HANDLE)

            verify(userDbService).updateHandle(eq(TEST_USER_ID), eq(TEST_HANDLE))

            usersLocalDataSource.changeHandle(TEST_HANDLE).isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changeHandle is called, when dao result is cancelled, then return error`() {
        runBlockingTest {

            usersLocalDataSource.changeHandle(TEST_HANDLE)

            verify(userDbService).updateHandle(eq(TEST_USER_ID), eq(TEST_HANDLE))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            usersLocalDataSource.changeHandle(TEST_HANDLE).isRight shouldBe false
        }
    }

    @Test
    fun `Given changeEmail is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            usersLocalDataSource.changeEmail(TEST_EMAIL)

            verify(userDbService).updateEmail(eq(TEST_USER_ID), eq(TEST_EMAIL))

            usersLocalDataSource.changeEmail(TEST_EMAIL).isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changeEmail is called, when dao result is cancelled, then return error`() {
        runBlockingTest {
            usersLocalDataSource.changeEmail(TEST_EMAIL)

            verify(userDbService).updateEmail(eq(TEST_USER_ID), eq(TEST_EMAIL))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            usersLocalDataSource.changeEmail(TEST_EMAIL).isRight shouldBe false
        }
    }

    @Test
    fun `Given changePhone is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            usersLocalDataSource.changePhone(TEST_PHONE)

            verify(userDbService).updatePhone(eq(TEST_USER_ID), eq(TEST_PHONE))

            usersLocalDataSource.changePhone(TEST_PHONE).isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changePhone is called, when dao result is cancelled, then return error`() {
        runBlockingTest {
            usersLocalDataSource.changePhone(TEST_PHONE)

            verify(userDbService).updatePhone(eq(TEST_USER_ID), eq(TEST_PHONE))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            usersLocalDataSource.changePhone(TEST_PHONE).isRight shouldBe false
        }
    }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_USER_ID = "userId"
        private const val TEST_NAME = "name"
        private const val TEST_HANDLE = "@handle"
        private const val TEST_EMAIL = "email@wire.com"
        private const val TEST_PHONE = "+497588838839"
    }
}
