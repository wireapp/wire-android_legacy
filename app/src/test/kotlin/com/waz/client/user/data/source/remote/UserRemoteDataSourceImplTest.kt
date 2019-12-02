package com.waz.client.user.data.source.remote

import com.waz.client.userEntity
import com.waz.zclient.user.data.source.remote.UserApi
import com.waz.zclient.user.data.source.remote.UserRemoteDataSource
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImpl
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class UserRemoteDataSourceImplTest {


    private lateinit var userRemoteDataSource: UserRemoteDataSource

    @Mock
    private lateinit var userApi: UserApi

    @Mock
    private lateinit var throwable: Throwable

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        userRemoteDataSource = UserRemoteDataSourceImpl(userApi)
    }


    @Test
    fun test_GetProfile_Success() {
        //`when`(Network().getUserApi()).thenReturn(userApi)
        `when`(userApi.getProfile()).thenReturn(Single.just(userEntity))
        val test = userRemoteDataSource.getProfile().test()
        verify(userApi).getProfile()
        test.assertValue(userEntity)
    }

    @Test
    fun test_GetProfile_Failure() {
        //`when`(Network().getUserApi()).thenReturn(userApi)
        `when`(userApi.getProfile()).thenReturn(Single.error(throwable))
        val test = userRemoteDataSource.getProfile().test()
        verify(userApi).getProfile()
        test.assertError(throwable)
    }

}
