package com.waz.zclient.core.backend.datasources.local

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.storage.pref.backend.BackendPreferences
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class BackendLocalDataSourceTest : UnitTest() {

    private lateinit var backendPrefsDataSource: BackendLocalDataSource

    @Mock
    private lateinit var backendPreferences: BackendPreferences

    @Mock
    private lateinit var backendConfig: CustomBackendPreferences

    @Before
    fun setup() {
        backendPrefsDataSource = BackendLocalDataSource(backendPreferences, backendConfig)
    }

    @Test
    fun `given backendConfig is valid, when getBackendConfig is requested, then return config`() {
        `when`(backendConfig.isValid()).thenReturn(true)

        val response = backendPrefsDataSource.getCustomBackendConfig()

        assert(response.isRight)
    }

    @Test
    fun `given backendConfig is not valid, when getBackendConfig is requested, then return InvalidBackendConfig error`() {
        `when`(backendConfig.isValid()).thenReturn(false)

        val response = backendPrefsDataSource.getCustomBackendConfig()

        assert(response.isLeft)
    }

    @Test
    fun `given url and new backend config, when update backend config is requested, then update backend preferences`() {
        val configUrl = "https://www.wire.com/config.json"
        val accountsUrl = "https://accounts.wire.com"
        val environment = "custom.environment.link.wire.com"
        val baseUrl = "https://www.wire.com"
        val blacklistUrl = "https://blacklist.wire.com"
        val teamsUrl = "https://teams.wire.com"
        val websiteUrl = "https://wire.com"

        val newBackendConfig = CustomBackendPreferences(
            environment,
            CustomBackendPrefEndpoints(
                baseUrl,
                blacklistUrl,
                teamsUrl,
                accountsUrl,
                websiteUrl
            )
        )

        backendPrefsDataSource.updateCustomBackendConfig(configUrl, newBackendConfig)

        verify(backendPreferences).environment = eq(environment)
        verify(backendPreferences).customConfigUrl = eq(configUrl)
        verify(backendPreferences).accountsUrl = eq(accountsUrl)
        verify(backendPreferences).baseUrl = eq(baseUrl)
        verify(backendPreferences).teamsUrl = eq(teamsUrl)
        verify(backendPreferences).websiteUrl = eq(websiteUrl)
        verify(backendPreferences).blacklistUrl = eq(blacklistUrl)

    }

}
