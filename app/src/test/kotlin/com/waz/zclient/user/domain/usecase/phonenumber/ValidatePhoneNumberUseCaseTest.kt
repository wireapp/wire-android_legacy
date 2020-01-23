package com.waz.zclient.user.domain.usecase.phonenumber

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `Given run is executed, country code doesn't match regex, then return failure`() = runBlockingTest {
        val countryCode = "-332"

        `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)

        validatePhoneNumberUseCase.run(validatePhoneNumberParams).onFailure {
            it shouldBe CountryCodeInvalid
        }
    }

    @Test
    fun `Given run is executed, when country code is valid, phone number has chars, then return failure`() = runBlockingTest {
        val countryCode = "+49"
        val phoneNumber = "176 883 73822l"

        `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)
        `when`(validatePhoneNumberParams.phoneNumber).thenReturn(phoneNumber)

        validatePhoneNumberUseCase.run(validatePhoneNumberParams).onFailure {
            it shouldBe PhoneNumberInvalid
        }
    }

    @Test
    fun `Given run is executed, when full phone number does not match E164 format, then return failure`() = runBlockingTest {
        val countryCode = "+49"
        val phoneNumber = "176 (883) 73822"

        `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)
        `when`(validatePhoneNumberParams.phoneNumber).thenReturn(phoneNumber)

        validatePhoneNumberUseCase.run(validatePhoneNumberParams).onFailure {
            it shouldBe PhoneNumberInvalid
        }
    }

    @Test
    fun `Given run is executed, when full phone number is E164 format then return success`() = runBlockingTest {
        val countryCode = "+49"
        val phoneNumber = "176 883 73822l"

        `when`(validatePhoneNumberParams.countryCode).thenReturn(countryCode)
        `when`(validatePhoneNumberParams.phoneNumber).thenReturn(phoneNumber)

        validatePhoneNumberUseCase.run(validatePhoneNumberParams).onSuccess {
            it shouldBe "${countryCode}${phoneNumber}"
        }
    }
}
