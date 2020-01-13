package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.api.token.AccessTokenResponse
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test

class AccessTokenMapperTest : UnitTest() {

    private lateinit var mapper: AccessTokenMapper

    @Before
    fun setUp() {
        mapper = AccessTokenMapper()
    }

    @Test
    fun `given an AccessTokenPreference, maps it to AccessToken`() {
        val accessToken = mapper.from(ACCESS_TOKEN_PREF)

        accessToken.token shouldBe ACCESS_TOKEN_PREF.token
        accessToken.tokenType shouldBe ACCESS_TOKEN_PREF.tokenType
        accessToken.expiresIn shouldBe ACCESS_TOKEN_PREF.expiresIn
    }

    @Test
    fun `given an AccessToken, maps it to AccessTokenPreference`() {
        val accessTokenPref = mapper.toPreference(ACCESS_TOKEN)

        accessTokenPref.token shouldBe ACCESS_TOKEN.token
        accessTokenPref.tokenType shouldBe ACCESS_TOKEN.tokenType
        accessTokenPref.expiresIn shouldBe ACCESS_TOKEN.expiresIn
    }

    @Test
    fun `given an AccessTokenResponse, maps it to AccessToken`() {
        val accessToken = mapper.from(ACCESS_TOKEN_RESPONSE)

        accessToken.token shouldBe ACCESS_TOKEN_RESPONSE.token
        accessToken.tokenType shouldBe ACCESS_TOKEN_RESPONSE.type
        accessToken.expiresIn shouldBe ACCESS_TOKEN_RESPONSE.expiresIn
    }

    companion object {
        private val ACCESS_TOKEN = AccessToken("token", "type", "expiry")
        private val ACCESS_TOKEN_PREF =
            AccessTokenPreference("token", "type", "expiry")
        private val ACCESS_TOKEN_RESPONSE =
            AccessTokenResponse("token", "type", "userId", "expiry")
    }
}
