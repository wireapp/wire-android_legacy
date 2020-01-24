package com.waz.zclient.storage.db.accountdata

import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqualTo
import org.amshove.kluent.shouldNotBe
import org.junit.Before
import org.junit.Test

class AccessTokenConverterTest {

    private lateinit var accessTokenConverter: AccessTokenConverter

    @Before
    fun setUp() {
        accessTokenConverter = AccessTokenConverter()
    }

    @Test
    fun `given a string with token, type and expires, when fromStringToAccessToken called, maps it to AccessTokenEntity`() {
        val tokenString = """
            {
                "token":"fbLpw9Entnm9WWbTyrEhE7GywnTTLqBDI6MAF6JyzdoerAHQQ9BsuubUjGO77GMiW1WJ8jJxqXaA9fHbXAMnCA==.v=1.k=1.d=1579283492.t=a.l=.u=1c5af167-fd5d-4ee5-a11b-fe93b1882ade.c=6843294628315865806",
                "type":"Bearer",
                "expires":1579283491844
            }
        """.trimIndent()

        val entity = accessTokenConverter.fromStringToAccessToken(tokenString)

        entity shouldNotBe null
        entity!!.token shouldEqualTo "fbLpw9Entnm9WWbTyrEhE7GywnTTLqBDI6MAF6JyzdoerAHQQ9BsuubUjGO77GMiW1WJ8jJxqXaA9fHbXAMnCA==.v=1.k=1.d=1579283492.t=a.l=.u=1c5af167-fd5d-4ee5-a11b-fe93b1882ade.c=6843294628315865806"
        entity.tokenType shouldEqualTo "Bearer"
        entity.expiresInMillis shouldEqualTo 1579283491844
    }

    @Test
    fun `given a string with invalid structure, when fromStringToAccessToken called, returns null AccessTokenEntity`() {
        val tokenString = """
            {
                "name": "unrelated"
            }
        """.trimIndent()

        val entity = accessTokenConverter.fromStringToAccessToken(tokenString)

        entity shouldBe null
    }

    @Test
    fun `given an AccessTokenEntity, when accessTokenToString called, maps it to correct json string`() {
        val entity = AccessTokenEntity("token", "type", 1000)

        val jsonString = accessTokenConverter.accessTokenToString(entity)

        jsonString shouldEqualTo """
            {
                "token": "token",
                "type": "type",
                "expires": 1000
            }
        """.trimIndent()
    }
}
