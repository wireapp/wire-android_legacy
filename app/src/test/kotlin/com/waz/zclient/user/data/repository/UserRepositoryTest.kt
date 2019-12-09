package com.waz.zclient.user.data.repository

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


    private lateinit var userRepository: UsersDataSource

    @Mock
    private lateinit var userRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var userLocalDataSource: UsersLocalDataSource

    @Mock
    private lateinit var throwable: Throwable

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        userRepository = UsersRepository(userRemoteDataSource, userLocalDataSource)
    }


    @Test
    fun test_GetProfile_Success() {
        `when`(userRemoteDataSource.profile()).thenReturn(Single.just(userEntity))
        val test = userRepository.profile().test()
        verify(userRemoteDataSource).profile()
        test.assertValue(user)
    }

    @Test
    fun test_GetProfile_Failure() {
        `when`(userRemoteDataSource.profile()).thenReturn(Single.error(throwable))
        val test = userRepository.profile().test()
        verify(userRemoteDataSource).profile()
        test.assertError(throwable)
    }

}
