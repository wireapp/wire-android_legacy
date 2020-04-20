package com.waz.zclient.core.backend.mapper

import com.waz.zclient.UnitTest
import com.waz.zclient.core.backend.BackendItem
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefEndpoints
import com.waz.zclient.core.backend.datasources.local.CustomBackendPreferences
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponse
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponseEndpoints
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test

class BackendMapperTest : UnitTest() {

    private lateinit var backendMapper: BackendMapper

    @Before
    fun setUp() {
        backendMapper = BackendMapper()
    }

    @Test
    fun `given a backend api response, map it to backend item`() {
        val backendItem = backendMapper.toBackendItem(customBackendResponse)
        assertBackendItemValues(backendItem)
    }

    @Test
    fun `given a backend pref response, map it to backend item`() {
        val backendItem = backendMapper.toBackendItem(customPrefBackend)
        assertBackendItemValues(backendItem)
    }

    private fun assertBackendItemValues(item: BackendItem) = with(item) {
        environment shouldBe TITLE
        baseUrl shouldBe BACKEND_URL
        websocketUrl shouldBe BACKEND_WSURL
        blacklistHost shouldBe BLACKLIST_URL
        teamsUrl shouldBe TEAMS_URL
        accountsUrl shouldBe ACCOUNTS_URL
        websiteUrl shouldBe WEBSITE_URL
    }

    @Test
    fun `given custom backend response, map it to custom backend pref`() {
        val customPrefBackend = backendMapper.toPreference(backendItem)
        with(customPrefBackend) {
            title shouldBe TITLE
            with(prefEndpoints) {
                backendUrl shouldBe BACKEND_URL
                websocketUrl shouldBe BACKEND_WSURL
                blacklistUrl shouldBe BLACKLIST_URL
                teamsUrl shouldBe TEAMS_URL
                accountsUrl shouldBe ACCOUNTS_URL
                websiteUrl shouldBe WEBSITE_URL
            }
        }
    }

    companion object {
        private const val ACCOUNTS_URL = "https://accounts.wire.com"
        private const val TITLE = "custom.environment.link.wire.com"
        private const val BACKEND_URL = "https://www.wire.com"
        private const val BACKEND_WSURL = "https://www.wire.com"
        private const val BLACKLIST_URL = "https://blacklist.wire.com"
        private const val TEAMS_URL = "https://teams.wire.com"
        private const val WEBSITE_URL = "https://wire.com"

        private val backendItem = BackendItem(
            environment = TITLE,
            baseUrl = BACKEND_URL,
            websocketUrl = BACKEND_WSURL,
            blacklistHost = BLACKLIST_URL,
            teamsUrl = TEAMS_URL,
            accountsUrl = ACCOUNTS_URL,
            websiteUrl = WEBSITE_URL
        )

        private val customPrefBackend = CustomBackendPreferences(
            TITLE,
            CustomBackendPrefEndpoints(
                backendUrl = BACKEND_URL,
                websocketUrl = BACKEND_WSURL,
                blacklistUrl = BLACKLIST_URL,
                teamsUrl = TEAMS_URL,
                accountsUrl = ACCOUNTS_URL,
                websiteUrl = WEBSITE_URL
            )
        )

        private val customBackendResponse = CustomBackendResponse(
            TITLE,
            CustomBackendResponseEndpoints(
                backendUrl = BACKEND_URL,
                backendWsUrl = BACKEND_WSURL,
                blacklistUrl = BLACKLIST_URL,
                teamsUrl = TEAMS_URL,
                accountsUrl = ACCOUNTS_URL,
                websiteUrl = WEBSITE_URL
            )
        )
    }
}
