package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import org.amshove.kluent.shouldBe
import org.junit.Test
import org.threeten.bp.Instant

class RefreshTokenTest : UnitTest() {

    @Test
    fun `given a token text with invalid expiryDate, when expiryDate called, returns null Instant`() {
        val invalidToken = "someToken"

        val refreshToken = RefreshToken(invalidToken)

        refreshToken.expiryDate shouldBe null
    }

    @Test
    fun `given a token text with expiryDate, when expiryDate called, returns date part of the token text as instant`() {
        val refreshToken = RefreshToken(TOKEN_TEXT_WITH_DATE)

        refreshToken.expiryDate?.compareTo(Instant.ofEpochSecond(DATE_AS_SECONDS)) shouldBe 0
    }

    @Test
    fun `given two RefreshTokens with same token but different expiryDates, when equals called, returns true`() {
        val token1 = RefreshToken(TOKEN_TEXT_WITH_DATE)
        val token2 = RefreshToken(TOKEN_TEXT_WITH_DATE)

        //calculate expiryDate for the first one:
        token1.expiryDate

        assert(token1 == token2)
    }

    companion object {
        private const val DATE_AS_SECONDS = 1583315326L
        private const val TOKEN_TEXT_WITH_DATE =
            "QF2Ol1_DTeatIvD_9GMq9H5OBvVkLdHSfcbeNkyPbzni0OsivrIoQsCb1FS-Xflxi4KaWo7XZ4_Mobts-5a0Ag==.v=1.k=1" +
                ".d=$DATE_AS_SECONDS.t=u.l=.u=1c5af167-fd5d-4ee5-a11b-fe93b1882ade.r=8f81ece9"
    }
}
