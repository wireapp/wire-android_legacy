package com.waz.zclient.core.network

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
    fun `given a RefreshTokenPreference, when from method called, maps its token to RefreshToken's token`() {
        val refreshTokenPreference = RefreshTokenPreference("someToken")

        val refreshToken = refreshTokenMapper.from(refreshTokenPreference)

        refreshToken.token shouldBe refreshTokenPreference.token
    }
}
