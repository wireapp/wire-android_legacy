package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import org.junit.Before

class ApiServiceTest : UnitTest() {

    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        apiService = ApiService()
    }
}
