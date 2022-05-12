package com.waz.zclient.core.backend

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.backend.datasources.BackendDataSource
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.datasources.local.CustomBackendPreferences
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponse
import com.waz.zclient.core.backend.di.BackendConfigScopeManager
import com.waz.zclient.core.backend.di.BackendRemoteDataSourceProvider
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@ExperimentalCoroutinesApi
class BackendDataSourceTest : UnitTest() {

    private lateinit var backendDataSource: BackendDataSource

    @Mock
    private lateinit var remoteDataSourceProvider: BackendRemoteDataSourceProvider

    @Mock
    private lateinit var remoteDataSource: BackendRemoteDataSource

    @Mock
    private lateinit var localDataSource: BackendLocalDataSource

    @Mock
    private lateinit var backendClient: BackendClient

    @Mock
    private lateinit var backendMapper: BackendMapper

    @Mock
    private lateinit var scopeManager: BackendConfigScopeManager

    @Mock
    private lateinit var backendItem: BackendItem

    @Before
    fun setup() {
        `when`(remoteDataSourceProvider.backendRemoteDataSource()).thenReturn(remoteDataSource)
        backendDataSource =
            BackendDataSource(remoteDataSourceProvider, localDataSource, backendClient, backendMapper, scopeManager)
    }

    @Test
    fun `Given app's running with the default config, when configuredUrl is called, then returns null`() {
        `when`(scopeManager.isDefaultConfig()).thenReturn(true)

        val url = backendDataSource.configuredUrl()

        url shouldBe null
    }

    @Test
    fun `Given app's running with a new config, when configuredUrl is called, then returns current backend config's url`() {
        `when`(backendItem.baseUrl).thenReturn(TEST_URL)
        `when`(scopeManager.backendItem()).thenReturn(backendItem)
        `when`(scopeManager.isDefaultConfig()).thenReturn(false)

        val url = backendDataSource.configuredUrl()

        verify(scopeManager).backendItem()
        verify(backendItem).baseUrl
        url shouldBe TEST_URL
    }

    @Test
    fun `Given a scopeManager, when backendConfig is called, then returns scopeManager's backendItem`() {
        backendDataSource.backendConfig()

        verify(scopeManager).backendItem()
    }

    @Test
    fun `Given localDataSource has a config, when fetchBackendConfig is called, returns local config as backend item`() {
        val preference = mock(CustomBackendPreferences::class)
        `when`(localDataSource.backendConfig()).thenReturn(Either.Right(preference))
        `when`(backendMapper.toBackendItem(preference)).thenReturn(backendItem)

        val backendConfig = backendDataSource.fetchBackendConfig()

        verify(localDataSource).backendConfig()
        verify(backendMapper).toBackendItem(preference)
        backendConfig shouldBe backendItem
    }

    @Test
    fun `Given localDataSource doesn't have a config, when fetchBackendConfig is called, returns backendClient's backend item`() {
        `when`(localDataSource.backendConfig()).thenReturn(Either.Left(ServerError))

        val environment = "Environment"
        `when`(localDataSource.environment()).thenReturn(environment)
        `when`(backendClient.get(environment)).thenReturn(backendItem)

        val backendConfig = backendDataSource.fetchBackendConfig()

        verify(localDataSource).backendConfig()
        verify(backendMapper, never()).toBackendItem(any<CustomBackendPreferences>())
        verify(backendClient).get(eq(environment))
        backendConfig shouldBe backendItem
    }

    @Test
    fun `Given a url, when loadBackendConfig is called and remote call succeeds, returns remote response as item`() =
        runBlockingTest {
            val backendResponse = mock(CustomBackendResponse::class)
            `when`(remoteDataSource.getCustomBackendConfig(TEST_URL)).thenReturn(Either.Right(backendResponse))
            `when`(backendMapper.toBackendItem(backendResponse)).thenReturn(backendItem)

            val loadedConfig = backendDataSource.loadBackendConfig(TEST_URL)

            verify(remoteDataSource).getCustomBackendConfig(eq(TEST_URL))
            verify(backendMapper).toBackendItem(backendResponse)
            loadedConfig.fold({ assert(false) }) {
                it shouldBe backendItem
            }
        }

    @Test
    fun `Given a url, when loadBackendConfig is called and remote call succeeds, stores response locally`() =
        runBlockingTest {
            val environment = "Environment"
            `when`(backendItem.environment).thenReturn(environment)

            val response = mock(CustomBackendResponse::class)
            `when`(remoteDataSource.getCustomBackendConfig(TEST_URL)).thenReturn(Either.Right(response))

            `when`(backendMapper.toBackendItem(response)).thenReturn(backendItem)

            val backendPreference = mock(CustomBackendPreferences::class)
            `when`(backendMapper.toPreference(backendItem)).thenReturn(backendPreference)

            backendDataSource.loadBackendConfig(TEST_URL)

            verify(remoteDataSource).getCustomBackendConfig(eq(TEST_URL))
            verify(backendMapper).toPreference(backendItem)
            verify(localDataSource).updateBackendConfig(TEST_URL, backendPreference)
            verify(scopeManager).onConfigChanged(environment)
        }

    @Test
    fun `Given a url, when loadBackendConfig is called and remote call fails, then returns failure directly`() =
        runBlockingTest {
            `when`(remoteDataSource.getCustomBackendConfig(TEST_URL)).thenReturn(Either.Left(ServerError))

            val result = backendDataSource.loadBackendConfig(TEST_URL)

            verifyNoInteractions(backendMapper)
            verifyNoInteractions(localDataSource)
            verifyNoInteractions(scopeManager)
            result.fold({
                it shouldBe ServerError
            }) { assert(false) }
        }

    companion object {
        private const val TEST_URL = "https://wire.com"
    }
}
