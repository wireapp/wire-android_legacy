package com.waz.client.user.data.repository

import com.waz.client.user
import com.waz.client.userEntity
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import com.waz.zclient.user.data.source.remote.UserRemoteDataSource
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class UserRepositoryImplTest {


    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userRemoteDataSource: UserRemoteDataSource

    @Mock
    private lateinit var throwable: Throwable

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        userRepository = UserRepositoryImpl(userRemoteDataSource)
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
