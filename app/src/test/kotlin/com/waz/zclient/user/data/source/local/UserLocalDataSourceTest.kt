package com.waz.zclient.user.data.source.local

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.pref.GlobalPreferences
import com.waz.zclient.userEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations


class UserLocalDataSourceTest {


    private lateinit var usersLocalDataSource: UsersLocalDataSource

    @Mock
    private lateinit var userDatabase: UserDatabase

    @Mock
    private lateinit var globalPreferences: GlobalPreferences

    @Mock
    private lateinit var userDao: UserDbService

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(userDatabase.userDbService()).thenReturn(userDao)
        `when`(globalPreferences.activeUserId).thenReturn(TEST_USER_ID)
        usersLocalDataSource = UsersLocalDataSource(globalPreferences, userDatabase)
    }

    @Test
    fun `Given profile() is called, when dao result is successful, then return the data`() {
        runBlocking {

            `when`(userDao.selectById(TEST_USER_ID)).thenReturn(userEntity)

            usersLocalDataSource.profile()

            verify(userDao).selectById(TEST_USER_ID)

            assert(usersLocalDataSource.profile().isRight)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given profile() is called, when dao result is an error, then return the error`() {
        runBlocking {

            `when`(userDao.selectById(TEST_USER_ID)).thenReturn(userEntity)

            usersLocalDataSource.profile()

            verify(userDao).selectById(TEST_USER_ID)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(200)

            assert(usersLocalDataSource.profile().isLeft)
        }

    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_USER_ID = "userId"
    }
}
