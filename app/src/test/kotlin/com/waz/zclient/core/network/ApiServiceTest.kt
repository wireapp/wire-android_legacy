package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import org.junit.Before
import org.mockito.Mock

class ApiServiceTest : UnitTest() {

    private lateinit var apiService: ApiService

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Before
    fun setUp() {
        apiService = ApiService(networkHandler)
    }
}
