package com.waz.zclient.shared.user.email

import com.waz.zclient.UnitTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class ValidateEmailUseCaseTest : UnitTest() {

    private lateinit var validationEmailUseCase: ValidateEmailUseCase

    @Mock
    private lateinit var validateEmailParams: ValidateEmailParams

    @Before
    fun setup() {
        validationEmailUseCase = ValidateEmailUseCase()
    }

    @Test
    fun `Given run is executed, when email doesn't match regex, then return failure`() {
        val email = "test"

        runBlockingTest {
            `when`(validateEmailParams.email).thenReturn(email)
            validationEmailUseCase.run(validateEmailParams).isLeft shouldBe true
        }
    }

    @Test
    fun `Given run is executed, when email length is smaller than 5, then return failure`() {
        val email = "t"

        runBlockingTest {
            `when`(validateEmailParams.email).thenReturn(email)
            validationEmailUseCase.run(validateEmailParams).isLeft shouldBe true
        }
    }

    @Test
    fun `Given run is executed, when email matches regex and email fits requirements then return success`() {
        val email = "test@wire.com"

        runBlockingTest {
            `when`(validateEmailParams.email).thenReturn(email)
            validationEmailUseCase.run(validateEmailParams).isRight shouldBe true
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given run is executed when request is canceled, then return false`() =

        runBlockingTest {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
            validationEmailUseCase.run(validateEmailParams).isLeft shouldBe true
        }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "The request has been cancelled"
        private const val CANCELLATION_DELAY = 200L
    }
}
