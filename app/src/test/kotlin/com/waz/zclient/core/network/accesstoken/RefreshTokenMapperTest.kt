package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test

class RefreshTokenMapperTest: UnitTest() {

    private lateinit var refreshTokenMapper: RefreshTokenMapper

    @Before
    fun setUp() {
        refreshTokenMapper = RefreshTokenMapper()
    }

    @Test
    fun `given a tokenText, when fromTokenText called, maps tokenText to RefreshToken's token`() {
        val tokenText = "someText"

        val refreshToken = refreshTokenMapper.fromTokenText(tokenText)

        refreshToken.token shouldBe tokenText
    }

    @Test
    fun `given a RefreshToken, when toEntity method called, returns its token as result`() {
        val token = "someToken"
        val refreshToken = RefreshToken(token)

        val entityResult = refreshTokenMapper.toEntity(refreshToken)

        entityResult shouldBe token
    }
}
