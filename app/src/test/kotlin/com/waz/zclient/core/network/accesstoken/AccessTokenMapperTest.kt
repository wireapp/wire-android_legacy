package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.api.token.AccessTokenResponse
import com.waz.zclient.storage.db.accountdata.AccessTokenEntity
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqualTo
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
        val accessToken = mapper.from(ACCESS_TOKEN_LOCAL)

        accessToken.token shouldBe ACCESS_TOKEN_LOCAL.token
        accessToken.tokenType shouldBe ACCESS_TOKEN_LOCAL.tokenType
        accessToken.expiresIn shouldEqualTo ACCESS_TOKEN_LOCAL.expiresInMillis.toString()
    }

    @Test
    fun `given an AccessToken, maps it to AccessTokenPreference`() {
        val accessTokenEntity = mapper.toEntity(ACCESS_TOKEN)

        accessTokenEntity.token shouldBe ACCESS_TOKEN.token
        accessTokenEntity.tokenType shouldBe ACCESS_TOKEN.tokenType
        accessTokenEntity.expiresInMillis shouldEqualTo ACCESS_TOKEN.expiresIn.toLong()
    }

    @Test
    fun `given an AccessTokenResponse, maps it to AccessToken`() {
        val accessToken = mapper.from(ACCESS_TOKEN_REMOTE)

        accessToken.token shouldBe ACCESS_TOKEN_REMOTE.token
        accessToken.tokenType shouldBe ACCESS_TOKEN_REMOTE.type
        accessToken.expiresIn shouldBe ACCESS_TOKEN_REMOTE.expiresIn
    }

    companion object {
        private const val EXPIRE_MILLIS = 1000L
        private val ACCESS_TOKEN = AccessToken("token", "type", EXPIRE_MILLIS.toString())
        private val ACCESS_TOKEN_LOCAL =
            AccessTokenEntity("token", "type", EXPIRE_MILLIS)
        private val ACCESS_TOKEN_REMOTE =
            AccessTokenResponse("token", "type", "userId", EXPIRE_MILLIS.toString())
    }
}
