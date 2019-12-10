package com.waz.zclient.user.data

import com.waz.zclient.user
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.data.source.UsersDataSource
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.userEntity
import io.reactivex.Single
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

    @Mock
    private lateinit var throwable: Throwable

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        usersRepository = UsersRepository(usersRemoteDataSource, usersLocalDataSource)
    }


    @Test
    fun test_Profile_From_LocalDataSource_Success() {
        `when`(usersLocalDataSource.profile()).thenReturn(Single.just(userEntity))
        `when`(usersRemoteDataSource.profile()).thenReturn(Single.just(userEntity))
        val test = usersRepository.profile().test()
        verify(usersLocalDataSource).profile()
        test.assertValue(user)
    }

    @Test
    fun test_Profile_From_LocalDataSource_Error_Profile_From_RemoteDataSource_Success() {
        `when`(usersLocalDataSource.profile()).thenReturn(Single.error(throwable))
        `when`(usersRemoteDataSource.profile()).thenReturn(Single.just(userEntity))
        val test = usersRepository.profile().test()
        verify(usersLocalDataSource).profile()
        test.assertValue(user)
    }

    @Test
    fun test_Profile_From_LocalDataSource_Error_Profile_From_RemoteDataSource_Error() {
        `when`(usersLocalDataSource.profile()).thenReturn(Single.error(throwable))
        `when`(usersRemoteDataSource.profile()).thenReturn(Single.error(throwable))
        val test = usersRepository.profile().test()
        verify(usersRemoteDataSource).profile()
        test.assertError(throwable)
    }

}
