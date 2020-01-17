package com.waz.zclient.storage.db.accountdata

import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqualTo
import org.amshove.kluent.shouldNotBe
import org.junit.Before
import org.junit.Test

class SsoIdConverterTest {

    private lateinit var ssoIdConverter: SsoIdConverter

    @Before
    fun setUp() {
        ssoIdConverter = SsoIdConverter()
    }

    @Test
    fun `given a string with subject and tenant, when fromStringToSsoId called, maps it to SsoIdEntity`() {
        val ssoString = """
            {
                "subject": "testSubject",
                "tenant": "testTennant"
            }
        """.trimIndent()

        val entity = ssoIdConverter.fromStringToSsoId(ssoString)

        entity shouldNotBe null
        entity!!.subject shouldEqualTo "testSubject"
        entity.tenant shouldEqualTo "testTennant"
    }

    @Test
    fun `given a string with invalid structure, when fromStringToSsoId called, returns null SsoIdEntity`() {
        val ssoString = """
            {
                "name": "unrelated"
            }
        """.trimIndent()

        val entity = ssoIdConverter.fromStringToSsoId(ssoString)

        entity shouldBe null
    }

    @Test
    fun `given an SsoIdEntity, when ssoIdToString called, maps it to correct json string`() {
        val entity = SsoIdEntity("testSubject", "testTenant")

        val jsonString = ssoIdConverter.ssoIdToString(entity)

        jsonString shouldEqualTo """
            {
                "subject": "testSubject",
                "tenant": "testTenant"
            }
        """.trimIndent()
    }
}
