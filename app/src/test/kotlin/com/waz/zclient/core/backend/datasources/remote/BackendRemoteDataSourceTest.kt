package com.waz.zclient.core.backend.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class BackendRemoteDataSourceTest : UnitTest() {

    private lateinit var backendRemoteDataSource: BackendRemoteDataSource

    @Mock
    private lateinit var backendApiService: BackendApiService

    @Before
    fun setup() {
        backendRemoteDataSource = BackendRemoteDataSource(backendApiService)
    }

    @Test
    fun `Given url is provided, then request apiServer for custom backend config`() =
        runBlockingTest {
            backendRemoteDataSource.getCustomBackendConfig(TEST_URL)

            verify(backendApiService).getCustomBackendConfig(eq(TEST_URL))
        }

    companion object {
        private const val TEST_URL = "https://www.wire.com"
    }

}
