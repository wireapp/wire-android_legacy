package com.waz.zclient.shared.user.datasources.local

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.storage.db.users.model.UsersEntity
import com.waz.zclient.storage.db.users.service.UsersDao
import com.waz.zclient.storage.pref.global.GlobalPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class UserLocalDataSourceTest : UnitTest() {

    private lateinit var usersLocalDataSource: UsersLocalDataSource

    @Mock
    private lateinit var globalPreferences: GlobalPreferences

    @Mock
    private lateinit var usersDao: UsersDao

    @Mock
    private lateinit var user: UsersEntity

    @Before
    fun setup() {
        `when`(globalPreferences.activeUserId).thenReturn(TEST_USER_ID)
        usersLocalDataSource = UsersLocalDataSource(usersDao, globalPreferences)
    }

    @Test
    fun `Given profile() is called, when dao result is successful, then return the data`() = runBlockingTest {
        usersLocalDataSource.profileDetails()

        verify(usersDao).byId(eq(TEST_USER_ID))
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
