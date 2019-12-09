package com.waz.zclient.user.data.source.local

import android.content.Context
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.dao.UserDao
import com.waz.zclient.storage.pref.GlobalPreferences
import com.waz.zclient.userEntity
import io.reactivex.Single
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
    private lateinit var userDao: UserDao

    @Mock
    private lateinit var throwable: Throwable

    private val userId = "aa4e0112-bc8c-493e-8677-9fde2edf3567"

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(userDatabase.userDao()).thenReturn(userDao)
        `when`(globalPreferences.activeUserId).thenReturn(userId)
        usersLocalDataSource = UsersLocalDataSource(globalPreferences,userDatabase)
    }


    @Test
    fun test_Profile_Success() {
        `when`(userDao.selectById(userId)).thenReturn(Single.just(userEntity))
        val test = usersLocalDataSource.profile().test()
        verify(userDao).selectById(userId)
        test.assertValue(userEntity)
    }

    @Test
    fun test_Profile_Failure() {
        `when`(userDao.selectById(userId)).thenReturn(Single.error(throwable))
        val test = usersLocalDataSource.profile().test()
        verify(userDao).selectById(userId)
        test.assertError(throwable)
    }

}
