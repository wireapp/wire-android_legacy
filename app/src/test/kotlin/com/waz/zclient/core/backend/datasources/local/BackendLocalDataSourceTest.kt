package com.waz.zclient.core.backend.datasources.local

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.eq
import com.waz.zclient.storage.pref.backend.BackendPreferences
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class BackendLocalDataSourceTest : UnitTest() {

    private lateinit var backendPrefsDataSource: BackendLocalDataSource

    @Mock
    private lateinit var backendPreferences: BackendPreferences

    @Before
    fun setup() {
        backendPrefsDataSource = BackendLocalDataSource(backendPreferences)
    }

    @Test
    fun `when environment is called, returns backendPreferences' environment`() {
        backendPrefsDataSource.environment()

        verify(backendPreferences).environment
    }

    @Test
    fun `given backendConfig is valid, when getBackendConfig is requested, then return config`() {
        mockBackendPrefs(valid = true)

        val response = backendPrefsDataSource.backendConfig()

        response.fold({
            assert(false) { "Expected a valid preference" }
        }) {
            assert(it == TEST_PREFERENCE) //TODO @Fernando : Does not verify with kluent: "it shouldBe TEST_PREFERENCE"
        }
    }

    @Test
    fun `given backendConfig is not valid, when getBackendConfig is requested, then return InvalidBackendConfig error`() {
        mockBackendPrefs(valid = false)

        val response = backendPrefsDataSource.backendConfig()

        response.fold({
            it shouldBe InvalidBackendConfig
        }) {
            assert(false) //should've got an error
        }
    }

    @Test
    fun `given url and new backend config, when update backend config is requested, then update backend preferences`() {
        backendPrefsDataSource.updateBackendConfig(CONFIG_URL, TEST_PREFERENCE)

        verify(backendPreferences).environment = eq(ENVIRONMENT)
        verify(backendPreferences).customConfigUrl = eq(CONFIG_URL)
        verify(backendPreferences).accountsUrl = eq(ACCOUNTS_URL)
        verify(backendPreferences).baseUrl = eq(BASE_URL)
        verify(backendPreferences).websocketUrl = eq(WEBSOCKET_URL)
        verify(backendPreferences).teamsUrl = eq(TEAMS_URL)
        verify(backendPreferences).websiteUrl = eq(WEBSITE_URL)
        verify(backendPreferences).blacklistUrl = eq(BLACKLIST_URL)

    }

    private fun mockBackendPrefs(valid: Boolean) {
        `when`(backendPreferences.environment).thenReturn(if (valid) ENVIRONMENT else String.empty())
        `when`(backendPreferences.baseUrl).thenReturn(BASE_URL)
        `when`(backendPreferences.websocketUrl).thenReturn(WEBSOCKET_URL)
        `when`(backendPreferences.blacklistUrl).thenReturn(BLACKLIST_URL)
        `when`(backendPreferences.teamsUrl).thenReturn(TEAMS_URL)
        `when`(backendPreferences.accountsUrl).thenReturn(ACCOUNTS_URL)
        `when`(backendPreferences.websiteUrl).thenReturn(WEBSITE_URL)
    }

    companion object {
        private const val CONFIG_URL = "https://www.wire.com/config.json"
        private const val ACCOUNTS_URL = "https://accounts.wire.com"
        private const val ENVIRONMENT = "custom.environment.link.wire.com"
        private const val BASE_URL = "https://www.wire.com"
        private const val WEBSOCKET_URL = "https://websocket.wire.com"
        private const val BLACKLIST_URL = "https://blacklist.wire.com"
        private const val TEAMS_URL = "https://teams.wire.com"
        private const val WEBSITE_URL = "https://wire.com"

        private val TEST_PREFERENCE = CustomBackendPreferences(
            ENVIRONMENT,
            CustomBackendPrefEndpoints(
                backendUrl = BASE_URL,
                websocketUrl = WEBSOCKET_URL,
                blacklistUrl = BLACKLIST_URL,
                teamsUrl = TEAMS_URL,
                accountsUrl = ACCOUNTS_URL,
                websiteUrl = WEBSITE_URL
            )
        )
    }

}
