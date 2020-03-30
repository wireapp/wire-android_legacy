package com.waz.zclient.core.backend.mapper

import com.waz.zclient.UnitTest
import com.waz.zclient.core.backend.CustomBackend
import com.waz.zclient.core.backend.CustomBackendEndpoint
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefEndpoints
import com.waz.zclient.core.backend.datasources.local.CustomBackendPreferences
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponse
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponseEndpoints
import org.amshove.kluent.shouldBe
import org.junit.Test

class BackendMapperTest : UnitTest() {

    private val backendMapper = BackendMapper()

    @Test
    fun `given a backend api response, map it to custom backend`() {
        val customBackend = backendMapper.toCustomBackend(customBackendResponse)
        assertCustomBackendValues(customBackend, BACKEND_WSURL)
    }

    @Test
    fun `given a backend pref response, map it to custom backend`() {
        val customBackend = backendMapper.toCustomBackend(customPrefBackend)
        assertCustomBackendValues(customBackend, null)
    }

    private fun assertCustomBackendValues(
        customBackend: CustomBackend,
        wsUrl: String?
    ) {
        with(customBackend) {
            title shouldBe TITLE
            with(endpoints) {
                backendUrl shouldBe BACKEND_URL
                backendWsUrl shouldBe wsUrl
                blacklistUrl shouldBe BLACKLIST_URL
                teamsUrl shouldBe TEAMS_URL
                accountsUrl shouldBe ACCOUNTS_URL
                websiteUrl shouldBe WEBSITE_URL
            }
        }
    }

    @Test
    fun `given custom backend response, map it to custom backend pref`() {
        val customPrefBackend = backendMapper.toCustomPrefBackend(customBackend)
        with(customPrefBackend) {
            title shouldBe TITLE
            with(prefEndpoints) {
                backendUrl shouldBe BACKEND_URL
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

        private val customBackend = CustomBackend(
            TITLE,
            CustomBackendEndpoint(
                BACKEND_URL,
                BACKEND_WSURL,
                BLACKLIST_URL,
                TEAMS_URL,
                ACCOUNTS_URL,
                WEBSITE_URL
            )
        )

        private val customPrefBackend = CustomBackendPreferences(
            TITLE,
            CustomBackendPrefEndpoints(
                BACKEND_URL,
                BLACKLIST_URL,
                TEAMS_URL,
                ACCOUNTS_URL,
                WEBSITE_URL
            )
        )

        private val customBackendResponse = CustomBackendResponse(
            TITLE,
            CustomBackendResponseEndpoints(
                BACKEND_URL,
                BACKEND_WSURL,
                BLACKLIST_URL,
                TEAMS_URL,
                ACCOUNTS_URL,
                WEBSITE_URL
            )
        )

    }
}
