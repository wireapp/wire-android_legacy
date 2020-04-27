package com.waz.zclient.shared.user.password

import com.waz.zclient.UnitTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class ValidatePasswordUseCaseTest : UnitTest() {

    private lateinit var validatePasswordUseCase: ValidatePasswordUseCase

    @Mock
    private lateinit var validatePasswordParams: ValidatePasswordParams

    @Before
    fun setup() {
        validatePasswordUseCase = ValidatePasswordUseCase()
        `when`(validatePasswordParams.minLength).thenReturn(TEST_MIN_LENGTH)
        `when`(validatePasswordParams.maxLength).thenReturn(TEST_MAX_LENGTH)
    }

    @Test
    fun `Given run is executed, when password length is smaller that minLength, then return failure`() {
        runBlockingTest {
            val password = "t"
            `when`(validatePasswordParams.password).thenReturn(password)
            val response = validatePasswordUseCase.run(validatePasswordParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe PasswordTooShort }) {}
        }
    }

    @Test
    fun `Given run is executed, when password length is greater that maxLength, then return failure`() {
        runBlockingTest {
            val password = "ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"
            `when`(validatePasswordParams.password).thenReturn(password)
            val response = validatePasswordUseCase.run(validatePasswordParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe PasswordTooLong }) {}
        }
    }

    @Test
    fun `Given run is executed, when password does not contains a lowercase, then return failure`() {
        runBlockingTest {
            val password = "TTTTTTTT"
            `when`(validatePasswordParams.password).thenReturn(password)
            val response = validatePasswordUseCase.run(validatePasswordParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe NoLowerCaseLetter }) {}
        }
    }

    @Test
    fun `Given run is executed, when password does not contains an uppercase, then return failure`() {
        runBlockingTest {
            val password = "tttttttt"
            `when`(validatePasswordParams.password).thenReturn(password)
            val response = validatePasswordUseCase.run(validatePasswordParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe NoUpperCaseLetter }) {}
        }
    }

    @Test
    fun `Given run is executed, when password does not contains a digit, then return failure`() {
        runBlockingTest {
            val password = "testPassword"
            `when`(validatePasswordParams.password).thenReturn(password)
            val response = validatePasswordUseCase.run(validatePasswordParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe NoDigit }) {}
        }
    }

    @Test
    fun `Given run is executed, when password does not contains a special character, then return failure`() {
        runBlockingTest {
            val password = "testPassword8"
            `when`(validatePasswordParams.password).thenReturn(password)
            val response = validatePasswordUseCase.run(validatePasswordParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe NoSpecialCharacter }) {}
        }
    }

    @Test
    fun `Given run is executed, when name fits requirements then return success`() {
        runBlockingTest {
            val password = "testPassword8@"
            `when`(validatePasswordParams.password).thenReturn(password)
            validatePasswordUseCase.run(validatePasswordParams).isRight shouldBe true
        }

    }

    companion object {
        private const val TEST_MIN_LENGTH = 8
        private const val TEST_MAX_LENGTH = 120
    }
}
