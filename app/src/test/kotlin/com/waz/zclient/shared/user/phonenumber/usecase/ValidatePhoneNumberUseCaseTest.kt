package com.waz.zclient.shared.user.phonenumber.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class ValidatePhoneNumberUseCaseTest : UnitTest() {

    private lateinit var validatePhoneNumberUseCase: ValidatePhoneNumberUseCase

    @Mock
    private lateinit var validatePhoneNumberParams: ValidatePhoneNumberParams

    @Before
    fun setup() {
        validatePhoneNumberUseCase = ValidatePhoneNumberUseCase()
    }

    @Test
    fun `Given run is executed, country code doesn't match regex, then return failure`() =
        runBlockingTest {
            val countryCode = "-332"

            `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)

            val response = validatePhoneNumberUseCase.run(validatePhoneNumberParams)
            assertTrue(response.isLeft)
            response.onFailure { it shouldBe CountryCodeInvalid }
        }

    @Test
    fun `Given run is executed, when country code is empty, then return failure`() =
        runBlockingTest {
            val countryCode = ""

            `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)

            val response = validatePhoneNumberUseCase.run(validatePhoneNumberParams)
            assertTrue(response.isLeft)
            response.onFailure { assertEquals(CountryCodeInvalid, it) }
        }

    @Test
    fun `Given run is executed, when country code is valid, phone number has chars, then return failure`() =
        runBlockingTest {
            val countryCode = "+49"
            val phoneNumber = "17688373822l"

            `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)
            `when`(validatePhoneNumberParams.phoneNumber).thenReturn(phoneNumber)

            val response = validatePhoneNumberUseCase.run(validatePhoneNumberParams)
            assertTrue(response.isLeft)
            response.onFailure { it shouldBe PhoneNumberInvalid }
        }

    @Test
    fun `Given run is executed, when full phone number does not match E164 format, then return failure`() =
        runBlockingTest {
            val countryCode = "+49"
            val phoneNumber = "176(883)73822"

            `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)
            `when`(validatePhoneNumberParams.phoneNumber).thenReturn(phoneNumber)

            val response = validatePhoneNumberUseCase.run(validatePhoneNumberParams)
            assertTrue(response.isLeft)
            response.onFailure { it shouldBe PhoneNumberInvalid }
        }

    @Test
    fun `Given run is executed, when full phone number is E164 format then return success`() =
        runBlockingTest {
            val countryCode = "+49"
            val phoneNumber = "1769999999"

            `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)
            `when`(validatePhoneNumberParams.phoneNumber).thenReturn(phoneNumber)

            val response = validatePhoneNumberUseCase.run(validatePhoneNumberParams)

            response.onSuccess { assertEquals("${countryCode}${phoneNumber}", it) }
        }
}
