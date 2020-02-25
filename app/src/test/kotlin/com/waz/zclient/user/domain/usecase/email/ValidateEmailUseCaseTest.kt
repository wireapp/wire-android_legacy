package com.waz.zclient.user.domain.usecase.email

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class ValidateEmailUseCaseTest : UnitTest() {

    private var validationEmailUseCase = ValidateEmailUseCase()

    @Mock
    private lateinit var validateEmailParams: ValidateEmailParams

    @Test
    fun `Given run is executed, when email doesn't match regex, then return failure`() {
        val email = "test"

        runBlockingTest {
            `when`(validateEmailParams.email).thenReturn(email)

            validationEmailUseCase.run(validateEmailParams).isLeft shouldBe true
        }
    }

    @Test
    fun `Given run is executed, when email matches regex and length is 1, then return failure`() {
        val email = "t"

        runBlockingTest {

            `when`(validateEmailParams.email).thenReturn(email)

            validationEmailUseCase.run(validateEmailParams).isLeft shouldBe true
        }
    }

    @Test
    fun `Given run is executed, when email is empty then return failure`() {
        val email = String.empty()

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
            validationEmailUseCase.run(validateEmailParams).map {
                it shouldBe email
            }
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given run is executed when request is canceled, then return false`() {

        runBlockingTest {

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            validationEmailUseCase.run(validateEmailParams).isLeft shouldBe true
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "The request has been cancelled"
        private const val CANCELLATION_DELAY = 200L
    }
}
