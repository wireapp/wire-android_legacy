package com.waz.zclient.core.backend

import com.waz.zclient.UnitTest
import com.waz.zclient.core.backend.datasources.local.BackendPrefsDataSource
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefResponse
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponse
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.backend.usecase.CustomBackend
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@ExperimentalCoroutinesApi
class BackendDataSourceTest : UnitTest() {

    private lateinit var backendDataSource: BackendDataSource

    @Mock
    private lateinit var backendRemoteDataSource: BackendRemoteDataSource

    @Mock
    private lateinit var backendPrefsDataSource: BackendPrefsDataSource

    @Mock
    private lateinit var backendMapper: BackendMapper

    @Before
    fun setup() {
        backendDataSource = BackendDataSource(backendRemoteDataSource, backendPrefsDataSource, backendMapper)
    }

    @Test
    fun `Given url is provided, when getCustomBackendConfig is called and pref request fails, then request config remotely and update prefs`() =
        runBlockingTest {
            val customBackendResponse = mock(CustomBackendResponse::class.java)
            val customBackend = mock(CustomBackend::class.java)
            val customPrefBackend: CustomBackendPrefResponse = mock(CustomBackendPrefResponse::class.java)

            `when`(backendPrefsDataSource.getCustomBackendConfig()).thenReturn(Either.Left(ServerError))
            `when`(backendRemoteDataSource.getCustomBackendConfig(TEST_URL)).thenReturn(Either.Right(customBackendResponse))
            `when`(backendMapper.toCustomBackend(customBackendResponse)).thenReturn(customBackend)
            `when`(backendMapper.toCustomPrefBackend(customBackend)).thenReturn(customPrefBackend)

            backendDataSource.getCustomBackendConfig(TEST_URL)

            verify(backendPrefsDataSource).getCustomBackendConfig()
            verify(backendRemoteDataSource).getCustomBackendConfig(eq(TEST_URL))
            verify(backendPrefsDataSource).updateCustomBackendConfig(eq(TEST_URL), eq(customPrefBackend))
        }

    @Test
    fun `Given url is provided, when getCustomBackendConfig is called and pref request succeeds, then return pref config`() =
        runBlockingTest {
            val customBackend = mock(CustomBackend::class.java)
            val customPrefBackend: CustomBackendPrefResponse = mock(CustomBackendPrefResponse::class.java)

            `when`(backendPrefsDataSource.getCustomBackendConfig()).thenReturn(Either.Right(customPrefBackend))
            `when`(backendMapper.toCustomBackend(customPrefBackend)).thenReturn(customBackend)

            backendDataSource.getCustomBackendConfig(TEST_URL)

            verify(backendPrefsDataSource).getCustomBackendConfig()
            verifyNoInteractions(backendRemoteDataSource)
        }

    companion object {
        private const val TEST_URL = "https://wire.com"
    }


}
