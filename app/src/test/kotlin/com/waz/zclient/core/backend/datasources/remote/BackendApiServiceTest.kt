package com.waz.zclient.core.backend.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class BackendApiServiceTest : UnitTest() {

    private lateinit var backendApiService: BackendApiService

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var backendApi: BackendApi

    @Before
    fun setup() {
        `when`(networkHandler.isConnected).thenReturn(true)
        backendApiService = BackendApiService(backendApi, networkHandler)
    }

    @Test
    fun `given config url, when getting custom backend config, returns config json`(): Unit =
        runBlocking {
            backendApiService.getCustomBackendConfig(TEST_URL)

            verify(backendApi).getCustomBackendConfig(eq(TEST_URL))

            Unit
        }

    companion object {
        private const val TEST_URL = "https://www.wire.com"
    }

}
